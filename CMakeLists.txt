cmake_minimum_required(VERSION 3.18.1)

project("heictojpeg_native")

# Define the path to your prebuilt libraries
set(JNI_LIBS_DIR ${CMAKE_SOURCE_DIR}/src/main/jniLibs)

# Add the prebuilt Rust shared library
add_library(heic_to_jpeg_rust SHARED IMPORTED)
set_target_properties(heic_to_jpeg_rust PROPERTIES
    IMPORTED_LOCATION ${JNI_LIBS_DIR}/${ANDROID_ABI}/libheic_to_jpeg_rust.so)

# Add the prebuilt libheif shared library
add_library(heif SHARED IMPORTED)
set_target_properties(heif PROPERTIES
    IMPORTED_LOCATION ${JNI_LIBS_DIR}/${ANDROID_ABI}/libheif.so)

# Create a target that depends on all libraries
# This ensures they get included in the APK
add_library(native_deps SHARED dummy.cpp)
target_link_libraries(native_deps 
    heic_to_jpeg_rust 
    heif
    log
)
