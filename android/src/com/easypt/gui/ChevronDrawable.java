package com.easypt.gui;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** MIUIX 列表行右侧的「›」箭头，纯代码画，不依赖资源 */
public class ChevronDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int size;

    public ChevronDrawable(int sizePx, int color, float strokePx) {
        size = sizePx;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokePx);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
    }

    @Override
    public int getIntrinsicWidth() { return size; }

    @Override
    public int getIntrinsicHeight() { return size; }

    @Override
    public void draw(Canvas c) {
        float w = getBounds().width() > 0 ? getBounds().width() : size;
        float h = getBounds().height() > 0 ? getBounds().height() : size;
        path.reset();
        path.moveTo(w * 0.32f, h * 0.22f);
        path.lineTo(w * 0.68f, h * 0.5f);
        path.lineTo(w * 0.32f, h * 0.78f);
        c.drawPath(path, paint);
    }

    @Override
    public void setAlpha(int a) { paint.setAlpha(a); }

    @Override
    public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
