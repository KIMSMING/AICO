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
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.annotation)
    implementation(libs.lifecycle.livedata.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // OkHttp 라이브러리 의존성 추가
    implementation("com.squareup.okhttp3:okhttp:4.9.3")

    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")  // Firebase 인증
    implementation("com.google.firebase:firebase-database")  // RealTime

    implementation("com.squareup.retrofit2:retrofit:2.9.0")       // Retrofit 본체
    implementation("com.squareup.retrofit2:converter-gson:2.9.0") // JSON 응답을 객체로 변환하는 Gson 컨버터

    implementation("com.google.android.material:material:1.11.0") // 레이아웃 머터리얼

    // 사용자 정보 조회 수정
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")
    implementation("com.google.firebase:firebase-storage:20.3.0")

    // 구글 로그인
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // 네이버 로그인 SDK
    implementation("com.navercorp.nid:oauth-jdk8:5.10.0")

    // 카카오 로그인 SDK
    implementation("com.kakao.sdk:v2-user:2.19.0")

    // 안드로이드X 라이브러리 등 필요한 의존성 추가
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")

    // 파일 요청
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // 이미지 화면출력
    implementation("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

}