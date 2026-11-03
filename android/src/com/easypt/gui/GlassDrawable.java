package com.easypt.gui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * 「液态玻璃」底：把壁纸在控件背后的那一块缩小 8 倍再拉伸（等效低通滤波）当作磨砂背景，
 * 叠一层薄雾、顶部高光和细白边。没有壁纸时退化成半透明纯色。
 */
public class GlassDrawable extends Drawable {

    private final View host;
    private Bitmap wall;
    private float radius;
    private int tint;
    private float borderPx;
    private int borderColor = 0x66FFFFFF;
    private int highlight = 0x26FFFFFF;

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Rect src = new Rect();
    private final Rect dst = new Rect();
    private final Path clip = new Path();

    public GlassDrawable(View host, float radiusPx, int tint) {
        this.host = host;
        this.radius = radiusPx;
        this.tint = tint;
    }

    public GlassDrawable wall(Bitmap b) {
        this.wall = b;
        return this;
    }

    public GlassDrawable border(float px, int color) {
        this.borderPx = px;
        this.borderColor = color;
        return this;
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) { return; }
        box.set(b);

        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        int save = c.save();
        c.clipPath(clip);

        fillPaint.setColor(tint);
        c.drawRect(box, fillPaint);

        if (wall != null) { drawBlurred(c, b); }

        // 薄雾
        fillPaint.setColor(0x10FFFFFF);
        c.drawRect(box, fillPaint);
        // 顶部高光
        fillPaint.setShader(new android.graphics.LinearGradient(0, b.top, 0, b.top + b.height() * 0.55f,
                0x22FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(box, fillPaint);
        fillPaint.setShader(null);

        c.restoreToCount(save);

        if (borderPx > 0) {
            linePaint.setStyle(Paint.Style.STROKE);
            linePaint.setStrokeWidth(borderPx);
            linePaint.setColor(borderColor);
            float inset = borderPx / 2f;
            RectF in = new RectF(box.left + inset, box.top + inset, box.right - inset, box.bottom - inset);
            c.drawRoundRect(in, Math.max(0, radius - inset), Math.max(0, radius - inset), linePaint);
        }

        // 顶沿 1px 亮线，玻璃的反光边
        linePaint.setShader(null);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(Math.max(1f, borderPx * 0.7f));
        linePaint.setColor(highlight);
        RectF top = new RectF(box.left + radius * 0.35f, box.top + borderPx / 2f,
                box.right - radius * 0.35f, box.top + borderPx / 2f);
        c.drawLine(top.left, top.top, top.right, top.top, linePaint);
    }

    /** 取壁纸在控件背后的那块，缩到 1/8 再铺回来 */
    private void drawBlurred(Canvas c, Rect b) {
        int[] loc = new int[2];
        host.getLocationOnScreen(loc);
        int x = Math.max(0, loc[0]);
        int y = Math.max(0, loc[1]);
        int w = Math.min(b.width(), wall.getWidth() - x);
        int h = Math.min(b.height(), wall.getHeight() - y);
        if (w <= 0 || h <= 0) { return; }

        int bw = Math.max(4, b.width() / 8);
        int bh = Math.max(4, b.height() / 8);
        Bitmap small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(small);
        src.set(x, y, x + w, y + h);
        dst.set(0, 0, bw, bh);
        sc.drawBitmap(wall, src, dst, bitmapPaint);
        c.drawBitmap(small, null, box, bitmapPaint);
        small.recycle();
    }

    @Override
    public void setAlpha(int a) { }

    @Override
    public void setColorFilter(ColorFilter cf) { }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
