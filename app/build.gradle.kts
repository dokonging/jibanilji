plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.jibanilji.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jibanilji.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "2.0"
    }
    // 빌드할 때마다 같은 서명 → 지우지 않고 덮어서 업데이트 가능 (개인용 키)
    signingConfigs {
        getByName("debug") {
            storeFile = file("jibanilji.keystore")
            storePassword = "sallimi"
            keyAlias = "sallimi"
            keyPassword = "sallimi"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    // 구글 로그인 (Credential Manager)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    // 무료 · 기기 내 글자 인식(한국어)
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    // 로그인 + 공유 데이터 (Firebase 무료 Spark 요금제로 충분)
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
