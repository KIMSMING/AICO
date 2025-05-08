plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.seoja.aico"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.seoja.aico"
        minSdk = 24
        targetSdk = 35
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")  // Firebase 인증
    implementation("com.google.firebase:firebase-database")  // RealTime

    // 구글 로그인
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // 네이버 로그인 SDK
    implementation("com.navercorp.nid:oauth:5.10.0")

    // 카카오 로그인 SDK
    implementation("com.kakao.sdk:v2-user:2.19.0")

    // 안드로이드X 라이브러리 등 필요한 의존성 추가
    implementation("androidx.core:core-ktx:1.12.0")
}