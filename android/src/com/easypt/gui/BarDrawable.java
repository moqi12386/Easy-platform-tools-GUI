package com.easypt.gui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * MIUI 风格的进度条底：一条圆头粗轨道 + 同高的圆头填充。
 * 自己画，避免 LayerDrawable 里两层高度对不齐。
 */
public class BarDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final RectF fill = new RectF();
    private int fillColor;
    private int trackColor;
    private float thickness;

    public BarDrawable(int fillColor, int trackColor, float thicknessPx) {
        this.fillColor = fillColor;
        this.trackColor = trackColor;
        this.thickness = thicknessPx;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) { return; }
        float cy = (b.top + b.bottom) / 2f;
        float half = thickness / 2f;
        track.set(b.left, cy - half, b.right, cy + half);
        paint.setColor(trackColor);
        c.drawRoundRect(track, half, half, paint);

        float w = b.width() * (getLevel() / 10000f);
        if (w <= 0) { return; }
        float minW = thickness;                       // 进度很小时也画成一个圆头
        float right = track.left + Math.max(w, minW);
        if (right > track.right) { right = track.right; }
        fill.set(track.left, track.top, right, track.bottom);
        paint.setColor(fillColor);
        c.drawRoundRect(fill, half, half, paint);
    }

    @Override
    public void setAlpha(int a) { paint.setAlpha(a); }

    @Override
    public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
