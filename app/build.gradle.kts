import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.qingricheng.app"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.qingricheng.app"
        minSdk = 30
        targetSdk = 37
        versionCode = 13
        versionName = "0.6.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("io.github.kyant0:backdrop:1.0.2")
    implementation(files("libs/pdfbox-android-2.0.27.0.aar"))
    implementation(files("libs/bcprov-jdk15to18-1.72.jar"))
    implementation(files("libs/bcpkix-jdk15to18-1.72.jar"))
    implementation(files("libs/bcutil-jdk15to18-1.72.jar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
