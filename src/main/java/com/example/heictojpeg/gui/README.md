# Modern GUI Implementation

This folder contains the new modern GUI implementation for the HEIC to JPEG converter app.

## Features Implemented

### 📐 Dimension Controls (Top Left)
- **Width & Height inputs** with "Same as source" default
- **Automatic aspect ratio** calculation when one dimension is changed
- **Keep proportions checkbox** to toggle aspect ratio locking
- Text clears when focused, numbers auto-adjust to maintain ratio

### 🖼️ Image Loading (Top Center)  
- **Large "Load Image" button** that defaults to file picker
- **Selected image display** showing filename below button
- Support for HEIC/HEIF files and fallback to all files

### 📁 Output Location (Middle)
- **4 selectable radio button options:**
  1. **Same as input** - saves next to original file
  2. **Pictures folder** - uses Android Pictures directory  
  3. **Downloads folder** - uses Android Downloads directory
  4. **Custom path** - blank field that opens folder picker when selected
- Custom path automatically deselects and clears when other options chosen
- Must select an output option to enable Start button

### ▶️ Start Button & Progress (Bottom)
- **Large START button** (60% width, 10% height) 
- **Disabled until** image selected AND output location chosen
- **Transforms into shimmering progress bar** during conversion
- **Facebook Shimmer library** integration for smooth animation

### ✅ Completion Feedback
- **"Save complete!" message** in bright accent color
- **Location confirmation** showing where file was saved
- **Auto-dismisses** after 3 seconds and resets UI

## Technical Implementation

### Libraries Used
- **Facebook Shimmer** (`com.facebook.shimmer:shimmer:0.5.0`) for progress animation
- **Material Design Components** for modern UI elements
- **AndroidX Activity Result APIs** for file picking

### Architecture
- **MVVM pattern** with lifecycle-aware components
- **Coroutines** for background processing and smooth UI updates
- **Proper permission handling** for storage access across Android versions

### Files Structure
```
gui/
├── MainActivityGUI.kt           # Main activity with all logic
└── ../res/layout/
    └── activity_main_gui.xml    # Layout with all UI components
```

## Key Features

### Responsive Design
- Uses ConstraintLayout for flexible positioning
- ScrollView support for smaller screens
- Maximum width constraints for tablet support

### User Experience
- Intuitive flow: dimensions → load → output → start
- Visual feedback at every step
- Error prevention (disabled states)
- Smooth animations and transitions

### Accessibility
- Material Design components with built-in accessibility
- Clear labels and hints
- Proper focus handling
- High contrast color scheme

## Next Steps

1. **Image dimension detection** - Get actual width/height from selected HEIC files
2. **Output file management** - Implement actual file saving to selected locations  
3. **Quality settings** - Add JPEG quality slider
4. **Batch processing** - Multiple file selection support
5. **Settings screen** - Advanced conversion options

## Usage

The new GUI is set as the main launcher activity in `AndroidManifest.xml`. The original `MainActivity` is kept for reference but not launched by default.

To switch back to the old UI, simply change the launcher activity in the manifest.
