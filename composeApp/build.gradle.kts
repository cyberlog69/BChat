plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
            freeCompilerArgs.add("-Xjdk-release=17")
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.materialIconsExtended)
            
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtime.ktx)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.navigation.compose)
            
            // Google Play Services Nearby Connections (Android)
            implementation(libs.play.services.nearby)
            
            // Coroutines & Serialization for Android
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.kotlinx.coroutines.play.services)
            
            // QR Code & Camera scanning
            implementation(libs.zxing.core)
            implementation(libs.camera.camera2)
            implementation(libs.camera.lifecycle)
            implementation(libs.camera.view)
            implementation(libs.mlkit.barcode.scanning)
        }
        
        iosMain.dependencies {
            // Apple Native frameworks (MultipeerConnectivity, Network, CoreBluetooth) are built-in
        }
    }
}

android {
    namespace = "com.praveen.bchat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.praveen.bchat"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
