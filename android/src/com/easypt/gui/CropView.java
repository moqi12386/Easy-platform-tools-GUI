package com.easypt.gui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/**
 * 固定框裁剪：图片可拖动、双指缩放，取框内的部分作为壁纸。
 * 框 = 控件本身大小。
 */
public class CropView extends View {

    private Bitmap bitmap;
    private final Matrix matrix = new Matrix();
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private float lastX, lastY;
    private float lastDist;
    private boolean ready;

    public void setBitmap(Bitmap b) {
        bitmap = b;
        ready = false;
        requestLayout();
        invalidate();
    }

    public CropView(Context c) {
        super(c);
        setBackgroundColor(0xFF000000);
    }

    private void fitOnce() {
        if (ready || bitmap == null || getWidth() == 0) { return; }
        float sx = (float) getWidth() / bitmap.getWidth();
        float sy = (float) getHeight() / bitmap.getHeight();
        float s = Math.max(sx, sy);
        matrix.reset();
        matrix.postScale(s, s);
        matrix.postTranslate((getWidth() - bitmap.getWidth() * s) / 2f, (getHeight() - bitmap.getHeight() * s) / 2f);
        ready = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        fitOnce();
        if (bitmap == null) { return; }
        canvas.drawBitmap(bitmap, matrix, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (bitmap == null) { return false; }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = e.getX();
                lastY = e.getY();
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (e.getPointerCount() >= 2) { lastDist = dist(e); }
                break;
            case MotionEvent.ACTION_MOVE:
                if (e.getPointerCount() >= 2) {
                    float d = dist(e);
                    if (lastDist > 0) {
                        float k = d / lastDist;
                        float cx = (e.getX(0) + e.getX(1)) / 2f;
                        float cy = (e.getY(0) + e.getY(1)) / 2f;
                        matrix.postScale(k, k, cx, cy);
                        lastDist = d;
                    }
                } else {
                    matrix.postTranslate(e.getX() - lastX, e.getY() - lastY);
                    lastX = e.getX();
                    lastY = e.getY();
                }
                clamp();
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                lastDist = 0;
                break;
        }
        return true;
    }

    private float dist(MotionEvent e) {
        float dx = e.getX(0) - e.getX(1);
        float dy = e.getY(0) - e.getY(1);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    /** 保证图片不会移出框外露黑边 */
    private void clamp() {
        float[] v = new float[9];
        matrix.getValues(v);
        float scale = v[Matrix.MSCALE_X];
        float tx = v[Matrix.MTRANS_X];
        float ty = v[Matrix.MTRANS_Y];
        float w = bitmap.getWidth() * scale;
        float h = bitmap.getHeight() * scale;

        if (scale < Math.max((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight())) {
            float min = Math.max((float) getWidth() / bitmap.getWidth(), (float) getHeight() / bitmap.getHeight());
            float k = min / scale;
            matrix.postScale(k, k, getWidth() / 2f, getHeight() / 2f);
            matrix.getValues(v);
            scale = v[Matrix.MSCALE_X];
            tx = v[Matrix.MTRANS_X];
            ty = v[Matrix.MTRANS_Y];
            w = bitmap.getWidth() * scale;
            h = bitmap.getHeight() * scale;
        }

        float dx = 0, dy = 0;
        if (w <= getWidth()) { dx = (getWidth() - w) / 2f - tx; }
        else if (tx > 0) { dx = -tx; }
        else if (tx + w < getWidth()) { dx = getWidth() - (tx + w); }

        if (h <= getHeight()) { dy = (getHeight() - h) / 2f - ty; }
        else if (ty > 0) { dy = -ty; }
        else if (ty + h < getHeight()) { dy = getHeight() - (ty + h); }

        if (dx != 0 || dy != 0) { matrix.postTranslate(dx, dy); }
    }

    /** 按当前框裁出位图 */
    public Bitmap crop() {
        if (bitmap == null) { return null; }
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        fitOnce();
        c.drawBitmap(bitmap, matrix, paint);
        return out;
    }
}
