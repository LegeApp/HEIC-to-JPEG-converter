package com.example.heictojpeg

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.heictojpeg.ui.theme.HEICtoJPEGTheme
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            HEICtoJPEGTheme {
                SplashScreen()
            }
        }
    }
    
    @Composable
    fun SplashScreen() {
        var loadingMessage by remember { mutableStateOf("Loading..") }
        
        // Animate loading messages
        LaunchedEffect(Unit) {
            val messages = listOf(
                "Loading..",
                "Initializing converter..",
                "Preparing image processing..",
                "Ready!"
            )
            
            for (message in messages) {
                loadingMessage = message
                delay(500) // Change message every 500ms
            }
            
            // Navigate to main activity after 2 seconds total
            delay(500)
            startActivity(Intent(this@SplashActivity, MainActivity::class.java))
            finish()
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Center content (icon and app name)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.align(Alignment.Center)
            ) {
                // App Icon
                Image(
                    painter = painterResource(id = R.drawable.splash_icon), // Using your clean 512x512 PNG
                    contentDescription = "App Icon",
                    modifier = Modifier.size(120.dp)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // App Name with GoogleSans font
                Text(
                    text = "HEIC to JPEG converter",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily(Font(R.font.googlesans_regular))
                )
            }
            
            // Loading message at bottom center
            Text(
                text = loadingMessage,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                fontFamily = FontFamily(Font(R.font.googlesans_regular)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 64.dp)
            )
        }
    }
}
