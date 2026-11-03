package com.easypt.gui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * 圆角底 + 一圈边框。
 * 边框可以是纯色（含透明度），也可以是沿按钮对角线的彩虹渐变。
 * 边框宽度按 px 传入，绘制时向内收缩，不会被父布局裁掉。
 */
public class PillDrawable extends Drawable {

    /** 彩虹边框的色标 */
    public static final int[] RAINBOW = {
            0xFFFF4D6D, 0xFFFFC53D, 0xFF3DDC97, 0xFF3AA0FF, 0xFFA66CFF, 0xFFFF4D6D
    };

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();

    private int fillColor;
    private float radius;

    private boolean borderOn;
    private boolean rainbow;
    private int borderColor = 0xFFFFFFFF;
    private float borderWidth;      // px
    private int borderAlpha = 255;  // 0..255

    private Shader shader;
    private int shaderW = -1, shaderH = -1;
    private int alpha = 255;
    private boolean pressed;

    public PillDrawable(int fillColor, float radius) {
        this.fillColor = fillColor;
        this.radius = radius;
    }

    /** 链式设置边框 */
    public PillDrawable border(boolean on, boolean rainbow, int color, float widthPx, int alphaPct) {
        this.borderOn = on;
        this.rainbow = rainbow;
        this.borderColor = color;
        this.borderWidth = widthPx;
        this.borderAlpha = Math.max(0, Math.min(100, alphaPct)) * 255 / 100;
        this.shader = null;
        invalidateSelf();
        return this;
    }

    public void setFillColor(int c) {
        if (fillColor != c) { fillColor = c; invalidateSelf(); }
    }

    @Override
    public void draw(Canvas c) {
        RectF b = box;
        b.set(getBounds());
        if (b.width() <= 0 || b.height() <= 0) { return; }

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setShader(null);
        fillPaint.setColor(fillColor);
        fillPaint.setAlpha(alpha);
        c.drawRoundRect(b, radius, radius, fillPaint);

        if (pressed) {
            fillPaint.setColor(0xFFFFFFFF);
            fillPaint.setAlpha(28 * alpha / 255);
            c.drawRoundRect(b, radius, radius, fillPaint);
        }

        if (!borderOn || borderWidth <= 0 || borderAlpha <= 0) { return; }

        if (rainbow) {
            int w = Math.round(b.width()), h = Math.round(b.height());
            if (shader == null || shaderW != w || shaderH != h) {
                shader = new LinearGradient(b.left, b.top, b.right, b.bottom,
                        RAINBOW, null, Shader.TileMode.CLAMP);
                shaderW = w;
                shaderH = h;
            }
            linePaint.setShader(shader);
            linePaint.setColor(0xFFFFFFFF);
        } else {
            linePaint.setShader(null);
            linePaint.setColor(borderColor);
        }
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(borderWidth);
        linePaint.setAlpha(borderAlpha);

        float inset = borderWidth / 2f;
        RectF in = new RectF(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset);
        float r = Math.max(0f, radius - inset);
        c.drawRoundRect(in, r, r, linePaint);
    }

    @Override
    public boolean isStateful() { return true; }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean p = false;
        for (int s : state) { if (s == android.R.attr.state_pressed) { p = true; break; } }
        if (p != pressed) { pressed = p; invalidateSelf(); }
        return true;
    }

    @Override
    public void setAlpha(int a) {
        if (alpha != a) { alpha = a; invalidateSelf(); }
    }

    @Override
    public void setColorFilter(ColorFilter cf) { }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
