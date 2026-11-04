package com.easypt.gui.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.runtimeShaderEffect

/**
 * 照抄 AndroidLiquidGlass demo 的 ProgressiveBlurContent：
 * drawPlainBackdrop + AlphaMask 运行时着色器，越靠边模糊越强。
 */
@Composable
fun ProgressiveBlurEdge(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    heightDp: Float = 140f,
    tintColor: Color = Color(0xFF808080),
    tintIntensity: Float = 0.35f
) {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    Box(
        modifier
            .height(heightDp.dp)
            .fillMaxWidth()
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = {
                    blur(4f.dp.toPx())
                    runtimeShaderEffect(
                        "AlphaMask",
                        """
    uniform shader content;

    uniform float2 size;
    layout(color) uniform half4 tint;
    uniform float tintIntensity;

    half4 main(float2 coord) {
        float blurAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
        float tintAlpha = smoothstep(size.y, size.y * 0.5, coord.y);
        return mix(content.eval(coord) * blurAlpha, tint * tintAlpha, tintIntensity);
    }""",
                        "content"
                    ) {
                        setFloatUniform("size", size.width, size.height)
                        setColorUniform("tint", tintColor)
                        setFloatUniform("tintIntensity", tintIntensity)
                    }
                }
            )
    )
}
