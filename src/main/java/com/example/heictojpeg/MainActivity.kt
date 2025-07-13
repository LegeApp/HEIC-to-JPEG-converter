package com.example.heictojpeg

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path as GraphicsPath
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.documentfile.provider.DocumentFile
import com.example.heictojpeg.ui.theme.HEICtoJPEGTheme
import com.example.heictojpeg.ui.theme.ThemeOption
import com.example.heictojpeg.ui.theme.getColorScheme
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// --- Custom File Picker Contract ---
val customOpenMultipleDocuments = object : ActivityResultContracts.OpenMultipleDocuments() {
    override fun createIntent(context: Context, input: Array<String>): Intent {
        return super.createIntent(context, input).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, input)
        }
    }
}

// --- Data Classes ---
data class FileDetails(val name: String, val size: Long, val lastModified: Long)

// --- Helper Functions ---
fun getFileDetails(context: Context, uri: Uri): FileDetails? {
    return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val lastModifiedIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            val name = if (nameIndex != -1) cursor.getString(nameIndex) else "Unknown"
            val size = if (sizeIndex != -1) cursor.getLong(sizeIndex) else 0L
            val lastModified = if (lastModifiedIndex != -1) cursor.getLong(lastModifiedIndex) else 0L

            FileDetails(name, size, lastModified)
        } else {
            null
        }
    }
}

fun getRelativePath(uri: Uri): String? {
    val docId = DocumentsContract.getDocumentId(uri)
    val split = docId.split(":", limit = 2)
    if (split.size < 2) return null
    val path = split[1]
    return path.substringBeforeLast("/")
}

fun getImageDimensions(context: Context, uri: Uri): Triple<Int?, Int?, Float?> {
    return try {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)
            val width = options.outWidth
            val height = options.outHeight
            val ratio = if (width > 0 && height > 0) width.toFloat() / height.toFloat() else null
            Triple(width, height, ratio)
        } ?: Triple(null, null, null)
    } catch (e: Exception) {
        Triple(null, null, null)
    }
}

