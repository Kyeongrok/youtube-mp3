plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 릴리즈 태그(v1.0.23)에서 넘겨받는다. 로컬 빌드는 기본값을 쓴다.
val appVersionName = (findProperty("appVersionName") as String?) ?: "0.0.0"
val appVersionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1

android {
    namespace = "com.kyeongrok.youtubemp3"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.kyeongrok.youtubemp3"
        minSdk = 29
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
    }

    // 파이썬·FFmpeg가 ABI마다 들어가 하나로 묶으면 너무 크므로 ABI별 APK로 나눈다.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    // 키가 늘 같아야 폰에서 지우지 않고 업데이트된다. 비밀값이 없으면 디버그 키로 서명한다.
    signingConfigs {
        val keystorePath = System.getenv("ANDROID_KEYSTORE_FILE")
        if (!keystorePath.isNullOrEmpty()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASS")
                keyAlias = "youtubemp3"
                keyPassword = System.getenv("ANDROID_KEYSTORE_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        // 라이브러리가 파이썬·FFmpeg를 .so로 실행하므로 설치 시 파일로 풀려 있어야 한다.
        jniLibs.useLegacyPackaging = true
    }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
