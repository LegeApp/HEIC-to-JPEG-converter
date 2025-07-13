Integration Tips for Android App

    Cargo.toml: Ensure dependencies like libheif-rs, toojpeg, rayon, anyhow, jni are up-to-date. Build with cargo ndk for Android targets.
    Kotlin Side: Call asynchronously (e.g., via CoroutineScope) to prevent ANRs. Handle null jstring as errors.
    Testing: Use Android Studio's profiler for CPU/memory. Test with 10-bit HEIC from cameras.
    Future Enhancements: If resize is needed (e.g., for thumbnails), add optional params and use fast_image_resize. For batch, expose a JNI batch function.

This should make the app faster and more reliable while maintaining high quality. If you need further tweaks, provide benchmarks or specific device info!