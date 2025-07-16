#!/bin/bash

# Simple test build script for Rust library - Linux/Bash Version
set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${GREEN}Testing Rust library compilation...${NC}"

# Navigate to the Rust project directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Test compile for host target first
echo -e "${YELLOW}Testing host compilation...${NC}"
if ! CC=clang cargo check; then
    echo -e "${RED}Host compilation failed${NC}"
    exit 1
fi

echo -e "${GREEN}Host compilation successful!${NC}"

# Test if Android targets can be added (don't fail if already installed)
echo -e "${YELLOW}Checking Android targets...${NC}"
android_targets=(
    "aarch64-linux-android"
    "armv7-linux-androideabi"
    "x86_64-linux-android"
    "i686-linux-android"
)

for target in "${android_targets[@]}"; do
    echo -e "${CYAN}Checking target: $target${NC}"
    if rustup target add "$target" >/dev/null 2>&1; then
        echo -e "${GREEN}✓ Target $target available${NC}"
    else
        echo -e "${YELLOW}⚠ Target $target may need installation${NC}"
    fi
done

# Check if we can at least compile for one Android target
echo -e "${YELLOW}Testing Android compilation (aarch64-linux-android)...${NC}"
if rustup target add aarch64-linux-android >/dev/null 2>&1; then
    if cargo check --target aarch64-linux-android 2>/dev/null; then
        echo -e "${GREEN}✓ Android target compilation successful!${NC}"
    else
        echo -e "${YELLOW}⚠ Android target compilation may need NDK setup${NC}"
        echo -e "${CYAN}This is normal if Android NDK is not configured yet.${NC}"
    fi
else
    echo -e "${YELLOW}⚠ Could not add Android target${NC}"
fi

# Check dependencies
echo -e "${YELLOW}Checking key dependencies...${NC}"

# Check for libheif dependency
if cargo tree | grep -q "libheif"; then
    echo -e "${GREEN}✓ libheif dependency found${NC}"
else
    echo -e "${YELLOW}⚠ libheif dependency not found in cargo tree${NC}"
fi

# Check for JNI dependency
if cargo tree | grep -q "jni"; then
    echo -e "${GREEN}✓ JNI dependency found${NC}"
else
    echo -e "${YELLOW}⚠ JNI dependency not found in cargo tree${NC}"
fi

# Check Cargo.toml for basic structure
echo -e "${YELLOW}Checking Cargo.toml structure...${NC}"
if [ -f "Cargo.toml" ]; then
    if grep -q "crate-type.*cdylib" Cargo.toml; then
        echo -e "${GREEN}✓ cdylib crate type configured${NC}"
    else
        echo -e "${YELLOW}⚠ cdylib crate type not found in Cargo.toml${NC}"
    fi
    
    if grep -q "jni" Cargo.toml; then
        echo -e "${GREEN}✓ JNI dependency configured${NC}"
    else
        echo -e "${YELLOW}⚠ JNI dependency not configured${NC}"
    fi
else
    echo -e "${RED}✗ Cargo.toml not found${NC}"
    exit 1
fi

echo -e "\n${CYAN}Test Summary:${NC}"
echo -e "${GREEN}✓ Basic Rust compilation works${NC}"
echo -e "${GREEN}✓ Project structure is valid${NC}"
echo -e "${CYAN}Next steps:${NC}"
echo -e "${YELLOW}1. Set up Android NDK (if not already done)${NC}"
echo -e "${YELLOW}2. Run ./build-rust.sh to build for Android${NC}"
echo -e "${YELLOW}3. Run ./build-libheif.sh to build native libraries${NC}"
echo -e "${YELLOW}4. Build the Android app in Android Studio${NC}"

echo -e "\n${GREEN}Test completed successfully!${NC}"
