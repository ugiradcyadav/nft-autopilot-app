plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.sadhu.nftautopilot"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.sadhu.nftautopilot"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "4.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
            buildConfigField("Boolean", "IS_PRODUCTION", "false")
            buildConfigField("String", "DEFAULT_CHAIN_ID", "\"80002\"")
            buildConfigField("String", "DEFAULT_RPC_URL", "\"https://rpc-amoy.polygon.technology\"")
            buildConfigField("String", "POLYGONSCAN_BASE_URL", "\"https://amoy.polygonscan.com\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("Boolean", "IS_PRODUCTION", "true")
            buildConfigField("String", "DEFAULT_CHAIN_ID", "\"137\"")
            buildConfigField("String", "DEFAULT_RPC_URL", "\"https://polygon-rpc.com\"")
            buildConfigField("String", "POLYGONSCAN_BASE_URL", "\"https://polygonscan.com\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.13" }
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DISCLAIMER",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
                "org/bouncycastle/x509/CertPathReviewerMessages*.properties"
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.biometric)
    implementation(libs.coroutines.android)
    implementation(libs.web3j.core)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.security.crypto)
    implementation(libs.datastore.prefs)
    debugImplementation(libs.compose.ui.tooling)
}
