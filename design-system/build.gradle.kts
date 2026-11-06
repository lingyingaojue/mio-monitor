plugins {
    id("com.android.library")
    // AGP 9+ built-in Kotlin
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.android.purebilibili.designsystem"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    api(project(":design-tokens"))
    api(platform(libs.androidx.compose.bom))
    api("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    api("androidx.compose.foundation:foundation")
    api("androidx.compose.animation:animation")
    api("androidx.compose.material:material-icons-extended")
    api("androidx.compose.material3:material3:1.5.0-alpha29")
    api("dev.chrisbanes.haze:haze:2.0.1")
    api("dev.chrisbanes.haze:haze-blur:2.0.1")
    implementation("dev.chrisbanes.haze:haze-blur-materials:2.0.1")
    api(libs.miuix.ui)
    // Shared liquid value controls render a safe sibling track backdrop.
    implementation(libs.miuix.blur)
    implementation(libs.miuix.preference)
    // ModalBottomSheet/Dialog 侧边预测返回：与 app 内 NavigationBackHandler 同栈。
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
}
