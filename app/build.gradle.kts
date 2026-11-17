plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.lume.launcher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lume.launcher"
        minSdk = 30
        targetSdk = 33 // o projetor roda Android 13
        versionCode = 1
        versionName = "0.1"
    }

    // Chave de plataforma pública do AOSP (test-keys). O firmware do projetor foi assinado com ela,
    // o que permite rodar como android.uid.system e acessar o SystemControl da Amlogic.
    signingConfigs {
        create("platform") {
            storeFile = rootProject.file("keys/platform.p12")
            storePassword = "android"
            keyAlias = "platform"
            keyPassword = "android"
            storeType = "pkcs12"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("platform") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("platform")
        }
    }

    lint { checkReleaseBuilds = false }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
