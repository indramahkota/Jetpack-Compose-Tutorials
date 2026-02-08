import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.smarttoolfactory.tutorial4_1chatbot"
    compileSdk {
        version = release(36)
    }

    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")

    if (localPropertiesFile.exists()) {
        localProperties.load(localPropertiesFile.inputStream())
    }

    defaultConfig {
        applicationId = "com.smarttoolfactory.tutoria4_1chatbot"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Add your own API_KEY from OPEN AI to use chat gpt api
        val apiKey: String = localProperties.getProperty("API_KEY") ?: ""

        buildConfigField(
            "String",
            "API_KEY",
            "\"$apiKey\""
        )
    }

    buildTypes {

        debug {

        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewModelCompose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.iconsExtended)

    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.chucker)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.moshi)
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.kotlin.codegen)
    ksp(libs.kotlin.metadata.jvm)
    implementation(libs.androidx.compose.ui.graphics)

    implementation(libs.richtext.ui)
    implementation("com.halilibo.compose-richtext:richtext-commonmark:1.0.0-alpha03") {
        exclude(group = "com.atlassian.commonmark", module = "commonmark")
        exclude(group = "com.atlassian.commonmark", module = "commonmark-ext-gfm-strikethrough")
        exclude(group = "com.atlassian.commonmark", module = "commonmark-ext-gfm-tables")
    }
    implementation("com.github.jeziellago:compose-markdown:0.5.8") {
        exclude(group = "com.atlassian.commonmark", module = "commonmark")
        exclude(group = "com.atlassian.commonmark", module = "commonmark-ext-gfm-strikethrough")
        exclude(group = "com.atlassian.commonmark", module = "commonmark-ext-gfm-tables")
    }
    implementation("io.noties.markwon:core:4.6.2") {
        exclude(group = "com.atlassian.commonmark", module = "commonmark")
        exclude(group = "com.atlassian.commonmark", module = "commonmark-ext-gfm-strikethrough")
        exclude(group = "com.atlassian.commonmark", module = "commonmark-ext-gfm-tables")
    }
    implementation("io.noties.markwon:ext-tables:4.6.2") {
        exclude(group = "com.atlassian.commonmark", module = "commonmark")
        exclude(group = "com.atlassian.commonmark", module = "commonmark-ext-gfm-strikethrough")
        exclude(group = "com.atlassian.commonmark", module = "commonmark-ext-gfm-tables")
    }

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}