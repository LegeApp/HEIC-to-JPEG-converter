#!/bin/bash

# Complete Android Build Script - Linux/Bash Version
# Builds libheif native libraries and Rust JNI library for Android

set -e

# Parse command line arguments
BUILD_LIBHEIF=false
BUILD_RUST=false
RELEASE=false
CLEAN_BUILD=false
HELP=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --build-libheif)
            BUILD_LIBHEIF=true
            shift
            ;;
        --build-rust)
            BUILD_RUST=true
            shift
            ;;
        --release)
            RELEASE=true
            shift
            ;;
        --clean)
            CLEAN_BUILD=true
            shift
            ;;
        --help|-h)
            HELP=true
            shift
            ;;
        *)
            echo "Unknown option $1"
            exit 1
            ;;
    esac
done

if [ "$HELP" = true ]; then
    echo "Usage: $0 [OPTIONS]"
    echo "Options:"
    echo "  --build-libheif    Build libheif native libraries"
    echo "  --build-rust       Build Rust JNI library"
    echo "  --release          Build in release mode"
    echo "  --clean            Clean previous builds"
    echo "  --help             Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0 --build-libheif --build-rust    # Build everything"
    echo "  $0 --build-rust --release          # Build only Rust library in release mode"
    exit 0
fi

# Default to building Rust if nothing specified
if [ "$BUILD_LIBHEIF" = false ] && [ "$BUILD_RUST" = false ]; then
    BUILD_RUST=true
fi

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}Android HEIC to JPEG Converter - Complete Build Script${NC}"
echo -e "${CYAN}======================================================${NC}"

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Build libheif first if requested
if [ "$BUILD_LIBHEIF" = true ]; then
    echo -e "${CYAN}Step 1: Building libheif native libraries...${NC}"
    
    libheif_args=()
    if [ "$CLEAN_BUILD" = true ]; then
        libheif_args+=("--clean")
    fi
    
    if ! "$SCRIPT_DIR/build-libheif.sh" "${libheif_args[@]}"; then
        echo -e "${RED}libheif build failed${NC}"
        exit 1
    fi
    echo -e "${GREEN}libheif build completed!${NC}"
else
    echo -e "${YELLOW}Skipping libheif build (use --build-libheif to build native libraries)${NC}"
fi

# Build Rust library
if [ "$BUILD_RUST" = true ]; then
    echo -e "${CYAN}Step 2: Building Rust JNI library...${NC}"
    
    # Check if Android targets are installed (for all required ABIs)
    ANDROID_TARGETS=(
        "aarch64-linux-android"   # arm64-v8a
        "armv7-linux-androideabi" # armeabi-v7a
        "i686-linux-android"      # x86
    )

    for target in "${ANDROID_TARGETS[@]}"; do
        echo -e "${CYAN}Checking target: $target${NC}"
        if ! rustup target list --installed | grep -q "$target"; then
            echo -e "${CYAN}Installing target: $target${NC}"
            rustup target add "$target"
        fi
    done

    # Build configuration
    if [ "$RELEASE" = true ]; then
        build_type="release"
    else
        build_type="debug"
    fi
    echo -e "${CYAN}Build type: $build_type${NC}"

    # Create jniLibs directory structure
    jni_libs_dir="$SCRIPT_DIR/src/main/jniLibs"
    mkdir -p "$jni_libs_dir"

    # ABI mapping
    declare -A ABI_MAPPING
    ABI_MAPPING["aarch64-linux-android"]="arm64-v8a"
    ABI_MAPPING["armv7-linux-androideabi"]="armeabi-v7a"
    ABI_MAPPING["i686-linux-android"]="x86"

    # Build for each target
    for target in "${ANDROID_TARGETS[@]}"; do
        abi="${ABI_MAPPING[$target]}"
        echo -e "${YELLOW}Building Rust library for target: $target (ABI: $abi)${NC}"

        # Create ABI directory
        abi_dir="$jni_libs_dir/$abi"
        mkdir -p "$abi_dir"

        # Build the Rust library
        cargo_args=("build" "--target" "$target")
        if [ "$RELEASE" = true ]; then
            cargo_args+=("--release")
        fi

        if ! cargo "${cargo_args[@]}"; then
            echo -e "${RED}Failed to build Rust library for target: $target${NC}"
            exit 1
        fi

        # Copy the built library to jniLibs
        if [ "$target" = "armv7-linux-androideabi" ]; then
            source_lib="$SCRIPT_DIR/target/$target/$build_type/libheic_to_jpeg_rust.so"
        else
            source_lib="$SCRIPT_DIR/target/$target/$build_type/libheic_to_jpeg_rust.so"
        fi
        dest_lib="$abi_dir/libheic_to_jpeg_rust.so"

        if [ -f "$source_lib" ]; then
            cp "$source_lib" "$dest_lib"
            echo -e "${GREEN}Copied Rust library to: $dest_lib${NC}"
        else
            echo -e "${RED}Built Rust library not found at: $source_lib${NC}"
            exit 1
        fi
    done

    echo -e "${GREEN}Rust library build completed!${NC}"
