plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Clone llama.cpp at configuration time (before CMake runs during sync)
val llamaCppDir = file("src/main/cpp/llama.cpp")
val llamaCppCMake = file("src/main/cpp/llama.cpp/CMakeLists.txt")

if (!llamaCppCMake.exists()) {
    println("llama.cpp not found, cloning repository...")
    
    // Remove empty directory if it exists
    if (llamaCppDir.exists() && (llamaCppDir.list()?.isEmpty() == true)) {
        llamaCppDir.delete()
    }
    
    exec {
        commandLine("git", "clone", "--depth", "1", "https://github.com/ggerganov/llama.cpp.git", llamaCppDir.absolutePath)
    }
    
    println("llama.cpp cloned successfully!")
}

android {
    namespace = "com.example.localchatbot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.localchatbot"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        
        externalNativeBuild {
            cmake {
                arguments += "-DLLAMA_CURL=OFF"
                arguments += "-DLLAMA_BUILD_COMMON=ON"
                arguments += "-DGGML_LLAMAFILE=OFF"
                arguments += "-DGGML_OPENMP=OFF"
                arguments += "-DGGML_NEON=ON"
                arguments += "-DCMAKE_BUILD_TYPE=Release"
                cppFlags += listOf("-O3", "-ffast-math", "-fno-finite-math-only", "-march=armv8-a+simd")
                cFlags += listOf("-O3", "-ffast-math", "-fno-finite-math-only", "-march=armv8-a+simd")
            }
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
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    buildFeatures {
        compose = true
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    
    implementation("com.facebook.soloader:soloader:0.10.5")
    
    // ExecuTorch disabled - AAR missing Java classes
    // To enable, build AAR from source: https://github.com/pytorch/executorch
    // implementation(files("libs/executorch.aar"))
    // implementation("com.facebook.fbjni:fbjni:0.5.1")
    
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