@Composable
fun SwipeHint(modifier: Modifier = Modifier, isBold: Boolean = false, mirrored: Boolean = false) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val strokeWidth = if (isBold) 8f else 4f

    Canvas(modifier = modifier.size(20.dp, 40.dp)) {
        val path = GraphicsPath().apply {
            if (mirrored) {
                moveTo(size.width * 0.8f, 0f)
                cubicTo(
                    size.width * 0.2f, size.height * 0.25f,
                    size.width * 0.2f, size.height * 0.75f,
                    size.width * 0.8f, size.height
                )
            } else {
                moveTo(size.width * 0.2f, 0f)
                cubicTo(
                    size.width * 0.8f, size.height * 0.25f,
                    size.width * 0.8f, size.height * 0.75f,
                    size.width * 0.2f, size.height
                )
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppScreen()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppScreen() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    // Billing manager
    val billingManager = remember { BillingManager(context.applicationContext) }
    val hasDonated by billingManager.hasDonated.collectAsState()
    
    DisposableEffect(Unit) {
        onDispose {
            billingManager.endConnection()
        }
    }

    val selectedFiles = remember { mutableStateListOf<Pair<Uri, FileDetails>>() }
    val conversionResults = remember { mutableStateMapOf<Uri, String>() }
    var tickerMessage by remember { mutableStateOf<String?>(null) }
    var isConverting by remember { mutableStateOf(false) }
    val showConversionTime = true // Always enabled, no user setting
    var selectedTheme by rememberSaveable { mutableStateOf<ThemeOption>(ThemeOption.Light) }
    
    // Aspect ratio control states  
    var widthText by rememberSaveable { mutableStateOf("") }
    var heightText by rememberSaveable { mutableStateOf("") }
    var keepProportions by rememberSaveable { mutableStateOf(true) }
    
    // State to track original image properties
    var sourceAspectRatio by remember { mutableStateOf<Float?>(null) }
    var sourceWidth by remember { mutableStateOf<Int?>(null) }
    var sourceHeight by remember { mutableStateOf<Int?>(null) }
    
    // State to track focus and user interaction
    var isWidthFocused by remember { mutableStateOf(false) }
    var isHeightFocused by remember { mutableStateOf(false) }
    var lastEditedField by rememberSaveable { mutableStateOf<String?>(null) }
    var hasUserEditedFields by rememberSaveable { mutableStateOf(false) }
    
    var oddNumberMessage by remember { mutableStateOf<String?>(null) }

    // Apply theme based on selected theme option
    HEICtoJPEGTheme(themeOption = selectedTheme) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {

    // --- Helper functions for aspect ratio calculations ---
    fun calculateProportionalDimension(baseValue: Int, isWidthToHeight: Boolean): Int {
        val ratio = sourceAspectRatio ?: return baseValue
        return if (isWidthToHeight) {
            (baseValue / ratio).toInt()
        } else {
            (baseValue * ratio).toInt()
        }
    }
    
    fun correctOddNumbers(): Boolean {
        var correctionMade = false
        val currentWidth = widthText.toIntOrNull()
        val currentHeight = heightText.toIntOrNull()
        
        if (currentWidth != null && currentWidth % 2 != 0) {
            widthText = (currentWidth + 1).toString()
            correctionMade = true
        }
        if (currentHeight != null && currentHeight % 2 != 0) {
            heightText = (currentHeight + 1).toString()
            correctionMade = true
        }
        
        if (correctionMade) {
            keepProportions = false
            oddNumberMessage = "Odd dimensions unsupported, rounded up to nearest even number."
        }
        
        return correctionMade
    }

    // --- LaunchedEffect for aspect ratio calculations ---
    LaunchedEffect(isWidthFocused, isHeightFocused) {
        // Only trigger calculations when both fields lose focus
        if (!isWidthFocused && !isHeightFocused && lastEditedField != null && hasUserEditedFields) {
            Log.d("AspectRatio", "Both fields unfocused, lastEditedField: $lastEditedField, keepProportions: $keepProportions")
            
            if (keepProportions && sourceAspectRatio != null) {
                when (lastEditedField) {
                    "width" -> {
                        widthText.toIntOrNull()?.let { width ->
                            if (width > 0) {
                                val newHeight = calculateProportionalDimension(width, true)
                                heightText = newHeight.toString()
                                Log.d("AspectRatio", "Calculated height: $newHeight based on width: $width")
                            }
                        }
                    }
                    "height" -> {
                        heightText.toIntOrNull()?.let { height ->
                            if (height > 0) {
                                val newWidth = calculateProportionalDimension(height, false)
                                widthText = newWidth.toString()
                                Log.d("AspectRatio", "Calculated width: $newWidth based on height: $height")
                            }
                        }
                    }
                }
            }
            
            // Apply odd number correction
            val correctionMade = correctOddNumbers()
            if (correctionMade) {
                Log.d("AspectRatio", "Odd number correction applied")
                // Auto-hide the message after 4 seconds
                delay(4000)
                oddNumberMessage = null
            }
            
            // Reset lastEditedField after processing
            lastEditedField = null
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = customOpenMultipleDocuments
    ) { uris: List<Uri>? ->
        uris?.let {
            if (it.size > 10) {
                Toast.makeText(context, "Cannot select more than 10 images", Toast.LENGTH_SHORT).show()
                return@let
            }
            val files = it.mapNotNull { uri ->
                val details = getFileDetails(context, uri) ?: return@mapNotNull null
                Pair(uri, details)
            }
            selectedFiles.clear()
            selectedFiles.addAll(files)
            conversionResults.clear()
            tickerMessage = null
            
            // Reset aspect ratio state when new images are loaded
            widthText = ""
            heightText = ""
            keepProportions = true
            hasUserEditedFields = false
            lastEditedField = null
            oddNumberMessage = null
            
            // Get dimensions from the first image
            if (files.isNotEmpty()) {
                val (width, height, ratio) = getImageDimensions(context, files.first().first)
                sourceWidth = width
                sourceHeight = height
                sourceAspectRatio = ratio
                Log.d("AspectRatio", "Source dimensions: ${width}x${height}, ratio: $ratio")
            }
        }
    }

    val horizontalPagerState = rememberPagerState(initialPage = 0) { 2 }

    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = horizontalPagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                    0 -> {
                        
                            ConstraintLayout(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() }
                                    ) {
                                        focusManager.clearFocus()
                                    }
                            ) {
                                val guideline = createGuidelineFromTop(0.8f)
                                val titleRef = createRef()
                                val topControlsRef = createRef()
                                val listRef = createRef()
                                val buttonRef = createRef()
                                val buttonSubtitleRef = createRef()
                                val tickerRef = createRef()
                                
                                // Title - only show when no files are selected
                                if (selectedFiles.isEmpty()) {
                                    Text(
                                        "HEIC to JPEG converter",
                                        style = MaterialTheme.typography.headlineMedium,
                                        modifier = Modifier.constrainAs(titleRef) {
                                            top.linkTo(parent.top, 48.dp)
                                            start.linkTo(parent.start)
                                            end.linkTo(parent.end)
                                        }
                                    )
                                }
                                
                                // Add aspect ratio controls when files are selected and not converting
                                if (selectedFiles.isNotEmpty() && !isConverting) {
                                    Column(
                                        modifier = Modifier
                                            .constrainAs(topControlsRef) {
                                                top.linkTo(parent.top, 16.dp)
                                                start.linkTo(parent.start)
                                                end.linkTo(parent.end)
                                                bottom.linkTo(listRef.top, 8.dp)
                                                width = Dimension.fillToConstraints
                                            }
                                            .padding(horizontal = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            OutlinedTextField(
                                                value = widthText,
                                                onValueChange = { newValue ->
                                                    widthText = newValue
                                                    hasUserEditedFields = true
                                                    lastEditedField = "width"
                                                },
                                                label = { 
                                                    Text(
                                                        if (hasUserEditedFields) "Width" else "Source Width",
                                                        fontSize = 12.sp
                                                    ) 
                                                },
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Number,
                                                    imeAction = ImeAction.Next
                                                ),
                                                keyboardActions = KeyboardActions(
                                                    onNext = {
                                                        focusManager.moveFocus(FocusDirection.Down)
                                                    }
                                                ),
                                                modifier = Modifier
                                                    .width(140.dp)
                                                    .height(56.dp)
                                                    .onFocusChanged { focusState ->
                                                        isWidthFocused = focusState.isFocused
                                                        if (focusState.isFocused && !hasUserEditedFields) {
                                                            widthText = ""
                                                            hasUserEditedFields = true
                                                        }
                                                    },
                                                singleLine = true,
                                                placeholder = { 
                                                    Text(
                                                        if (hasUserEditedFields) "" else "Same as source",
                                                        fontSize = 12.sp
                                                    ) 
                                                },
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Text("x", style = MaterialTheme.typography.bodyLarge)
                                            Spacer(modifier = Modifier.width(16.dp))
                                            OutlinedTextField(
                                                value = heightText,
                                                onValueChange = { newValue ->
                                                    heightText = newValue
                                                    hasUserEditedFields = true
                                                    lastEditedField = "height"
                                                },
                                                label = { 
                                                    Text(
                                                        if (hasUserEditedFields) "Height" else "Source Height",
                                                        fontSize = 12.sp
                                                    ) 
                                                },
                                                keyboardOptions = KeyboardOptions(
                                                    keyboardType = KeyboardType.Number,
                                                    imeAction = ImeAction.Done
                                                ),
                                                keyboardActions = KeyboardActions(
                                                    onDone = {
                                                        focusManager.clearFocus()
                                                    }
                                                ),
                                                modifier = Modifier
                                                    .width(140.dp)
                                                    .height(56.dp)
                                                    .onFocusChanged { focusState ->
                                                        isHeightFocused = focusState.isFocused
                                                        if (focusState.isFocused && !hasUserEditedFields) {
                                                            heightText = ""
                                                            hasUserEditedFields = true
                                                        }
                                                    },
                                                singleLine = true,
                                                placeholder = { 
                                                    Text(
                                                        if (hasUserEditedFields) "" else "Same as source",
                                                        fontSize = 12.sp
                                                    ) 
                                                },
                                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                                            )
                                        }
                                        
                                        // Odd number message
                                        if (oddNumberMessage != null) {
                                            Text(
                                                text = oddNumberMessage!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { keepProportions = !keepProportions }
                                        ) {
                                            Checkbox(
                                                checked = keepProportions,
                                                onCheckedChange = { keepProportions = it }
                                            )
                                            Text("Keep Aspect Ratio")
                                        }
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier
                                        .constrainAs(listRef) {
                                            top.linkTo(topControlsRef.bottom, 16.dp)
                                            start.linkTo(parent.start)
                                            end.linkTo(parent.end)
                                            bottom.linkTo(guideline, 32.dp)
                                            height = Dimension.fillToConstraints
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    state = rememberLazyListState()
                                ) {
                                    items(
                                        items = selectedFiles,
                                        key = { it.first }
                                    ) { file ->
                                        val (uri, details) = file
                                        Card(
                                            modifier = Modifier
                                                .animateItemPlacement()
                                                .padding(8.dp)
                                                .fillMaxWidth(0.8f)
                                                .clickable {
                                                    if (!isConverting) {
                                                        selectedFiles.remove(file)
                                                    }
                                                },
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)),
                                            shape = RectangleShape
                                        ) {
                                            Column(modifier = Modifier.padding(16.dp)) {
                                                Text(details.name, fontWeight = FontWeight.Bold, maxLines = 1)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Column {
                                                    val sizeMB = "%.2f MB".format(details.size / 1024.0 / 1024.0)
                                                    Text("Size: $sizeMB", style = MaterialTheme.typography.bodySmall)
                                                    val date = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(details.lastModified))
                                                    Text("Modified: $date", style = MaterialTheme.typography.bodySmall)
                                                    
                                                    // Add source dimensions display
                                                    if (sourceWidth != null && sourceHeight != null) {
                                                        Text(
                                                            "Source: ${sourceWidth}×${sourceHeight}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                val buttonText = if (selectedFiles.isEmpty()) "Load" else "Convert"
                                val isLoadButton = selectedFiles.isEmpty()
                                
                                // Subtitle above Load button - only show when no files are selected
                                if (selectedFiles.isEmpty()) {
                                    Text(
                                        "1 file or up to 10 at once",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.constrainAs(buttonSubtitleRef) {
                                            top.linkTo(guideline, margin = -110.dp)
                                            start.linkTo(parent.start)
                                            end.linkTo(parent.end)
                                        }
                                    )
                                }
                                
                                ElevatedButton(
                                    onClick = {
                                        if (isLoadButton) {
                                            imagePickerLauncher.launch(arrayOf("image/heic", "image/heif"))
                                        } else {
                                            if (isConverting) return@ElevatedButton
                                            
                                            // Get the target dimensions if specified
                                            val targetWidth = widthText.toIntOrNull()
                                            val targetHeight = heightText.toIntOrNull()
                                            
                                            // Convert all selected files with the specified dimensions
                                            isConverting = true
                                            tickerMessage = null
                                            
                                            scope.launch(Dispatchers.IO) {
                                                val results = mutableListOf<String>()
                                                val allSuccess = selectedFiles.map { (uri, details) ->
                                                    val res = convertSingle(context, uri, details.name, targetWidth, targetHeight)
                                                    results.add(res)
                                                    res.startsWith("Successfully")
                                                }.all { it }
                                                
                                                withContext(Dispatchers.Main) {
                                                    isConverting = false
                                                    // Start ticker with appropriate timing
                                                    scope.launch {
                                                        val messageDelayMs = if (selectedFiles.size >= 10) 10000L else 5000L
                                                        for (result in results) {
                                                            tickerMessage = result
                                                            delay(messageDelayMs)
                                                        }
                                                        tickerMessage = null
                                                        selectedFiles.clear()
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .constrainAs(buttonRef) {
                                            top.linkTo(guideline, margin = -60.dp)
                                            start.linkTo(parent.start)
                                            end.linkTo(parent.end)
                                        }
                                        .width(180.dp)
                                        .height(72.dp),
                                    enabled = !isConverting || isLoadButton,
                                    colors = if (isLoadButton) {
                                        ButtonDefaults.elevatedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.secondary,
                                            contentColor = MaterialTheme.colorScheme.onSecondary
                                        )
                                    } else {
                                        ButtonDefaults.elevatedButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    },
                                    elevation = ButtonDefaults.elevatedButtonElevation(
                                        defaultElevation = 6.dp,
                                        pressedElevation = 8.dp,
                                        disabledElevation = 0.dp
                                    )
                                ) {
                                    if (isConverting && selectedFiles.isNotEmpty()) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                    } else {
                                        Text(buttonText, fontSize = 24.sp)
                                    }
                                }

                                if (tickerMessage != null) {
                                    Text(
                                        text = tickerMessage!!,
                                        modifier = Modifier
                                            .constrainAs(tickerRef) {
                                                bottom.linkTo(parent.bottom, 16.dp)
                                                start.linkTo(parent.start)
                                                end.linkTo(parent.end)
                                            }
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                                            .padding(16.dp),
                                        textAlign = TextAlign.Center,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                    }
                    1 -> SettingsScreen(
                        selectedTheme = selectedTheme,
                        onThemeChange = { selectedTheme = it },
                        billingManager = billingManager,
                        hasDonated = hasDonated,
                        activity = activity
                    )
                }
            }

            // Conditional swipe hints - only show on main page
            if (horizontalPagerState.currentPage == 0) {
                SwipeHint(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 16.dp),
                    mirrored = false
                )
            }
        }
        } // End Surface
    } // End HEICtoJPEGTheme
}

suspend fun convertSingle(context: Context, uri: Uri, originalName: String, targetWidth: Int? = null, targetHeight: Int? = null): String {
    Log.d("ConvertSingle", "Starting conversion for $originalName")
    val startTime = System.currentTimeMillis()
    
    try {
        val tempInput = File(context.cacheDir, "temp_heic_${System.currentTimeMillis()}.heic")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempInput).use { output ->
                input.copyTo(output)
            }
        } ?: return "Error reading file".also { Log.e("ConvertSingle", "Content resolver failed to open input stream for URI: $uri") }

        val tempOutput = File(context.cacheDir, "temp_jpeg_${System.currentTimeMillis()}.jpg")
        Log.d("ConvertSingle", "Temp input: ${tempInput.absolutePath}, Temp output: ${tempOutput.absolutePath}")
        
        // If dimensions are provided, we'll need to resize after conversion
        val needsResize = targetWidth != null && targetHeight != null
        val tempConverted = if (needsResize) {
            File(context.cacheDir, "temp_converted_${System.currentTimeMillis()}.jpg")
        } else {
            tempOutput
        }

        // First convert HEIC to JPEG
        Log.d("ConvertSingle", "Calling native library to convert ${tempInput.absolutePath} to ${tempConverted.absolutePath}")
        var result = NativeLib.convertHeicToJpeg(tempInput.absolutePath, tempConverted.absolutePath)
        
        // If conversion was successful and we need to resize
        if (result.startsWith("Successfully") && needsResize) {
            try {
                // Load the converted image
                val options = BitmapFactory.Options()
                val bitmap = BitmapFactory.decodeFile(tempConverted.absolutePath, options)
                
                // Resize the bitmap
                val resizedBitmap = Bitmap.createScaledBitmap(
                    bitmap, 
                    targetWidth!!, 
                    targetHeight!!, 
                    true
                )
                
                // Save the resized bitmap
                FileOutputStream(tempOutput).use { out ->
                    resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                
                // Clean up
                resizedBitmap.recycle()
                bitmap.recycle()
                tempConverted.delete()
                
                result = "Successfully converted and resized to ${targetWidth}x${targetHeight}"
                Log.d("ConvertSingle", "Successfully resized image to ${targetWidth}x${targetHeight}")
            } catch (e: Exception) {
                tempConverted.delete()
                Log.e("ConvertSingle", "Error during image resizing", e)
                return "Error resizing image: ${e.message}"
            }
        }

        if (result.startsWith("Successfully")) {
            // Try to determine the source folder and save to the same location
            val (targetPath, saveMessage) = determineTargetPath(context, uri)
            val jpegName = originalName.replace(Regex("\\.heic?$", RegexOption.IGNORE_CASE), ".jpg")

            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, jpegName)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, targetPath)
            }

            val resolver = context.contentResolver
            val insertUri = try {
                resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            } catch (e: Exception) {
                Log.w("ConvertSingle", "Failed to save to $targetPath, falling back to Pictures", e)
                // Fall back to Pictures if the original path fails
                contentValues.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures")
                resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            }

            insertUri?.let { uri ->
                resolver.openOutputStream(uri)?.use { output ->
                    FileInputStream(tempOutput).use { input ->
                        input.copyTo(output)
                    }
                } ?: return "Error writing file"
                
                // Update result message to include save location info
                result = "$result $saveMessage"
            } ?: return "Error creating media entry"
        } else {
            return result
        }

        tempInput.delete()
        tempOutput.delete()

        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        return "$result (${duration}ms)"
    } catch (e: Exception) {
        Log.e("ConvertSingle", "General error in convertSingle for $originalName", e)
        return "Error: ${e.message}"
    }
}

fun determineTargetPath(context: Context, sourceUri: Uri): Pair<String, String> {
    try {
        // Try to get the source directory from the URI
        val sourcePath = when {
            sourceUri.scheme == "content" && sourceUri.authority == "com.android.externalstorage.documents" -> {
                // This is a Documents Provider URI
                val docId = DocumentsContract.getDocumentId(sourceUri)
                val split = docId.split(":", limit = 2)
                if (split.size >= 2) {
                    val path = split[1]
                    path.substringBeforeLast("/", "")
                } else null
            }
            else -> null
        }

        Log.d("determineTargetPath", "Source URI: $sourceUri")
        Log.d("determineTargetPath", "Extracted source path: $sourcePath")

        // Determine target path based on source path
        val (targetPath, message) = when {
            sourcePath?.lowercase()?.contains("download") == true -> {
                // Downloads folder - MediaStore doesn't allow saving here, fall back to Pictures
                "Pictures" to "(saved to Pictures - Downloads not allowed by Android)"
            }
            sourcePath?.lowercase()?.contains("dcim") == true -> {
                // DCIM folder - this is allowed
                if (sourcePath.startsWith("DCIM/")) {
                    sourcePath to "(saved to same folder: $sourcePath)"
                } else {
                    "DCIM" to "(saved to DCIM folder)"
                }
            }
            sourcePath?.lowercase()?.contains("picture") == true -> {
                // Pictures folder - this is allowed
                if (sourcePath.startsWith("Pictures/")) {
                    sourcePath to "(saved to same folder: $sourcePath)"
                } else {
                    "Pictures" to "(saved to Pictures folder)"
                }
            }
            !sourcePath.isNullOrEmpty() -> {
                // Other folder - try to use it, but it might fail
                sourcePath to "(attempting same folder: $sourcePath)"
            }
            else -> {
                // Couldn't determine source - use Pictures as default
                "Pictures" to "(saved to Pictures folder)"
            }
        }

        Log.d("determineTargetPath", "Target path: $targetPath, Message: $message")
        return targetPath to message

    } catch (e: Exception) {
        Log.e("determineTargetPath", "Error determining target path", e)
        return "Pictures" to "(saved to Pictures folder)"
    }
}

@Composable
fun SettingsScreen(
    selectedTheme: ThemeOption,
    onThemeChange: (ThemeOption) -> Unit,
    billingManager: BillingManager,
    hasDonated: Boolean,
    activity: ComponentActivity
) {
    val context = LocalContext.current
    var showThanks by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Theme Toggle section at top
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(16.dp))
        
        // Light/Dark Mode Switch with dynamic label
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { 
                    onThemeChange(if (selectedTheme == ThemeOption.Light) ThemeOption.Dark else ThemeOption.Light)
                }
                .padding(vertical = 8.dp)
        ) {
            // Switch on the left
            Switch(
                checked = selectedTheme == ThemeOption.Dark,
                onCheckedChange = { isDark ->
                    onThemeChange(if (isDark) ThemeOption.Dark else ThemeOption.Light)
                }
            )
            
            Spacer(Modifier.width(16.dp))
            
            // Dynamic text that shows current mode
            Text(
                text = if (selectedTheme == ThemeOption.Light) "Light Mode" else "Dark Mode",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        // This spacer takes up all remaining space, pushing the message to bottom
        Spacer(Modifier.weight(1f))
        
        // Source Code Information Message at bottom
        Text(
            text = "Source code available on request. Send a blank message with the subject line \"Source Code\" to legeappdesign@gmail.com and the source code will be sent to you automatically. This is a requirement of app component licensing",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(Modifier.height(16.dp))
        
        // Donation Link - only show if not donated yet
        if (!hasDonated && !showThanks) {
            Text(
                text = "Donate",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable {
                        billingManager.makeDonation(activity)
                        showThanks = true
                    }
            )
        } else if (showThanks || hasDonated) {
            Text(
                text = "Thanks!",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            
            // Hide the thanks message after 3 seconds
            LaunchedEffect(showThanks) {
                if (showThanks) {
                    kotlinx.coroutines.delay(3000)
                    showThanks = false
                }
            }
        }
        
        Spacer(Modifier.height(32.dp))
    }
}