else
    echo -e "${YELLOW}Skipping Rust build (use --build-rust to build Rust library)${NC}"
fi

# Verify all libraries are present
echo -e "${CYAN}Verifying build results...${NC}"
jni_libs_dir="$SCRIPT_DIR/src/main/jniLibs"

if [ -d "$jni_libs_dir" ]; then
    echo -e "\n${CYAN}Built libraries:${NC}"
    find "$jni_libs_dir" -name "*.so" -type f | while read -r lib; do
        relative_path="${lib#$jni_libs_dir/}"
        echo -e "  ${GREEN}✓${NC} $relative_path"
    done
    
    # Check for required libraries per ABI
    required_libs=("libheic_to_jpeg_rust.so")
    if [ "$BUILD_LIBHEIF" = true ]; then
        required_libs+=("libheif.so" "libde265.so")
    fi
    
    abis=("arm64-v8a" "armeabi-v7a" "x86")
    all_present=true
    
    for abi in "${abis[@]}"; do
        abi_dir="$jni_libs_dir/$abi"
        if [ -d "$abi_dir" ]; then
            for required_lib in "${required_libs[@]}"; do
                lib_path="$abi_dir/$required_lib"
                if [ ! -f "$lib_path" ]; then
                    echo -e "${YELLOW}Missing: $abi/$required_lib${NC}"
                    all_present=false
                fi
            done
        else
            echo -e "${YELLOW}Missing ABI directory: $abi${NC}"
            all_present=false
        fi
    done
    
    if [ "$all_present" = true ]; then
        echo -e "\n${GREEN}🎉 All libraries built successfully!${NC}"
    else
        echo -e "\n${YELLOW}⚠️  Some libraries are missing. You may need to build libheif first.${NC}"
    fi
else
    echo -e "${RED}No libraries found in jniLibs directory${NC}"
fi

echo -e "\n${CYAN}Next steps:${NC}"
echo -e "${YELLOW}1. Open Android Studio${NC}"
echo -e "${YELLOW}2. Build and run the Android app${NC}"
echo -e "${YELLOW}3. Test the HEIC conversion functionality${NC}"

if [ "$BUILD_LIBHEIF" = false ]; then
    echo -e "\n${CYAN}To build complete native support:${NC}"
    echo -e "${YELLOW}$0 --build-libheif --build-rust${NC}"
fi

# Check system dependencies
echo -e "\n${CYAN}System dependency check:${NC}"
deps=("rustc" "cargo" "rustup" "cmake" "git" "make")
for dep in "${deps[@]}"; do
    if command -v "$dep" &> /dev/null; then
        echo -e "  ${GREEN}✓${NC} $dep found"
    else
        echo -e "  ${RED}✗${NC} $dep not found"
        case $dep in
            rustc|cargo|rustup)
                echo -e "    ${CYAN}Install Rust: curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh${NC}"
                ;;
            cmake)
                echo -e "    ${CYAN}Install cmake: sudo apt install cmake${NC}"
                ;;
            git)
                echo -e "    ${CYAN}Install git: sudo apt install git${NC}"
                ;;
            make)
                echo -e "    ${CYAN}Install build tools: sudo apt install build-essential${NC}"
                ;;
        esac
    fi
done
