import java.util.Properties
import kotlin.apply

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")

}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
// 키가 없거나 CI 에서 secret 이 비어 "null"/"" 로 들어온 경우를 모두 null 로 취급
fun adProperty(key: String): String? =
    localProperties.getProperty(key)?.trim()?.takeUnless { it.isEmpty() || it == "null" }

val adsId = adProperty("ADS_ID")
val adsFixedSizeBannerUnitId = adProperty("ADS_FIXED_SIZE_BANNER_UNIT_ID")

// Google 공식 테스트 ID (https://developers.google.com/admob/android/test-ads)
// debug 빌드는 local.properties 와 무관하게 항상 테스트 ID 사용 → 개발 중 실광고 노출/무효 클릭 방지
val admobTestPublisher = "ca-app-pub-3940256099942544"
val admobTestAppId = "$admobTestPublisher~3347511713"
val admobTestBannerId = "$admobTestPublisher/6300978111"

// release 빌드 AdMob ID 검증. 로컬에서 테스트 ID 로 release 를 만들어야 할 때만 -PallowTestAds=true
val allowTestAds = providers.gradleProperty("allowTestAds").orNull?.toBoolean() == true
fun releaseAdMobProblems(): List<String> = buildList {
    fun check(key: String, value: String?, pattern: Regex, example: String) {
        when {
            value == null -> add("$key 가 없거나 비어 있음/\"null\" 입니다")
            !pattern.matches(value) -> add("$key 형식 오류: '$value' (예: $example)")
            value.startsWith(admobTestPublisher) && !allowTestAds ->
                add("$key 가 Google 테스트 ID($admobTestPublisher) 입니다")
        }
    }
    check("ADS_ID", adsId, Regex("""ca-app-pub-\d{16}~\d{10}"""), "ca-app-pub-0000000000000000~0000000000")
    check(
        "ADS_FIXED_SIZE_BANNER_UNIT_ID", adsFixedSizeBannerUnitId,
        Regex("""ca-app-pub-\d{16}/\d{10}"""), "ca-app-pub-0000000000000000/0000000000",
    )
}

android {
    namespace = "com.jinscompany.saveurl"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jinscompany.saveurl"
        minSdk = 24
        targetSdk = 36
        versionCode = 38
        versionName = "2.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // AdMob ID 는 buildType 별로 지정 (아래 buildTypes 참고)
        buildConfigField("String", "AdMobBannerIdDubug", "\"$admobTestBannerId\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("jks/saveurlkey.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 값 검증은 verifyReleaseAdMobIds 태스크가 release 빌드 시에만 수행 (debug 빌드/IDE sync 는 막지 않음)
            manifestPlaceholders["AdMobId"] = adsId ?: ""
            buildConfigField("String", "AdMobId", "\"${adsId ?: ""}\"")
            buildConfigField("String", "AdMobBannerUnitId", "\"${adsFixedSizeBannerUnitId ?: ""}\"")
        }
        debug {
            manifestPlaceholders["AdMobId"] = admobTestAppId
            buildConfigField("String", "AdMobId", "\"$admobTestAppId\"")
            buildConfigField("String", "AdMobBannerUnitId", "\"$admobTestBannerId\"")
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
        buildConfig = true
    }
}

// release 변형의 모든 빌드(assembleRelease, bundleRelease 등)는 preReleaseBuild 를 거치므로 여기에 가드를 건다
val verifyReleaseAdMobIds by tasks.registering {
    group = "verification"
    description = "release 빌드의 AdMob ID 가 실제 ID 인지 검증 (테스트 ID/누락/형식 오류 시 실패)"
    val problems = releaseAdMobProblems()
    val allowed = allowTestAds
    doLast {
        if (problems.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("AdMob release 가드 실패 — 테스트/잘못된 광고 ID 로 release 를 빌드할 수 없습니다.")
                    problems.forEach { appendLine("  - $it") }
                    appendLine("local.properties(또는 CI secret)의 ADS_ID / ADS_FIXED_SIZE_BANNER_UNIT_ID 를 실제 값으로 설정하세요.")
                    append("로컬에서 테스트 ID 로 release 를 확인해야 한다면: ./gradlew assembleRelease -PallowTestAds=true")
                }
            )
        }
        if (allowed) logger.warn("-PallowTestAds=true — AdMob 테스트 ID 로 release 빌드 중. 이 산출물을 배포하지 마세요.")
    }
}
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseAdMobIds) }

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    // 폴드·태블릿 2분할 기반 (버전은 Compose BOM 이 관리)
    implementation(libs.androidx.material3.adaptive)
    implementation(libs.androidx.material3.adaptive.layout)
    implementation(libs.androidx.material3.adaptive.navigation)

    implementation("androidx.navigation:navigation-compose:2.8.8")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    implementation("com.google.dagger:hilt-android:2.56.2")
    ksp("com.google.dagger:hilt-android-compiler:2.56.2")

    implementation("androidx.room:room-runtime:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1") //KTX Extensions/Coroutines for Room
    implementation("androidx.room:room-paging:2.6.1")

    implementation("org.jsoup:jsoup:1.14.3")
    // 링크 미리보기 HTTP/2 전송 (Coil 이 이미 가져오는 버전과 동일)
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("com.github.bumptech.glide:glide:4.15.1")

    androidTestImplementation("androidx.room:room-testing:2.6.1")

    implementation("io.coil-kt:coil-compose:2.4.0")


    implementation("com.google.accompanist:accompanist-webview:0.24.13-rc")

    implementation("net.dankito.readability4j:readability4j:1.0.8")
    implementation("org.apache.commons:commons-lang3:3.6")

    implementation("androidx.paging:paging-runtime:3.3.6")
    implementation("androidx.paging:paging-compose:3.3.6")

    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-config")

    implementation("com.google.android.play:app-update:2.1.0")
    implementation("com.google.android.play:app-update-ktx:2.1.0")

    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("com.google.android.material:material:1.12.0")

    implementation("androidx.datastore:datastore-preferences:1.1.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    implementation("com.google.android.gms:play-services-ads:24.3.0")
    // TODO: 통신판매업 신고 완료 후 활성화
    // implementation("com.android.billingclient:billing-ktx:7.1.1")

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("app.cash.turbine:turbine:1.1.0")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("androidx.arch.core:core-testing:2.2.0")

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation("androidx.customview:customview-poolingcontainer:1.0.0")
}