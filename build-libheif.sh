#!/bin/bash

# Build libheif and its dependencies for Android
set -e

# --- Configuration and Argument Parsing ---
ANDROID_NDK="${ANDROID_NDK_ROOT:-$NDK_HOME}"
ANDROID_API="21"
CLEAN_BUILD=false
HELP=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --ndk-path)
            ANDROID_NDK="$2"
            shift 2
            ;;
        --api-level)
            ANDROID_API="$2"
            shift 2
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
    echo "  --ndk-path PATH     Path to Android NDK (default: \$ANDROID_NDK_ROOT or \$NDK_HOME)"
    echo "  --api-level LEVEL   Android API level (default: 21)"
    echo "  --clean             Perform a clean build, removing previous artifacts"
    echo "  --help              Show this help message"
    exit 0
fi

# --- Setup and Validation ---
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${GREEN}Starting build of libheif for Android...${NC}"

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
echo -e "${CYAN}Targeting Android API: $ANDROID_API${NC}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/native-build"
LIBS_DIR="$SCRIPT_DIR/src/main/jniLibs"

if [ "$CLEAN_BUILD" = true ] && [ -d "$BUILD_DIR" ]; then
    echo -e "${YELLOW}Cleaning previous build directory...${NC}"
    rm -rf "$BUILD_DIR"
fi

mkdir -p "$BUILD_DIR"
mkdir -p "$LIBS_DIR"

# --- Clone Dependencies ---
clone_repo() {
    local repo_url=$1
    local repo_dir=$2
    if [ ! -d "$repo_dir" ]; then
        echo -e "${CYAN}Cloning $1...${NC}"
        git clone --depth 1 "$repo_url" "$repo_dir"
    else
        echo -e "${CYAN}Dependency $2 already exists. Skipping clone.${NC}"
    fi
}

clone_repo https://github.com/strukturag/libheif.git "$BUILD_DIR/libheif"
clone_repo https://github.com/strukturag/libde265.git "$BUILD_DIR/libde265"
clone_repo https://github.com/libjpeg-turbo/libjpeg-turbo.git "$BUILD_DIR/libjpeg-turbo"

# --- Build Function for Each Target ---
build_target() {
    local abi="$1"
    
    echo -e "${YELLOW}=====================================================${NC}"
    echo -e "${YELLOW}Building for ABI: $abi${NC}"
    echo -e "${YELLOW}=====================================================${NC}"
    
    local install_dir="$BUILD_DIR/install-$abi"
    local final_lib_dir="$LIBS_DIR/$abi"
    mkdir -p "$install_dir"
    mkdir -p "$final_lib_dir"

    local CMAKE_COMMON_ARGS=(
        "-DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake"
        "-DANDROID_ABI=$abi"
        "-DANDROID_PLATFORM=android-$ANDROID_API"
        "-DCMAKE_BUILD_TYPE=Release"
        "-DCMAKE_INSTALL_PREFIX=$install_dir"
    )

    # 1. Build libde265 (shared)
    echo -e "${CYAN}--- Building libde265 (shared) ---${NC}"
    local de265_build_dir="$BUILD_DIR/libde265/build-$abi"
    mkdir -p "$de265_build_dir"
    cd "$de265_build_dir"
    cmake "${CMAKE_COMMON_ARGS[@]}" -DBUILD_SHARED_LIBS=ON -DENABLE_DECODER=ON -DENABLE_ENCODER=OFF -DLOG_LEVEL=0 "$BUILD_DIR/libde265"
    cmake --build . --target install -j$(nproc)
    cd "$SCRIPT_DIR"

    # 2. Build libjpeg-turbo (static)
    echo -e "${CYAN}--- Building libjpeg-turbo (static) ---${NC}"
    local jpeg_build_dir="$BUILD_DIR/libjpeg-turbo/build-$abi"
    mkdir -p "$jpeg_build_dir"
    cd "$jpeg_build_dir"
    cmake "${CMAKE_COMMON_ARGS[@]}" -DENABLE_SHARED=OFF -DENABLE_STATIC=ON "$BUILD_DIR/libjpeg-turbo"
    cmake --build . --target install -j$(nproc)
    cd "$SCRIPT_DIR"

    # 3. Build libheif (shared), linking against static dependencies
    echo -e "${CYAN}--- Building libheif (shared) ---${NC}"
    local heif_build_dir="$BUILD_DIR/libheif/build-$abi"
    mkdir -p "$heif_build_dir"
    cd "$heif_build_dir"
    cmake "${CMAKE_COMMON_ARGS[@]}" \
        -DBUILD_SHARED_LIBS=ON \
        -DWITH_EXAMPLES=OFF \
        -DWITH_GDK_PIXBUF=OFF \
        -DBUILD_TESTING=OFF \
        -DWITH_LIBDE265=ON \
        -DWITH_JPEG_DECODER=ON \
        -DWITH_JPEG_ENCODER=ON \
        -DPLUGIN_LOADING=BUILTIN \
        -DENABLE_PLUGIN_LOADING=OFF \
        -DCMAKE_PREFIX_PATH="$install_dir" \
        -DLIBDE265_INCLUDE_DIR="$install_dir/include" \
        -DLIBDE265_LIBRARY="$install_dir/lib/libde265.so" \
        -DJPEG_INCLUDE_DIR="$install_dir/include" \
        -DJPEG_LIBRARY="$install_dir/lib/libjpeg.a" \
        "$BUILD_DIR/libheif"

    cmake --build . --target install -j$(nproc)

    # 4. Copy the final shared library to the jniLibs directory
    echo -e "${CYAN}--- Copying final library ---${NC}"
    cp "$install_dir/lib/"*.so "$final_lib_dir/"
    echo -e "${GREEN}Successfully copied libheif.so to $final_lib_dir${NC}"
    cd "$SCRIPT_DIR"
}

# --- Main Execution Loop ---
declare -a TARGET_ABIS=("arm64-v8a" "armeabi-v7a" "x86_64")

for abi in "${TARGET_ABIS[@]}"; do
    if ! build_target "$abi"; then
        echo -e "${RED}Error: Failed to build for ABI: $abi${NC}"
        exit 1
    fi
done

# --- Final Summary ---
echo -e "${GREEN}=====================================================${NC}"
echo -e "${GREEN}Build completed successfully for all targets!${NC}"
echo -e "${GREEN}=====================================================${NC}"

echo -e "\n${CYAN}Final libraries are located in: $LIBS_DIR${NC}"
find "$LIBS_DIR" -name "*.so" -print -exec echo \;

echo -e "\n${CYAN}Next steps:${NC}"
echo -e "  1. Run ${YELLOW}./build-rust.sh${NC} to compile the Rust JNI wrapper."
echo -e "  2. Open the project in Android Studio and build the full APK."

# Check for common dependencies
echo -e "\n${CYAN}Dependency check:${NC}"
if command -v cmake &> /dev/null; then
    echo -e "  ${GREEN}✓${NC} cmake found"
else
    echo -e "  ${RED}✗${NC} cmake not found (install with: sudo apt install cmake)"
fi

if command -v git &> /dev/null; then
    echo -e "  ${GREEN}✓${NC} git found"
else
    echo -e "  ${RED}✗${NC} git not found (install with: sudo apt install git)"
fi

if command -v make &> /dev/null; then
    echo -e "  ${GREEN}✓${NC} make found"
else
    echo -e "  ${RED}✗${NC} make not found (install with: sudo apt install build-essential)"
fi
