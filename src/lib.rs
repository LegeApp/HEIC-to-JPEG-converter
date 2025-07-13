#[macro_use]
extern crate lazy_static;

use std::{fs, time::{Instant, Duration}};
use std::cmp::{max, min};
use libheif_rs::{ColorSpace, HeifContext, LibHeif, RgbChroma};
use fast_image_resize as fr;
use rayon::prelude::*;
use fr::{PixelType, FilterType, ResizeAlg, Resizer};
use fr::images::Image;
use fr::ResizeOptions;
use anyhow::{anyhow, Result};
use toojpeg::{EncodeOptions, ImageFormat, encode_jpeg};

#[cfg(feature = "android")]
use jni::JNIEnv;
#[cfg(feature = "android")]
use jni::objects::{JClass, JObjectArray, JString};
#[cfg(feature = "android")]
use jni::sys::{jint, jstring};

#[cfg(feature = "android")]
fn jni_resize_filter_from_int(filter_int: jint) -> &'static str {
    match filter_int {
        0 => "lanczos", // Default or Lanczos3
        1 => "bilinear",
        _ => "lanczos", // Fallback to default
    }
}

#[cfg(feature = "android")]
fn create_java_string(env: &mut JNIEnv, rust_string: &str) -> jstring {
    env.new_string(rust_string).expect("Couldn't create java string!").into_raw()
}

#[cfg(feature = "android")]
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_com_example_heictojpeg_NativeLib_convertHeicToJpeg(
    mut env: JNIEnv,
    _class: JClass,
    input_path: JString,
    output_path: JString,
) -> jstring {
    let input: String = env.get_string(&input_path).expect("Couldn't get java string!").into();
    let output: String = env.get_string(&output_path).expect("Couldn't get java string!").into();
    // For single conversion, we don't apply resizing. Pass 0 for width and height.
    let result = match convert_heic_to_jpeg(&input, &output, 0, 0, "lanczos") {
        Ok(timing) => format!("Successfully converted in {:.2?}s", timing.total.as_secs_f32()),
        Err(e) => format!("Error: {}", e),
    };

    create_java_string(&mut env, &result)
}

/// Convert HEIC to JPEG with optional resizing
///
/// # Arguments
/// * `input_file` - Path to input HEIC file
/// * `output_file` - Path where to save the output JPEG
/// * `target_width` - Target width (0 to keep original)
/// * `target_height` - Target height (0 to keep original)
/// * `resize_filter` - Resize filter to use ("lanczos" or "bilinear")
/// Convert HEIC to JPEG with optional resizing and chroma subsampling
/// 
/// # Arguments
/// * `input_file` - Path to input HEIC file
pub fn convert_heic_to_jpeg(
    heic_path: &str,
    jpeg_path: &str,
    width: u32,
    height: u32,
    resize_filter: &str,
) -> Result<ConversionTiming, Box<dyn std::error::Error>> {
    let mut timing = ConversionTiming::default();
    convert_heic_to_jpeg_internal(heic_path, jpeg_path, width, height, resize_filter, &mut timing)?;
    timing.total = timing.decode + timing.linear + timing.resize + timing.encode;
    Ok(timing)
}

#[derive(Debug, Default)]
pub struct ConversionTiming {
    pub total: Duration,
    pub decode: Duration,
    pub linear: Duration,
    pub resize: Duration,
    pub encode: Duration,
}

