# PowerShell script to build ONLY the Rust JNI library for Android
# Assumes libheif native libraries are already built

param(
    [switch]$Release,
    [switch]$Help
)

if ($Help) {
    Write-Host "Usage: $PSCommandPath [OPTIONS]"
    Write-Host "Options:"
    Write-Host "  -Release         Build in release mode (default: debug)"
    Write-Host "  -Help            Show this help message"
    Write-Host ""
    Write-Host "This script only builds the Rust JNI library."
    Write-Host "It assumes libheif native libraries are already present."
    exit 0
}

# --- Setup and Validation ---
$RED = "`e[31m"
$GREEN = "`e[32m"
$YELLOW = "`e[33m"
$CYAN = "`e[36m"
$NC = "`e[0m" # No Color

Write-Host "${GREEN}Building Rust JNI library for Android (native libs assumed present)...${NC}"

$SCRIPT_DIR = Split-Path -Parent $PSCommandPath
$JNI_LIBS_DIR = Join-Path $SCRIPT_DIR "src/main/jniLibs"

# Check if native libraries exist
$NATIVE_LIBS_EXIST = (Test-Path (Join-Path $JNI_LIBS_DIR "arm64-v8a/libheif.so")) -and 
                     (Test-Path (Join-Path $JNI_LIBS_DIR "x86_64/libheif.so"))

if (-not $NATIVE_LIBS_EXIST) {
    Write-Host "${RED}Error: Native libheif libraries not found.${NC}"
    Write-Host "Expected to find libheif.so in:"
    Write-Host "  - $JNI_LIBS_DIR/arm64-v8a/"
    Write-Host "  - $JNI_LIBS_DIR/x86_64/"
    Write-Host ""
    Write-Host "${YELLOW}Please build the native libraries first using Linux/WSL or copy them from a working build.${NC}"
    exit 1
}

Write-Host "${GREEN}✓ Native libheif libraries found${NC}"

# --- Target Definitions (Modern ABIs Only) ---
$TARGETS = @(
    "aarch64-linux-android",
    "x86_64-linux-android"
)

$ABI_MAPPING = @{
    "aarch64-linux-android" = "arm64-v8a";
    "x86_64-linux-android" = "x86_64"
}

$BUILD_TYPE = if ($Release) { "release" } else { "debug" }
Write-Host "${CYAN}Build type: $BUILD_TYPE${NC}"

# --- Install Rust Targets ---
Write-Host "${YELLOW}Installing required Rust targets...${NC}"
foreach ($target in $TARGETS) {
    rustup target add $target
}

# --- Simple Build Loop (No complex environment setup) ---
foreach ($target in $TARGETS) {
    $abi = $ABI_MAPPING[$target]
    Write-Host "${YELLOW}=====================================================${NC}"
    Write-Host "${YELLOW}Building for Target: $target (ABI: $abi)${NC}"
    Write-Host "${YELLOW}=====================================================${NC}"

    # Simple cargo build without complex cross-compilation setup
    $cargo_args = @("build", "--target", $target)
    if ($Release) {
        $cargo_args += "--release"
    }

    Write-Host "${CYAN}Running: cargo $($cargo_args -join ' ')${NC}"
    & cargo @cargo_args
    if ($LASTEXITCODE -ne 0) {
        Write-Host "${RED}Error: Cargo build failed for target: $target${NC}"
        Write-Host "${YELLOW}This might be due to missing dependencies or environment setup.${NC}"
        Write-Host "${YELLOW}Consider using WSL or copying libraries from a working Linux build.${NC}"
        exit 1
    }

    # --- Copy Final Library ---
    $source_lib = Join-Path $SCRIPT_DIR "target/$target/$BUILD_TYPE/libheic_to_jpeg_rust.so"
    $dest_lib = Join-Path $JNI_LIBS_DIR "$abi/libheic_to_jpeg_rust.so"
    
    if (Test-Path $source_lib) {
        Copy-Item -Path $source_lib -Destination $dest_lib -Force
        Write-Host "${GREEN}Successfully copied library to: $dest_lib${NC}"
    } else {
        Write-Host "${RED}Error: Built library not found at: $source_lib${NC}"
        Write-Host "${YELLOW}Expected .so file but cargo might have produced .dll on Windows${NC}"
        
        # Try .dll instead
        $source_dll = Join-Path $SCRIPT_DIR "target/$target/$BUILD_TYPE/libheic_to_jpeg_rust.dll"
        if (Test-Path $source_dll) {
            Copy-Item -Path $source_dll -Destination $dest_lib -Force
            Write-Host "${GREEN}Successfully copied .dll as .so to: $dest_lib${NC}"
        } else {
            Write-Host "${RED}Neither .so nor .dll found${NC}"
            exit 1
        }
    }
}

# --- Final Summary ---
Write-Host "${GREEN}=====================================================${NC}"
Write-Host "${GREEN}Rust build completed successfully for all targets!${NC}"
Write-Host "${GREEN}=====================================================${NC}"

Write-Host "`n${CYAN}Final JNI libraries are located in: $JNI_LIBS_DIR${NC}"
Get-ChildItem -Path $JNI_LIBS_DIR -Filter "*.so" -Recurse | ForEach-Object { 
    $relativePath = $_.FullName.Replace($JNI_LIBS_DIR, "").TrimStart('\')
    Write-Host "  ${GREEN}✓${NC} $relativePath"
}

Write-Host "`n${CYAN}Next step:${NC}"
Write-Host "  Open the project in Android Studio and run the app!"
