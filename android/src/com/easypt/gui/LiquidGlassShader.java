package com.easypt.gui;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

/**
 * 液态玻璃的折射（AGSL RuntimeShader）。
 *
 * 着色器移植自 Kyant0/AndroidLiquidGlass（Apache-2.0）的 RoundedRectRefractionShaderString：
 * https://github.com/Kyant0/AndroidLiquidGlass/blob/main/backdrop/src/commonMain/kotlin/com/kyant/backdrop/internal/Shaders.kt
 * 原项目是 Compose 库，这里只取它的 AGSL 片段，改为对本项目的预模糊壁纸位图做取样。
 *
 * 仅在 API 33+ 可用；不支持时调用方自动退回位图模糊。
 */
public class LiquidGlassShader {

    private static final String SRC =
            "uniform shader content;\n" +
            "uniform float2 size;\n" +
            "uniform float2 offset;\n" +
            "uniform float4 cornerRadii;\n" +
            "uniform float refractionHeight;\n" +
            "uniform float refractionAmount;\n" +
            "uniform float depthEffect;\n" +
            "\n" +
            "float radiusAt(float2 coord, float4 radii) {\n" +
            "    if (coord.x >= 0.0) {\n" +
            "        if (coord.y <= 0.0) return radii.y;\n" +
            "        else return radii.z;\n" +
            "    } else {\n" +
            "        if (coord.y <= 0.0) return radii.x;\n" +
            "        else return radii.w;\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
            "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
            "    float outside = length(max(cornerCoord, 0.0)) - radius;\n" +
            "    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n" +
            "    return outside + inside;\n" +
            "}\n" +
            "\n" +
            "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n" +
            "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n" +
            "    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n" +
            "        return sign(coord) * normalize(max(cornerCoord, 0.0));\n" +
            "    } else {\n" +
            "        float gradX = step(cornerCoord.y, cornerCoord.x);\n" +
            "        return sign(coord) * float2(gradX, 1.0 - gradX);\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "float circleMap(float x) {\n" +
            "    return 1.0 - sqrt(1.0 - x * x);\n" +
            "}\n" +
            "\n" +
            "half4 main(float2 coord) {\n" +
            "    float2 halfSize = size * 0.5;\n" +
            "    float2 centeredCoord = (coord + offset) - halfSize;\n" +
            "    float radius = radiusAt(coord, cornerRadii);\n" +
            "    float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n" +
            "    if (-sd >= refractionHeight) {\n" +
            "        return content.eval(coord);\n" +
            "    }\n" +
            "    sd = min(sd, 0.0);\n" +
            "    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n" +
            "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n" +
            "    float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius)" +
            " + depthEffect * normalize(centeredCoord));\n" +
            "    float2 refractedCoord = coord + d * grad;\n" +
            "    return content.eval(refractedCoord);\n" +
            "}\n";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RuntimeShader shader;
    private BitmapShader content;
    private Bitmap lastBitmap;
    private final Matrix matrix = new Matrix();
    private boolean broken;

    /** 实验开关：折射着色器通路。默认关，走预模糊位图；打开后若驱动不支持会自动退回。 */
    public static boolean ENABLED = false;

    public static boolean supported() {
        return ENABLED && Build.VERSION.SDK_INT >= 33;
    }

    /**
     * 准备一帧：把 level 位图按 (srcX, srcY) 对齐到控件坐标，再按 refrHeight/refrAmount 做折射。
     *
     * @return 可以直接 drawRoundRect 的 Paint；失败返回 null（调用方退回位图模糊）
     */
    public Paint paintFor(Bitmap level, float w, float h, float radius,
                          float refrHeight, float refrAmount) {
        if (broken || !supported() || level == null || w <= 0 || h <= 0) { return null; }
        try {
            if (shader == null) { shader = new RuntimeShader(SRC); }
            if (content == null || lastBitmap != level) {
                content = new BitmapShader(level, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                lastBitmap = level;
            }
            // 采样坐标由调用方的 canvas 变换负责对齐，这里保持恒等
            content.setLocalMatrix(null);
            shader.setInputBuffer("content", content);
            shader.setFloatUniform("size", w, h);
            shader.setFloatUniform("offset", 0f, 0f);
            shader.setFloatUniform("cornerRadii", radius, radius, radius, radius);
            shader.setFloatUniform("refractionHeight", Math.max(1f, refrHeight));
            shader.setFloatUniform("refractionAmount", Math.max(0f, refrAmount));
            shader.setFloatUniform("depthEffect", 0f);
            paint.setShader(shader);
            return paint;
        } catch (Throwable t) {
            broken = true;
            return null;
        }
    }
}
