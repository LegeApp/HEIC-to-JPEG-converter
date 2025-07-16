#!/bin/bash

# Build Rust JNI library for Android
set -e

# --- Configuration and Argument Parsing ---
ANDROID_NDK="${ANDROID_NDK_ROOT:-$NDK_HOME}"
ANDROID_API="21"
RELEASE=false
HELP=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --ndk-path)
            ANDROID_NDK="$2"
            shift 2
            ;;
        --release)
            RELEASE=true
            shift
            ;;
        --help|-h)
            HELP=true
            shift
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

if [ "$HELP" = true ]; then
    echo "Usage: $0 [OPTIONS]"
    echo "Options:"
    echo "  --ndk-path PATH  Path to Android NDK (or set ANDROID_NDK_ROOT)"
    echo "  --release        Build in release mode (default: debug)"
    echo "  --help           Show this help message"
    exit 0
fi

# --- Setup and Validation ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting Rust JNI library build for Android...${NC}"

if [ -z "$ANDROID_NDK" ]; then
    echo -e "${RED}Error: Android NDK path not found.${NC}"
    echo "Please set the ANDROID_NDK_ROOT environment variable or use the --ndk-path option."
    exit 1
fi

if [ ! -d "$ANDROID_NDK" ]; then
    echo -e "${RED}Error: Android NDK path does not exist: $ANDROID_NDK${NC}"
    exit 1
fi

echo -e "${CYAN}Using Android NDK: $ANDROID_NDK${NC}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JNI_LIBS_DIR="$SCRIPT_DIR/src/main/jniLibs"

# --- Target Definitions (Modern ABIs Only) ---
# Only targeting modern architectures for better performance and smaller APK size
# - arm64-v8a: All modern Android devices (99%+ of active devices)
# - x86_64: Android emulators and some tablets
TARGETS=(
    "aarch64-linux-android"
    "x86_64-linux-android"
)

declare -A ABI_MAPPING
ABI_MAPPING["aarch64-linux-android"]="arm64-v8a"
ABI_MAPPING["x86_64-linux-android"]="x86_64"

BUILD_TYPE=$([ "$RELEASE" = true ] && echo "release" || echo "debug")
echo -e "${CYAN}Build type: $BUILD_TYPE${NC}"

# --- Install Rust Targets ---
echo -e "${YELLOW}Installing required Rust targets...${NC}"
for target in "${TARGETS[@]}"; do
    rustup target add "$target"
done

# --- Main Build Loop ---
for target in "${TARGETS[@]}"; do
    abi="${ABI_MAPPING[$target]}"
    echo -e "${YELLOW}=====================================================${NC}"
    echo -e "${YELLOW}Building for Target: $target (ABI: $abi)${NC}"
    echo -e "${YELLOW}=====================================================${NC}"

    # --- Configure Environment for Cross-Compilation ---
    # All modern targets use the standard naming pattern
    linker_name="${target}${ANDROID_API}-clang"
    export "CARGO_TARGET_$(echo "$target" | tr '[:lower:]' '[:upper:]' | tr '-' '_')_LINKER"="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/$linker_name"

    export PKG_CONFIG_ALLOW_CROSS=1
    export PKG_CONFIG_PATH="$SCRIPT_DIR/native-build/install-$abi/lib/pkgconfig"
    export CARGO_TARGET_$(echo "$target" | tr '[:lower:]' '[:upper:]' | tr '-' '_')_RUSTFLAGS="-L$JNI_LIBS_DIR/$abi"
    
    # Set sysroot for bindgen
    SYSROOT="$ANDROID_NDK/toolchains/llvm/prebuilt/linux-x86_64/sysroot"
    export BINDGEN_EXTRA_CLANG_ARGS="--sysroot=$SYSROOT"

    echo "  Linker: $(eval echo \$CARGO_TARGET_$(echo "$target" | tr '[:lower:]' '[:upper:]' | tr '-' '_')_LINKER)"
    echo "  pkg-config Path: $PKG_CONFIG_PATH"
    echo "  Rust Flags: $(eval echo \$CARGO_TARGET_$(echo "$target" | tr '[:lower:]' '[:upper:]' | tr '-' '_')_RUSTFLAGS)"
    echo "  Bindgen Sysroot: $SYSROOT"

    # --- Run Cargo Build ---
    # Set the PKG_CONFIG_PATH to the location of the .pc files for the native library
    # This allows the Rust build system (via pkg-config) to find the cross-compiled libheif.
    export PKG_CONFIG_PATH="$SCRIPT_DIR/native-build/install-$abi/lib/pkgconfig"

    cargo_args=("build" "--target" "$target" "--features" "android")
    if [ "$RELEASE" = true ]; then
        cargo_args+=("--release")
    fi

    if ! cargo "${cargo_args[@]}"; then
        echo -e "${RED}Error: Cargo build failed for target: $target${NC}"
        exit 1
    fi

    # --- Copy Final Library ---
    source_lib="$SCRIPT_DIR/target/$target/$BUILD_TYPE/libheic_to_jpeg_rust.so"
    dest_lib="$JNI_LIBS_DIR/$abi/libheic_to_jpeg_rust.so"
    cp "$source_lib" "$dest_lib"
    echo -e "${GREEN}Successfully copied library to: $dest_lib${NC}"

done

# --- Final Summary ---
echo -e "${GREEN}=====================================================${NC}"
echo -e "${GREEN}Build completed successfully for all targets!${NC}"
echo -e "${GREEN}=====================================================${NC}"

echo -e "\n${CYAN}Final JNI libraries are located in: $JNI_LIBS_DIR${NC}"
find "$JNI_LIBS_DIR" -name "*.so" -print -exec echo \;

echo -e "\n${CYAN}Next step:${NC}"
echo -e "  Open the project in Android Studio and run the app!"
