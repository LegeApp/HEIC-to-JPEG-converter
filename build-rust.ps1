# PowerShell script to build Rust JNI library for Android

# --- Configuration and Argument Parsing ---
$ANDROID_NDK = $env:ANDROID_NDK_ROOT
if (-not $ANDROID_NDK) { $ANDROID_NDK = $env:NDK_HOME }
$ANDROID_API = "21"
$RELEASE = $false
$HELP = $false

# Parse command-line arguments
$argsClone = $args.Clone()
$index = 0
while ($index -lt $argsClone.Length) {
    switch ($argsClone[$index]) {
        "--ndk-path" {
            $ANDROID_NDK = $argsClone[$index + 1]
            $index += 2
        }
        "--release" {
            $RELEASE = $true
            $index++
        }
        "--help" {
            $HELP = $true
            $index++
        }
        "-h" {
            $HELP = $true
            $index++
        }
        default {
            Write-Host "Unknown option: $($argsClone[$index])"
            exit 1
        }
    }
}

if ($HELP) {
    Write-Host "Usage: $PSCommandPath [OPTIONS]"
    Write-Host "Options:"
    Write-Host "  --ndk-path PATH  Path to Android NDK (or set ANDROID_NDK_ROOT)"
    Write-Host "  --release        Build in release mode (default: debug)"
    Write-Host "  --help, -h       Show this help message"
    exit 0
}

# --- Setup and Validation ---
$RED = "`e[31m"
$GREEN = "`e[32m"
$YELLOW = "`e[33m"
$CYAN = "`e[36m"
$NC = "`e[0m" # No Color

Write-Host "${GREEN}Starting Rust JNI library build for Android...${NC}"

if (-not $ANDROID_NDK) {
    Write-Host "${RED}Error: Android NDK path not found.${NC}"
    Write-Host "Please set the ANDROID_NDK_ROOT environment variable or use the --ndk-path option."
    exit 1
}

if (-not (Test-Path $ANDROID_NDK)) {
    Write-Host "${RED}Error: Android NDK path does not exist: $ANDROID_NDK${NC}"
    exit 1
}

Write-Host "${CYAN}Using Android NDK: $ANDROID_NDK${NC}"

$SCRIPT_DIR = Split-Path -Parent $PSCommandPath
$JNI_LIBS_DIR = Join-Path $SCRIPT_DIR "src/main/jniLibs"

# --- Target Definitions (Modern ABIs Only) ---
$TARGETS = @(
    "aarch64-linux-android",
    "x86_64-linux-android"
)

$ABI_MAPPING = @{
    "aarch64-linux-android" = "arm64-v8a";
    "x86_64-linux-android" = "x86_64"
}

$BUILD_TYPE = if ($RELEASE) { "release" } else { "debug" }
Write-Host "${CYAN}Build type: $BUILD_TYPE${NC}"

# --- Install Rust Targets ---
Write-Host "${YELLOW}Installing required Rust targets...${NC}"
foreach ($target in $TARGETS) {
    rustup target add $target
}

