package com.easypt.gui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * 液态玻璃。
 *
 * 模糊不在 draw() 里算：壁纸加载时预生成 4 级「降采样 + 盒滤」的小图（1/3、1/6、1/12、1/24），
 * draw() 只按控件当前屏幕位置从对应级别里取一块画上去，成本就是一次 drawBitmap，
 * 所以滚动时每帧重画也不掉帧。
 *
 * 再叠三层液态特征：边缘的放大环（折射）、顶部高光、底部压暗。
 */
public class GlassDrawable extends Drawable {

    /** 全局模糊强度（0–100），设置里改完 invalidate 一次即可 */
    public static int BLUR = 80;
    /** 全局浓度（0–100）：面板不透明度，越高壁纸越看不清 */
    public static int CONC = 45;

    private final View host;
    private Bitmap[] levels;          // 预模糊的壁纸，从清晰到最糊
    private int[] factors = { 3, 6, 12, 24 };
    private float radius;
    private int tint = 0x14FFFFFF;
    private float borderPx;
    private int borderColor = 0x66FFFFFF;
    private boolean lens = true;

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Rect src = new Rect();
    private final Path clip = new Path();
    private final Path ring = new Path();
    private final Matrix mx = new Matrix();

    public GlassDrawable(View host) {
        this.host = host;
    }

    /** levels 是按 factor 逐级降采样+盒滤后的壁纸；factors 与之一一对应 */
    public GlassDrawable wall(Bitmap[] levelBitmaps, int[] downFactors) {
        this.levels = levelBitmaps;
        if (downFactors != null && levelBitmaps != null && downFactors.length == levelBitmaps.length) {
            this.factors = downFactors;
        }
        return this;
    }

    public GlassDrawable wall(Bitmap single) {
        this.levels = single == null ? null : new Bitmap[]{ single };
        this.factors = new int[]{ 1 };
        return this;
    }

    public GlassDrawable radius(float r) { this.radius = r; return this; }

    public GlassDrawable tint(int color) { this.tint = color; return this; }

    public GlassDrawable border(float px, int color) {
        this.borderPx = px;
        this.borderColor = color;
        return this;
    }

    public GlassDrawable lens(boolean on) { this.lens = on; return this; }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        if (b.width() <= 0 || b.height() <= 0) { return; }
        box.set(b);

        clip.reset();
        clip.addRoundRect(box, radius, radius, Path.Direction.CW);
        int save = c.save();
        c.clipPath(clip);

        int idx = levelIndex();
        Bitmap lv = (levels != null && levels.length > 0)
                ? levels[Math.min(idx, levels.length - 1)] : null;
        int factor = factors[Math.min(idx, factors.length - 1)];

        if (lv != null) {
            drawFromLevel(c, lv, factor);
        } else {
            fillPaint.setColor((tint & 0x00FFFFFF) | 0x66000000);
            c.drawRect(box, fillPaint);
        }

        // 主题色按浓度压上去
        int a = Math.max(0, Math.min(100, CONC)) * 255 / 100;
        fillPaint.setShader(null);
        fillPaint.setColor((tint & 0x00FFFFFF) | (a << 24));
        c.drawRect(box, fillPaint);

        // 边缘放大环：透镜的折射感
        if (lens && lv != null) {
            float w = Math.min(dp(13), Math.min(box.width(), box.height()) * 0.30f);
            float r2 = Math.max(1, radius - w);
            ring.reset();
            ring.addRoundRect(box, radius, radius, Path.Direction.CW);
            Path inner = new Path();
            inner.addRoundRect(new RectF(box.left + w, box.top + w, box.right - w, box.bottom - w),
                    r2, r2, Path.Direction.CW);
            ring.op(inner, Path.Op.DIFFERENCE);
            int s2 = c.save();
            c.clipPath(ring);
            mx.reset();
            mx.setScale(1.30f, 1.30f, box.centerX(), box.centerY());
            c.concat(mx);
            drawFromLevel(c, lv, factor);
            c.restoreToCount(s2);
            fillPaint.setColor(0x08FFFFFF);
            c.drawPath(ring, fillPaint);
        }

        // 顶部高光
        fillPaint.setShader(new LinearGradient(box.left, box.top, box.left, box.top + box.height() * 0.5f,
                0x1EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(box, fillPaint);
        fillPaint.setShader(null);

        // 左上斜向反光
        fillPaint.setShader(new LinearGradient(box.left, box.top, box.left + box.width() * 0.55f,
                box.top + box.height(), 0x22FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(box, fillPaint);
        fillPaint.setShader(null);

        // 底部压暗
        fillPaint.setShader(new LinearGradient(box.left, box.bottom, box.left, box.bottom - box.height() * 0.35f,
                0x14000000, 0x00000000, Shader.TileMode.CLAMP));
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
    }

    /** 0..100 映射到 3 级：最低级也有 1/6 的模糊，不会退回原图 */
    private int levelIndex() {
        int p = Math.max(0, Math.min(100, BLUR));
        if (p < 40) { return 1; }
        if (p < 70) { return 2; }
        return 3;
    }

    /** 从预模糊图里取「屏幕上这块位置」对应的部分，铺到控件里 */
    private void drawFromLevel(Canvas c, Bitmap lv, int factor) {
        int[] loc = new int[2];
        host.getLocationOnScreen(loc);
        float f = factor;
        float sx = loc[0] / f;
        float sy = loc[1] / f;
        float sw = box.width() / f;
        float sh = box.height() / f;
        int x0 = Math.max(0, Math.round(sx));
        int y0 = Math.max(0, Math.round(sy));
        int x1 = Math.min(lv.getWidth(), Math.round(sx + sw));
        int y1 = Math.min(lv.getHeight(), Math.round(sy + sh));
        if (x1 - x0 < 1 || y1 - y0 < 1) { return; }
        src.set(x0, y0, x1, y1);
        c.drawBitmap(lv, src, box, bmpPaint);
    }

    private float dp(float v) {
        return host == null ? v : v * host.getResources().getDisplayMetrics().density;
    }

    @Override
    public void setAlpha(int a) { }

    @Override
    public void setColorFilter(ColorFilter cf) { }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
