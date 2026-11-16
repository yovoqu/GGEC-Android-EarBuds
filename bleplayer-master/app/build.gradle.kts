plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ambiq.blevideoplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ambiq.blevideoplayer"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
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

    aaptOptions {
        noCompress += "onnx"
    }

    // AGP 8.5.2 的 Lint 在 JDK 22+ 上会以运行时版本号失败（Android Studio 自带 JBR 为 25.0.3），
    // 任务失败后 LintClassLoaderBuildService 也无法停止。JDK 17 仍执行 release vital lint。
    lint {
        checkReleaseBuilds = JavaVersion.current() <= JavaVersion.VERSION_21
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
}