# --- Main Build Loop ---
foreach ($target in $TARGETS) {
    $abi = $ABI_MAPPING[$target]
    Write-Host "${YELLOW}=====================================================${NC}"
    Write-Host "${YELLOW}Building for Target: $target (ABI: $abi)${NC}"
    Write-Host "${YELLOW}=====================================================${NC}"

    # --- Configure Environment for Cross-Compilation ---
    $linker_name = "${target}${ANDROID_API}-clang"
    $linker_var = "CARGO_TARGET_$($target.ToUpper().Replace('-', '_'))_LINKER"
    $linker_path = Join-Path $ANDROID_NDK "toolchains/llvm/prebuilt/windows-x86_64/bin/$linker_name"
    Set-Item -Path "Env:$linker_var" -Value $linker_path

    $env:PKG_CONFIG_ALLOW_CROSS = "1"
    $env:PKG_CONFIG_PATH = Join-Path $SCRIPT_DIR "native-build/install-$abi/lib/pkgconfig"
    
    # Aggressively disable vcpkg for cross-compilation
    $env:VCPKG_ROOT = ""
    $env:VCPKGRS_DISABLE = "1"
    $env:VCPKGRS_DYNAMIC = "0"
    Remove-Item Env:VCPKG_DEFAULT_TRIPLET -ErrorAction SilentlyContinue
    Remove-Item Env:VCPKG_TRIPLET -ErrorAction SilentlyContinue
    
    # Set library paths for native dependencies
    $native_lib_path = Join-Path $SCRIPT_DIR "native-build/install-$abi/lib"
    $native_include_path = Join-Path $SCRIPT_DIR "native-build/install-$abi/include"
    $rustflags_var = "CARGO_TARGET_$($target.ToUpper().Replace('-', '_'))_RUSTFLAGS"
    $rustflags_value = "-L$native_lib_path -L$(Join-Path $JNI_LIBS_DIR $abi)"
    Set-Item -Path "Env:$rustflags_var" -Value $rustflags_value
    
    # Set sysroot for bindgen
    $SYSROOT = Join-Path $ANDROID_NDK "toolchains/llvm/prebuilt/windows-x86_64/sysroot"
    $env:BINDGEN_EXTRA_CLANG_ARGS = "--sysroot=$SYSROOT -I$native_include_path"
    
    # Set C/C++ include paths for build scripts
    $env:CFLAGS = "-I$native_include_path"
    $env:CXXFLAGS = "-I$native_include_path"
    $env:CPPFLAGS = "-I$native_include_path"

    Write-Host "  Linker: $(Get-Item -Path "Env:$linker_var" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Value)"
    Write-Host "  pkg-config Path: $env:PKG_CONFIG_PATH"
    Write-Host "  Rust Flags: $(Get-Item -Path "Env:$rustflags_var" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Value)"
    Write-Host "  Bindgen Sysroot: $SYSROOT"
    Write-Host "  VCPKG Disabled: $env:VCPKGRS_DISABLE"
    Write-Host "  Native Lib Path: $native_lib_path"

    # --- Run Cargo Build ---
    $env:PKG_CONFIG_PATH = Join-Path $SCRIPT_DIR "native-build/install-$abi/lib/pkgconfig"
    
    # Additional environment variables to force proper cross-compilation
    $env:TARGET = $target
    $env:HOST = "x86_64-pc-windows-msvc"
    $env:CARGO_CFG_TARGET_ARCH = $target.Split('-')[0]
    $env:CARGO_CFG_TARGET_OS = "android"
    
    # Force libheif-sys to use pkg-config instead of vcpkg
    $env:LIBHEIF_SYS_USE_PKG_CONFIG = "1"
    $env:LIBHEIF_NO_PKG_CONFIG = "0"
    $env:LIBHEIF_STATIC = "1"
    
    # Set C compiler environment variables
    $cc_var = "CC_$($target.Replace('-', '_'))"
    $cxx_var = "CXX_$($target.Replace('-', '_'))"
    $ar_var = "AR_$($target.Replace('-', '_'))"
    
    Set-Item -Path "Env:$cc_var" -Value (Join-Path $ANDROID_NDK "toolchains/llvm/prebuilt/windows-x86_64/bin/$linker_name")
    Set-Item -Path "Env:$cxx_var" -Value (Join-Path $ANDROID_NDK "toolchains/llvm/prebuilt/windows-x86_64/bin/${target}${ANDROID_API}-clang++")
    Set-Item -Path "Env:$ar_var" -Value (Join-Path $ANDROID_NDK "toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-ar")

    $cargo_args = @("build", "--target", $target, "--features", "android")
    if ($RELEASE) {
        $cargo_args += "--release"
    }

    $cargo_cmd = "cargo $cargo_args"
    $result = Invoke-Expression $cargo_cmd
    if ($LASTEXITCODE -ne 0) {
        Write-Host "${RED}Error: Cargo build failed for target: $target${NC}"
        exit 1
    }

    # --- Copy Final Library ---
    $source_lib = Join-Path $SCRIPT_DIR "target/$target/$BUILD_TYPE/libheic_to_jpeg_rust.dll"
    $dest_lib = Join-Path $JNI_LIBS_DIR "$abi/libheic_to_jpeg_rust.so"
    Copy-Item -Path $source_lib -Destination $dest_lib -Force
    Write-Host "${GREEN}Successfully copied library to: $dest_lib${NC}"
}

# --- Final Summary ---
Write-Host "${GREEN}=====================================================${NC}"
Write-Host "${GREEN}Build completed successfully for all targets!${NC}"
Write-Host "${GREEN}=====================================================${NC}"

Write-Host "`n${CYAN}Final JNI libraries are located in: $JNI_LIBS_DIR${NC}"
Get-ChildItem -Path $JNI_LIBS_DIR -Filter "*.so" -Recurse | ForEach-Object { Write-Host $_.FullName }

Write-Host "`n${CYAN}Next step:${NC}"
Write-Host "  Open the project in Android Studio and run the app!"