fn convert_heic_to_jpeg_internal(
    input_file: &str,
    output_file: &str,
    target_width: u32,
    target_height: u32,
    resize_filter: &str,
    timing: &mut ConversionTiming,
) -> Result<(), Box<dyn std::error::Error>> {


    if !input_file.to_lowercase().ends_with(".heic") {
        return Err(anyhow!("Input is not a HEIC file: {}", input_file).into());
    }

    let lib_heif = LibHeif::new();
    let context = HeifContext::read_from_file(input_file)?;
    let image_handle = context.primary_image_handle()?;

    let mut width = image_handle.width();
    let mut height = image_handle.height();

    let resize_options = if target_width == 0 || target_height == 0 {
        None
    } else {
        Some((target_width, target_height))
    };

    let mut output_buffer = Vec::new();

    if let Some((new_width, new_height)) = resize_options {
        // Resize Path: Decode to RGB, linearize, resize, convert back to sRGB, then encode.
        let decode_start = Instant::now();
        let image_data = lib_heif.decode(&image_handle, ColorSpace::Rgb(RgbChroma::Rgb), None)?;
        timing.decode = decode_start.elapsed();

        let rgb_bytes = image_data.planes().interleaved.unwrap().data;
        
        // Create source and destination images for resizing
        let src_image = Image::from_vec_u8(
            width,
            height,
            rgb_bytes.to_vec(),
            PixelType::U8x3,
        )?;
        
        let mut dst_image = Image::new(
            new_width,
            new_height,
            src_image.pixel_type(),
        );
        
        // Configure resizing
        let filter_type = match resize_filter {
            "bilinear" => FilterType::Bilinear,
            "catmull_rom" => FilterType::CatmullRom,
            "mitchell" => FilterType::Mitchell,
            // Only Lanczos3 is available in the current version
            _ => FilterType::Lanczos3, // Default to Lanczos3
        };
        
        let mut resizer = Resizer::new();
        
        // CPU extensions not needed for our optimized implementation

        // Create resize options with the desired filter
        let resize_options = ResizeOptions::new()
            .resize_alg(ResizeAlg::Convolution(FilterType::Lanczos3));
        
        // Perform the resize
        let resize_start = Instant::now();
        resizer.resize(&src_image, &mut dst_image, &resize_options)?;
        timing.resize = resize_start.elapsed();
        
        // Get the resized RGB data
        let resized_rgb = dst_image.buffer();
        
        // Convert to linear RGB for color space conversion
        let mut linear_rgb = vec![0.0f32; resized_rgb.len()];
        let linear_start = Instant::now();
        srgb_to_linear_wide(resized_rgb, &mut linear_rgb);
        timing.linear = linear_start.elapsed();
        
        // Convert back to sRGB
        let mut rgb_out = vec![0u8; resized_rgb.len()];
        let linear_to_srgb_start = Instant::now();
        linear_to_srgb_wide(&linear_rgb, &mut rgb_out);
        timing.linear = timing.linear + linear_to_srgb_start.elapsed();
        
        width = new_width;
        height = new_height;

        // Encode the resized RGB buffer to JPEG
        let encode_start = Instant::now();
        let options = EncodeOptions {
            width,
            height,
            format: ImageFormat::RGB,
            quality: 90,
            baseline: true,
            optimized: true,
            subsample: true, // Always use 4:2:0 chroma subsampling for better performance, 4:4:4 if false
        };
        
        encode_jpeg(&rgb_out, options, &mut output_buffer)
            .map_err(|e| anyhow::anyhow!(e))?;
            
        timing.encode = encode_start.elapsed();
    } else {
        // No-Resize Path: Decode to sRGB, and encode.
        // Decode the image
        let decode_start = Instant::now();
        let image_data = lib_heif.decode(&image_handle, ColorSpace::Rgb(RgbChroma::Rgb), None)?;
        timing.decode = decode_start.elapsed();

        // Extract the interleaved RGB data.
        let rgb_bytes = image_data.planes().interleaved.unwrap().data;

        // Convert to linear RGB
        let linear_start = Instant::now();
        let mut linear_rgb = vec![0.0f32; rgb_bytes.len()];
        srgb_to_linear_wide(rgb_bytes, &mut linear_rgb);
        timing.linear = linear_start.elapsed();
        
        // Convert back to sRGB for JPEG encoding
        let mut srgb_out = vec![0u8; rgb_bytes.len()];
        linear_to_srgb_wide(&linear_rgb, &mut srgb_out);
        
        // Encode the RGB data to JPEG. TooJpeg will handle the RGB to YCbCr conversion.
        let encode_start = Instant::now();
        let options = EncodeOptions {
            width,
            height,
            format: ImageFormat::RGB,
            quality: 95,
            baseline: true,
            optimized: true,
            subsample: true, // Always use 4:2:0 chroma subsampling for better performance, 4:4:4 if false
        };
        
        encode_jpeg(&srgb_out, options, &mut output_buffer)
            .map_err(|e| anyhow::anyhow!(e))?;
        timing.encode = encode_start.elapsed();

        println!("Encode: {:.2?} ({:.1}%)", 
            timing.encode, 
            timing.encode.as_secs_f64() / timing.total.as_secs_f64() * 100.0
        );
    }

    fs::write(output_file, output_buffer)?;

    // Timing is updated in-place through the mutable reference
    Ok(())
}

