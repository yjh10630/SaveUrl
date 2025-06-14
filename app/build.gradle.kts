plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.jinscompany.saveurl"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jinscompany.saveurl"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation("androidx.navigation:navigation-compose:2.8.8")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("com.google.dagger:hilt-android:2.48")
    ksp("com.google.dagger:hilt-android-compiler:2.48")

    implementation("androidx.room:room-runtime:2.6.1")
    ksp("android.arch.persistence.room:compiler:1.1.1")
    ksp("androidx.room:room-compiler:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1") // use kapt for Kotlin
    implementation("androidx.room:room-ktx:2.6.1") //KTX Extensions/Coroutines for Room

    implementation("org.jsoup:jsoup:1.14.3")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("com.github.bumptech.glide:glide:4.15.1")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")

    testImplementation("androidx.room:room-testing:2.6.1")

    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("io.coil-kt:coil-compose:2.4.0")


    implementation("com.google.accompanist:accompanist-webview:0.24.13-rc")

    implementation("net.dankito.readability4j:readability4j:1.0.8")
    implementation("org.apache.commons:commons-lang3:3.6")

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}