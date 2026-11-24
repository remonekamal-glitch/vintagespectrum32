package com.vintage.spectrum32;

import android.content.Context;
import android.graphics.*;
import android.view.*;

public class SpectrumView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] levels = new float[32];
    private int mode = 0, brightness = 85;
    private float density;
    private float downX, downY;
    private ScaleGestureDetector scaleDetector;
    private float scale = 1f;
    public interface WindowGestureListener {
        void onDrag(float dx, float dy);
        void onResize(float scaleFactor);
    }
    private WindowGestureListener windowGestureListener;
    public void setWindowGestureListener(WindowGestureListener listener) { windowGestureListener = listener; }

    public SpectrumView(Context c) {
        super(c); density = getResources().getDisplayMetrics().density;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        scaleDetector = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float factor = d.getScaleFactor();
                scale = Math.max(0.65f, Math.min(2.0f, scale * factor));
                if (windowGestureListener != null) windowGestureListener.onResize(factor);
                return true;
            }
        });
    }
    public void setMode(int m) { mode = Math.max(0, Math.min(4,m)); invalidate(); }
    public void setBrightness(int b) { brightness = b; invalidate(); }
    public void setLevels(float[] values) {
        synchronized (levels) { System.arraycopy(values, 0, levels, 0, Math.min(32, values.length)); }
        postInvalidate();
    }
    public float getScaleFactor() { return scale; }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        float pad = 5*density, gap = 2*density;
        float barW = Math.max(1, (w - 2*pad - 31*gap)/32f);
        float baseline = (mode == 1 || mode == 2) ? h*0.50f : h*0.90f;
        float maxH = (mode == 1 || mode == 2) ? h*0.43f : h*0.82f;
        for (int i=0;i<32;i++) {
            float v; synchronized (levels) { v = levels[i]; }
            float bh = Math.max(2*density, Math.min(maxH, v*maxH));
            float x = pad + i*(barW+gap);
            int col = colorFor(i, v);
            drawColumn(c, x, baseline, barW, bh, col, false);
            if (mode == 1 || mode == 2) {
                int reflect = Color.argb(90, Color.red(col), Color.green(col), Color.blue(col));
                drawColumn(c, x, baseline + 2*density, barW, bh, reflect, true);
            }
        }
        if (mode == 3) {
            p.setColor(Color.argb(100, 255,255,255)); p.setStrokeWidth(1.2f*density);
            c.drawLine(w/2, h*.08f, w/2, h*.91f, p);
        }
    }

    private void drawColumn(Canvas c, float x, float baseline, float bw, float height, int color, boolean down) {
        int segments = Math.max(1, (int)(height/(5*density)));
        float segH = 3.4f*density, step = 5*density;
        for (int s=0;s<segments;s++) {
            float y = down ? baseline+s*step : baseline-(s+1)*step;
            if (y < 0 || y > getHeight()) continue;
            int led = color;
            if (mode == 0) {
                float t = (float)s/Math.max(1,segments);
                led = t > .72f ? Color.RED : (t > .48f ? 0xFFFFC928 : 0xFF35E65B);
            } else if (mode == 1) led = rainbow((float)s/Math.max(1,segments));
            else if (mode == 2) led = (s%2==0 ? 0xFFFF3030 : 0xFF168BFF);
            else if (mode == 3) led = (s%5==0 ? 0xFFFFB52E : 0xFF4BDB83);
            else led = (tintFire((float)s/Math.max(1,segments)));
            int alpha = Math.max(35, Math.min(255, brightness*255/100));
            p.setColor((led & 0x00FFFFFF) | (alpha<<24));
            p.setShadowLayer(5*density,0,0,led);
            c.drawRoundRect(x, y, x+bw, y+segH, 1.5f*density, 1.5f*density, p);
            p.clearShadowLayer();
        }
    }
    private int colorFor(int i, float v) {
        if (mode==2) return (i%2==0)?0xFFFF3030:0xFF168BFF;
        if (mode==1) return rainbow(i/31f);
        return 0xFF42E879;
    }
    private int rainbow(float t) {
        return Color.HSVToColor(new float[]{(1f-t)*300f, 1f, 1f});
    }
    private int tintFire(float t) {
        return Color.rgb(255, (int)(55+190*t), (int)(8+20*t));
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        scaleDetector.onTouchEvent(e);
        if (e.getPointerCount()>1) return true;
        if (e.getAction()==MotionEvent.ACTION_DOWN) {
            downX=e.getRawX(); downY=e.getRawY(); return true;
        }
        if (e.getAction()==MotionEvent.ACTION_MOVE) {
            float dx=e.getRawX()-downX, dy=e.getRawY()-downY;
            if (windowGestureListener != null) windowGestureListener.onDrag(dx,dy);
            downX=e.getRawX(); downY=e.getRawY(); return true;
        }
        return true;
    }
}
