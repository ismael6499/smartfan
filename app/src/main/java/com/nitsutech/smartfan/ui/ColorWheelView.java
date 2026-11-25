package com.nitsutech.smartfan.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;

/**
 * Circular HSV hue ring/wheel matching IntelliBeacon's RGB color picker.
 */
public class ColorWheelView extends View {

    public interface OnColorSelectedListener {
        void onColorChanged(String hexRgb, boolean isFinal);
    }

    private final Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indicatorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float hueDegrees = 0f;
    private float radius = 0f;
    private float cx = 0f;
    private float cy = 0f;
    private OnColorSelectedListener listener;

    public ColorWheelView(Context context) {
        super(context);
        init();
    }

    public ColorWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ColorWheelView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        wheelPaint.setStyle(Paint.Style.STROKE);
        indicatorPaint.setStyle(Paint.Style.STROKE);
        indicatorPaint.setStrokeWidth(6f);
        indicatorPaint.setColor(Color.WHITE);
        centerPaint.setStyle(Paint.Style.FILL);
        centerPaint.setColor(Color.RED);
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        this.listener = listener;
    }

    public void setHexColor(String hex6) {
        try {
            String clean = hex6.replace("#", "").trim();
            int color = Color.parseColor("#" + clean);
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            hueDegrees = hsv[0];
            centerPaint.setColor(color);
            invalidate();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cx = w / 2f;
        cy = h / 2f;
        float minDim = Math.min(w, h);
        float stroke = minDim * 0.17f;
        wheelPaint.setStrokeWidth(stroke);
        radius = (minDim / 2f) - stroke * 0.65f;

        int[] colors = new int[] {
                0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
                0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
        };
        wheelPaint.setShader(new SweepGradient(cx, cy, colors, null));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (radius <= 0f) {
            return;
        }
        canvas.drawCircle(cx, cy, radius, wheelPaint);
        canvas.drawCircle(cx, cy, radius * 0.42f, centerPaint);

        double rad = Math.toRadians(hueDegrees);
        float ix = cx + (float) (radius * Math.cos(rad));
        float iy = cy + (float) (radius * Math.sin(rad));
        canvas.drawCircle(ix, iy, 16f, indicatorPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN
                || action == MotionEvent.ACTION_MOVE
                || action == MotionEvent.ACTION_UP) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(action != MotionEvent.ACTION_UP);
            }
            double angle = Math.toDegrees(Math.atan2(event.getY() - cy, event.getX() - cx));
            if (angle < 0) {
                angle += 360.0;
            }
            hueDegrees = (float) angle;
            int color = Color.HSVToColor(new float[] { hueDegrees, 1f, 1f });
            centerPaint.setColor(color);
            invalidate();

            String hex = String.format(Locale.US, "%06x", color & 0xFFFFFF);
            if (listener != null) {
                listener.onColorChanged(hex, action == MotionEvent.ACTION_UP);
            }
            return true;
        }
        return super.onTouchEvent(event);
    }
}
