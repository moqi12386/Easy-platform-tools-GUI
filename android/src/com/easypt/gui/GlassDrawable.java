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
 * 液态玻璃：实时把「控件背后那块壁纸」降采样 + 多次盒滤成模糊底，再叠一层镜面边（边缘放大取样的环），
 * 最后加顶部高光和 1px 亮边。模糊强度 0–100 可调，0 等于不模糊。
 */
public class GlassDrawable extends Drawable {

    private final View host;
    private Bitmap wall;
    private float radius;
    private int tint = 0x14FFFFFF;
    /** 全局模糊强度（0–100），设置里改了立刻生效 */
    public static int BLUR = 65;
    private int blurPct = -1;
    private float borderPx;
    private int borderColor = 0x66FFFFFF;
    private boolean lens = true;

    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private final Rect src = new Rect();
    private final Rect dst = new Rect();
    private final Path clip = new Path();
    private final Path ring = new Path();
    private final Matrix mx = new Matrix();

    public GlassDrawable(View host, float radiusPx) {
        this.host = host;
        this.radius = radiusPx;
    }

    public GlassDrawable wall(Bitmap b) { this.wall = b; return this; }

    public GlassDrawable tint(int color) { this.tint = color; return this; }

    /** 0 = 不模糊，100 = 最糊 */
    public GlassDrawable blur(int pct) { this.blurPct = Math.max(0, Math.min(100, pct)); return this; }

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

        Bitmap small = null;
        if (wall != null) { small = blurredSample(b); }

        if (small != null) {
            c.drawBitmap(small, null, box, bmpPaint);
        } else {
            fillPaint.setColor(tint & 0x00FFFFFF | 0x66000000);
            c.drawRect(box, fillPaint);
        }

        // 主题色薄薄一层，压住过亮的壁纸
        fillPaint.setShader(null);
        fillPaint.setColor(tint);
        c.drawRect(box, fillPaint);

        // 镜面边：把同一块图放大一点，只留在边缘那一圈，做出「透镜」的折射感
        if (lens && small != null) {
            float w = Math.min(dp(9), Math.min(box.width(), box.height()) * 0.26f);
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
            mx.setScale(1.18f, 1.18f, box.centerX(), box.centerY());
            c.concat(mx);
            c.drawBitmap(small, null, box, bmpPaint);
            c.restoreToCount(s2);
            // 边缘再补一点白，像玻璃的反光
            fillPaint.setColor(0x0CFFFFFF);
            c.drawPath(ring, fillPaint);
        }

        // 顶部高光（从左上打下来的光）
        fillPaint.setShader(new LinearGradient(box.left, box.top, box.left, box.top + box.height() * 0.5f,
                0x2EFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRect(box, fillPaint);
        fillPaint.setShader(null);

        // 底部一点点压暗，玻璃的厚度感
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

    /** 取控件背后那块壁纸，按 blurPct 降采样 + 盒滤 */
    private Bitmap blurredSample(Rect b) {
        int[] loc = new int[2];
        host.getLocationOnScreen(loc);
        int x = Math.max(0, loc[0]);
        int y = Math.max(0, loc[1]);
        int w = Math.min(b.width(), wall.getWidth() - x);
        int h = Math.min(b.height(), wall.getHeight() - y);
        if (w <= 0 || h <= 0) { return null; }

        int pct = blurPct >= 0 ? blurPct : BLUR;
        int factor = 1 + Math.round(pct * 0.24f);   // 1..25
        int bw = Math.max(2, w / factor);
        int bh = Math.max(2, h / factor);
        Bitmap small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(small);
        src.set(x, y, x + w, y + h);
        dst.set(0, 0, bw, bh);
        sc.drawBitmap(wall, src, dst, bmpPaint);
        if (factor > 2) { boxBlur(small, 1); }
        return small;
    }

    /** 3x3 盒滤，把降采样的锯齿抹平 */
    private static void boxBlur(Bitmap bmp, int passes) {
        int w = bmp.getWidth(), h = bmp.getHeight();
        int[] px = new int[w * h];
        bmp.getPixels(px, 0, w, 0, 0, w, h);
        int[] out = new int[w * h];
        for (int p = 0; p < passes; p++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int r = 0, g = 0, bl = 0, a = 0, n = 0;
                    for (int dy = -1; dy <= 1; dy++) {
                        int yy = y + dy;
                        if (yy < 0 || yy >= h) { continue; }
                        for (int dx = -1; dx <= 1; dx++) {
                            int xx = x + dx;
                            if (xx < 0 || xx >= w) { continue; }
                            int c = px[yy * w + xx];
                            a += (c >>> 24); r += (c >> 16) & 0xFF; g += (c >> 8) & 0xFF; bl += c & 0xFF;
                            n++;
                        }
                    }
                    out[y * w + x] = ((a / n) << 24) | ((r / n) << 16) | ((g / n) << 8) | (bl / n);
                }
            }
        }
        bmp.setPixels(out, 0, w, 0, 0, w, h);
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
