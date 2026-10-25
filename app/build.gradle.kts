plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.searxngrunner.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.searxngrunner.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            // 디버그 APK만 사용하므로 release 서명은 설정하지 않습니다.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // assets/*.sh 는 압축하지 않아야 앱에서 원문 그대로 읽힙니다.
    androidResources {
        noCompress += "sh"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // 외부 라이브러리를 쓰지 않습니다. Kotlin 표준 라이브러리만 Kotlin 플러그인이 자동 추가합니다.
}
