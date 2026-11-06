// Mio 3.0：工具链与真实 BiliPai 同代（AGP 9 / Kotlin 2.4 / Compose 1.12），
// 以便直接消费搬运自 BiliPai 的 design-tokens / design-system（Miuix 设计语言）。
// R8 与 AGP 分开固定（与 BiliPai 一致），便于更新优化器而不动 AGP 系列。
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools:r8:9.5.28")
    }
}

plugins {
    id("com.android.application") version "9.3.2" apply false
    id("com.android.library") version "9.3.2" apply false
    // AGP 9+ 内置 Kotlin，无需 org.jetbrains.kotlin.android
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
}
