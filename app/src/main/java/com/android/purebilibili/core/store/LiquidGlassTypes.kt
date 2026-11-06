/*
 * Vendored from BiliPai v0.7.8 `app/src/main/java/com/android/purebilibili/core/store/SettingsManager.kt`
 * (LiquidGlassStyle / LiquidGlassMode / LiquidGlassAdvancedSettings / 归一化函数，原样抽取)。
 *
 * 抽取原因：SettingsManager.kt 共 9000+ 行且依赖 DataStore/播放器设置，Mio 只需要液态玻璃
 * 参数类型；为避免拖入整条设置链，这里只保留 BiliPai 原声明，代码逻辑未改动。
 * 搬运记录见 design-system/VENDORED.md。
 */
package com.android.purebilibili.core.store

enum class LiquidGlassStyle(val value: Int) {
    CLASSIC(0),      // BiliPai's Wavy Ripple
    SUKISU(1),       // SukiSU floating bottom bar glass
    IOS26(2);        // iOS26-like layered liquid glass

    companion object {
        fun fromValue(value: Int): LiquidGlassStyle = entries.find { it.value == value } ?: CLASSIC
    }
}

enum class LiquidGlassMode(val value: Int, val label: String) {
    CLEAR(0, "通透玻璃"),
    BALANCED(1, "平衡"),
    FROSTED(2, "柔和磨砂");

    companion object {
        fun fromValue(value: Int): LiquidGlassMode = entries.find { it.value == value } ?: BALANCED
    }
}

enum class LiquidGlassAdvancedPreset(val value: Int, val label: String) {
    READABLE(0, "清晰"),
    BALANCED(1, "均衡"),
    PRISM(2, "棱镜"),
    CUSTOM(3, "自定");

    companion object {
        fun fromValue(value: Int): LiquidGlassAdvancedPreset =
            entries.find { it.value == value } ?: BALANCED
    }
}

data class LiquidGlassAdvancedSettings(
    val preset: LiquidGlassAdvancedPreset = LiquidGlassAdvancedPreset.BALANCED,
    val progressiveBlurRadius: Float = 0.40f,
    val progressiveBlurExtent: Float = 1.0f,
    val progressiveBlurCurve: Float = 0.55f,
    val contentReadability: Float = 0.62f,
    val chromaticAberration: Float = 0.56f,
    val contentDistortion: Float = 0.45f,
)

internal fun resolveLegacyLiquidGlassMode(style: LiquidGlassStyle): LiquidGlassMode = when (style) {
    LiquidGlassStyle.IOS26 -> LiquidGlassMode.CLEAR
    LiquidGlassStyle.CLASSIC -> LiquidGlassMode.BALANCED
    LiquidGlassStyle.SUKISU -> LiquidGlassMode.BALANCED
}

internal fun resolveDefaultLiquidGlassStrength(mode: LiquidGlassMode): Float = when (mode) {
    LiquidGlassMode.CLEAR -> 0.42f
    LiquidGlassMode.BALANCED -> 0.52f
    LiquidGlassMode.FROSTED -> 0.62f
}

internal fun normalizeLiquidGlassStrength(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0.52f

internal fun normalizeLiquidGlassProgress(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0.5f

internal fun resolveLegacyLiquidGlassProgress(
    mode: LiquidGlassMode,
    strength: Float
): Float {
    val normalizedStrength = normalizeLiquidGlassStrength(strength)
    val (start, end) = when (mode) {
        LiquidGlassMode.CLEAR -> 0f to 0.32f
        LiquidGlassMode.BALANCED -> 0.34f to 0.66f
        LiquidGlassMode.FROSTED -> 0.68f to 1f
    }
    return normalizeLiquidGlassProgress(start + (end - start) * normalizedStrength)
}

internal fun resolveLegacyLiquidGlassProgress(style: LiquidGlassStyle): Float {
    val mode = resolveLegacyLiquidGlassMode(style)
    return resolveLegacyLiquidGlassProgress(
        mode = mode,
        strength = resolveDefaultLiquidGlassStrength(mode)
    )
}

internal fun resolveLiquidGlassModeFromProgress(progress: Float): LiquidGlassMode {
    val normalizedProgress = normalizeLiquidGlassProgress(progress)
    return when {
        normalizedProgress < 0.34f -> LiquidGlassMode.CLEAR
        normalizedProgress < 0.68f -> LiquidGlassMode.BALANCED
        else -> LiquidGlassMode.FROSTED
    }
}

internal fun resolveLiquidGlassStrengthFromProgress(progress: Float): Float {
    val normalizedProgress = normalizeLiquidGlassProgress(progress)
    val mode = resolveLiquidGlassModeFromProgress(normalizedProgress)
    val (start, end) = when (mode) {
        LiquidGlassMode.CLEAR -> 0f to 0.32f
        LiquidGlassMode.BALANCED -> 0.34f to 0.66f
        LiquidGlassMode.FROSTED -> 0.68f to 1f
    }
    return normalizeLiquidGlassStrength(
        if (end <= start) {
            0f
        } else {
            (normalizedProgress - start) / (end - start)
        }
    )
}
