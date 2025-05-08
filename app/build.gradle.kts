import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Load keystore properties from local.properties file
val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties()
if (localPropertiesFile.exists()) {
    localProperties.load(FileInputStream(localPropertiesFile))
}

android {
    namespace = "com.watchware.mp3"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.watchware.mp3"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Vector drawables support
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Only use the signing config if all required properties are available
            val keystoreFile = localProperties.getProperty("storeFile")
            val keystorePassword = localProperties.getProperty("storePassword")
            val keyAlias = localProperties.getProperty("keyAlias")
            val keyPassword = localProperties.getProperty("keyPassword")
            
            if (keystoreFile != null && keystorePassword != null && 
                keyAlias != null && keyPassword != null) {
                storeFile = rootProject.file(keystoreFile)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            } else {
                // Provide feedback about missing signing config
                logger.warn("Signing config not applied. Check that all required properties are in local.properties")
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
            
            // Only apply signing config if all properties exist
            val hasAllSigningProperties = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
                .all { localProperties.getProperty(it) != null }
                
            if (hasAllSigningProperties) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Base Wear OS dependencies
    implementation(libs.play.services.wearable)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material)
    // Use the specific wear compose foundation instead of the generic one
    implementation(libs.compose.foundation)
    implementation(libs.material3) // Material3 support
    implementation(libs.wear.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    
    // Additional Wear OS specific components
    implementation(libs.wear.core)
    
    // Media3 ExoPlayer dependencies
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.androidx.media3.extractor) // For metadata extraction
    
    // Media Session dependencies for Bluetooth headset controls
    implementation(libs.androidx.media)
    implementation(libs.androidx.media3.session)
    
    // Gson for JSON serialization
    implementation(libs.gson)
    
    // Lifecycle components
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    
    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)
    
    // Palette dependency
    implementation(libs.androidx.palette.ktx)
    
    // Testing
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.ui.test.junit4)
    debugImplementation(libs.ui.tooling)
    debugImplementation(libs.ui.test.manifest)
}