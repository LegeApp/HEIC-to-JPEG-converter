# HEIC to JPEG - Theming System

## 🎨 3-Color Palette System

This app uses a **3-color palette theming system** designed for easy customization and clear visual hierarchy:

### **Color Areas**

1. **🖼️ BACKGROUND AREA**
   - Main app background
   - Scroll view background
   - Overall canvas color

2. **📋 OPTION WINDOW AREA**  
   - Input fields and text boxes
   - Cards and containers
   - Interactive form elements
   - Radio buttons and checkboxes

3. **🔘 BUTTON AREA**
   - Primary action buttons
   - Load Image button
   - Start Conversion button
   - Interactive elements

---

## 🎯 Available Themes

### 1. **Default (Light Blue)**
- **Background:** Light gray-blue `#F5F7FA`
- **Option Windows:** Light blue `#E3F2FD`  
- **Buttons:** Material blue `#2196F3`
- **Use case:** Clean, professional interface

### 2. **Forest Green**
- **Background:** Light green `#F1F8E9`
- **Option Windows:** Soft green `#C8E6C9`
- **Buttons:** Forest green `#4CAF50`
- **Use case:** Nature-inspired, calming

### 3. **Purple Elegance**
- **Background:** Light purple `#F3E5F5`
- **Option Windows:** Soft purple `#E1BEE7`
- **Buttons:** Rich purple `#9C27B0`
- **Use case:** Elegant, premium feel

### 4. **Ocean Blue**
- **Background:** Light teal `#E0F2F1`
- **Option Windows:** Soft teal `#B2DFDB`
- **Buttons:** Ocean teal `#009688`
- **Use case:** Calming, professional

### 5. **Sunset Orange**
- **Background:** Light orange `#FFF3E0`
- **Option Windows:** Soft orange `#FFE0B2`
- **Buttons:** Warm orange `#FF9800`
- **Use case:** Energetic, warm feeling

### 6. **Dark Mode**
- **Background:** Dark gray `#121212`
- **Option Windows:** Medium gray `#2D2D2D`
- **Buttons:** Purple accent `#BB86FC`
- **Use case:** Low-light environments

---

## 🛠️ Implementation Details

### **Theme Attributes**
All themes use standardized attributes in `attrs.xml`:
```xml
<!-- BACKGROUND AREA -->
<attr name="theme_background_color" />
<attr name="theme_background_surface_color" />

<!-- OPTION WINDOW AREA -->
<attr name="theme_option_window_color" />
<attr name="theme_option_window_stroke_color" />
<attr name="theme_option_window_text_color" />

<!-- BUTTON AREA -->
<attr name="theme_button_primary_color" />
<attr name="theme_button_secondary_color" />
<attr name="theme_button_accent_color" />
<attr name="theme_button_text_color" />
```

### **Layout Usage**
Layouts reference theme attributes instead of hardcoded colors:
```xml
android:background="?attr/theme_background_color"
android:textColor="?attr/theme_option_window_text_color"
android:backgroundTint="?attr/theme_button_primary_color"
```

### **Theme Management**
The `ThemeManager` class handles theme switching:
```kotlin
// Apply theme to activity
ThemeManager.applyTheme(this)

// Change theme
ThemeManager.setCurrentTheme(context, AppTheme.FOREST)

// Get current theme
val currentTheme = ThemeManager.getCurrentTheme(context)
```

---

## 📁 File Structure

### **Color Definitions**
- `values/colors.xml` - All theme color definitions
- `values/attrs.xml` - Theme attributes  
- `values/themes.xml` - Theme style definitions

### **Theme Management**
- `gui/theme/ThemeManager.kt` - Main theme controller
- `gui/theme/ThemePreview.kt` - Theme preview utilities

### **Layout Integration**
- `layout/activity_main_gui.xml` - Uses theme attributes throughout

---

## 🎨 Adding New Themes

To add a new theme:

1. **Add colors to `colors.xml`:**
```xml
<!-- NEW THEME COLORS -->
<color name="newtheme_background">#YOUR_BG_COLOR</color>
<color name="newtheme_option_window">#YOUR_OPTION_COLOR</color>
<color name="newtheme_button_primary">#YOUR_BUTTON_COLOR</color>
<!-- ... (add all required colors) -->
```

2. **Add theme style to `themes.xml`:**
```xml
<style name="Theme.HEICToJPEG.NewTheme" parent="BaseAppTheme">
    <item name="theme_background_color">@color/newtheme_background</item>
    <!-- ... (map all attributes) -->
</style>
```

3. **Add to ThemeManager enum:**
```kotlin
enum class AppTheme(val themeName: String, val styleRes: Int) {
    // ... existing themes ...
    NEWTHEME("New Theme Name", R.style.Theme_HEICToJPEG_NewTheme)
}
```

4. **Add preview colors to ThemePreview:**
```kotlin
ThemeManager.AppTheme.NEWTHEME -> ThemeColors(
    backgroundArea = ContextCompat.getColor(context, R.color.newtheme_background),
    // ... etc
)
```

---

## 🚀 Usage

### **Current Implementation**
The theme system is fully integrated into the main GUI. Themes are applied automatically when the app starts based on user preference.

### **Theme Switching**
Currently themes are set programmatically. To add theme selection UI, create a settings screen that:
1. Shows theme previews using `ThemePreview`
2. Allows user selection 
3. Calls `ThemeManager.setCurrentTheme()`
4. Recreates the activity to apply new theme

### **Testing Themes**
To test different themes during development, change the default in `ThemeManager`:
```kotlin
// Temporarily change for testing
return AppTheme.FOREST // Instead of AppTheme.DEFAULT
```

---

## 📝 Notes

- All themes maintain **high contrast** for accessibility
- **Dark mode** uses Material Design 3 dark theme guidelines
- Colors are organized by **semantic meaning** not visual appearance
- Theme switching requires **activity recreation** to take effect
- All themes use the **same layout** - only colors change
