package com.a41probe.monitor.ui.glass

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.a41probe.monitor.ui.theme.Ink
import com.android.purebilibili.core.ui.ModalWindowBlurBehindEffect

/**
 * v3.9：玻璃弹窗的统一可读性参数。
 *
 * 弹窗/弹层是文字密集区，不能跟着四档透明度一起变透；这里固定高不透明底色，
 * 并用窗口模糊把背后的立绘/内容虚化，保证 Ink.tx / Ink.tx2 始终清晰。
 */
const val MioDialogContainerAlpha: Float = 0.92f

/** 弹层（SHEET）经透明度四档缩放后的最低不透明度，避免极高档看不清文字。 */
const val MioSheetMinFillAlpha: Float = 0.72f

/** 弹窗背后内容实时模糊（API 31+ 生效，低版本自动 no-op）。 */
@Composable
fun MioDialogBackdropBlur() {
    ModalWindowBlurBehindEffect(enabled = currentGlassCapability().realtimeBlur, radius = 24.dp)
}
