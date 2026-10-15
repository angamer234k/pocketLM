import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreDir = rootProject.file("keystore")
val keystoreB64 = keystoreDir.resolve("pocketlm-debug.p12.b64")
val keystoreFile = keystoreDir.resolve("pocketlm-debug.p12")
if (keystoreB64.exists()) {
    val bytes = Base64.getDecoder().decode(keystoreB64.readText().replace(Regex("\\s"), ""))
    if (!keystoreFile.exists() || keystoreFile.length() != bytes.size.toLong()) {
        keystoreDir.mkdirs()
        keystoreFile.writeBytes(bytes)
    }
}

android {
    namespace = "com.angamer.pocketlm"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.angamer.pocketlm"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-O3")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_OPENMP=OFF"
                )
            }
        }
    }

    signingConfigs {
        create("pocketlm") {
            storeFile = keystoreFile
            storePassword = "pocketlm"
            keyAlias = "pocketlm"
            keyPassword = "pocketlm"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("pocketlm")
        }
        release {
            signingConfig = signingConfigs.getByName("pocketlm")
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
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.google.mediapipe:tasks-genai:0.10.24")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
