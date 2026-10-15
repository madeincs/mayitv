plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mayitv.app"
    compileSdk = 34

    // 固定签名密钥：所有版本同一把钥匙，新版可覆盖旧版（免卸载热更新）
    signingConfigs {
        create("fixed") {
            storeFile = file("release.p12")
            storePassword = "mayitv123"
            keyAlias = "mayitv"
            keyPassword = "mayitv123"
        }
    }

    defaultConfig {
        applicationId = "com.mayitv.app"
        minSdk = 21
        targetSdk = 34
        versionCode = (project.findProperty("versionCode")?.toString()?.toIntOrNull()) ?: 1
        versionName = "1.0"
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Media3 ExoPlayer (HLS/m3u8 native playback)
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.1")
    implementation("androidx.media3:media3-datasource:1.2.1")
    implementation("androidx.media3:media3-ui:1.2.1")

    // Networking + parsing + image loading
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.github.bumptech.glide:glide:4.16.0")
}
