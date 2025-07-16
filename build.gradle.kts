plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.jetbrains.compose)
}

android {
    namespace = "lege.heictojpeg.converter"
    compileSdk = 35

    defaultConfig {
        applicationId = "lege.heictojpeg.converter"
        minSdk = 27
        targetSdk = 35
        versionCode = 3
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Explicitly specify which ABIs to build for CMake
        externalNativeBuild {
            cmake {
                abiFilters("arm64-v8a", "x86_64")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    
    // APK splits configuration - only enable for assemble tasks, not for bundle tasks
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("assemble", ignoreCase = true) }
            reset()
            // Include arm64-v8a (required) and x86_64 (emulator) - the ABIs we have working libraries for
            include("arm64-v8a", "x86_64")
            isUniversalApk = false // Don't generate universal APK containing all ABIs
        }
    }
    
    // App Bundle configuration - includes all ABIs for Google Play to optimize
    bundle {
        abi {
            enableSplit = true
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        compose = true
    }
    
    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }
    
    ndkVersion = "29.0.13599879"




}



dependencies {
    // Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.1")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.documentfile)

    // Google Play Billing
    implementation("com.android.billingclient:billing-ktx:7.0.0")

    // Jetpack Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Compose UI Components
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Test Dependencies
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}