#[cfg(feature = "android")]
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_com_example_heictojpeg_NativeLib_convertHeicBatchToJpeg(
    mut env: JNIEnv,
    _class: JClass,
    input_paths: JObjectArray,
    output_dir: JString,
) -> jstring {
    let output_dir: String = env.get_string(&output_dir).expect("Couldn't get java string!").into();
    let num_files = env.get_array_length(&input_paths).unwrap_or(0);
    let mut successful_conversions = 0;
    let mut failed_conversions = 0;

    for i in 0..num_files {
        if let Ok(input_path_jstring) = env.get_object_array_element(&input_paths, i) {
            if let Ok(input_path) = env.get_string(&JString::from(input_path_jstring)) {
                let input_path: String = input_path.into();
                let file_name = std::path::Path::new(&input_path)
                    .file_stem()
                    .and_then(|s| s.to_str())
                    .unwrap_or("converted");

                let output_path = format!("{}/{}.jpg", &output_dir, file_name);

                match convert_heic_to_jpeg(&input_path, &output_path, 0, 0, "lanczos") {
                    Ok(_) => successful_conversions += 1,
                    Err(_) => failed_conversions += 1,
                }
            }
        }
    }

    let result_message = format!(
        "Batch conversion complete. Success: {}, Failed: {}",
        successful_conversions,
        failed_conversions
    );

    create_java_string(&mut env, &result_message)
}

// Parallel sRGB to linear RGB conversion
fn srgb_to_linear_wide(srgb: &[u8], linear: &mut [f32]) {
    // Process in parallel using Rayon's parallel iterators
    linear.par_chunks_mut(3).enumerate().for_each(|(i, linear_chunk)| {
        let src_idx = i * 3;
        if src_idx + 2 < srgb.len() {
            linear_chunk[0] = srgb_to_linear(srgb[src_idx]);
            linear_chunk[1] = srgb_to_linear(srgb[src_idx + 1]);
            linear_chunk[2] = srgb_to_linear(srgb[src_idx + 2]);
        }
    });
}

// Parallel linear RGB to sRGB conversion
fn linear_to_srgb_wide(linear: &[f32], srgb: &mut [u8]) {
    // Process in parallel using Rayon's parallel iterators
    srgb.par_chunks_mut(3).enumerate().for_each(|(i, srgb_chunk)| {
        let src_idx = i * 3;
        if src_idx + 2 < linear.len() {
            srgb_chunk[0] = linear_to_srgb(linear[src_idx]);
            srgb_chunk[1] = linear_to_srgb(linear[src_idx + 1]);
            srgb_chunk[2] = linear_to_srgb(linear[src_idx + 2]);
        }
    });
}

// Use lazy_static to initialize the LUT at runtime
lazy_static::lazy_static! {
    static ref SRGB_TO_LINEAR: [f32; 256] = {
        let mut table = [0f32; 256];
        for (i, val) in table.iter_mut().enumerate() {
            let x = i as f32 / 255.0;
            *val = if x <= 0.04045 {
                x / 12.92
            } else {
                ((x + 0.055) / 1.055).powf(2.4)
            };
        }
        table
    };
}

// Convert sRGB value to linear RGB (LUT version)
#[inline(always)]
fn srgb_to_linear(c: u8) -> f32 {
    SRGB_TO_LINEAR[c as usize]
}

// Convert linear RGB value to sRGB (scalar version, with safety clamp)
#[inline(always)]
fn linear_to_srgb(c: f32) -> u8 {
    let x = if c <= 0.0 {
        0.0
    } else if c >= 1.0 {
        1.0
    } else {
        c
    };
    
    let y = if x <= 0.0031308 {
        x * 12.92
    } else {
        1.055 * x.powf(1.0 / 2.4) - 0.055
    };
    
    min(max((y * 255.0).round() as i32, 0), 255) as u8
}

#[cfg(feature = "android")]
#[no_mangle]
#[allow(non_snake_case)]
pub extern "system" fn Java_com_example_heictojpeg_NativeLib_testConnection(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let lib_heif = LibHeif::new();
    
    // Check available decoder plugins
    let result = format!("LibHeif initialized successfully. Version info and decoder check would go here.");
    
    create_java_string(&mut env, &result)
}