package pe.edu.bosquealerta;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Dibuja cajas de seguimiento ("tracking") sobre las zonas con fuego. */
public class FireOverlayView extends View {
    public static class Box {
        public float left;
        public float top;
        public float right;
        public float bottom;
        public float score;
    }

    private static final int GRID_LINES = 4;

    private List<Box> boxes = new ArrayList<>();
    private int bmpW;
    private int bmpH;
    private float density;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thinPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public FireOverlayView(Context context) {
        super(context);
        init();
    }

    public FireOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;

        gridPaint.setColor(Color.argb(26, 255, 255, 255));
        gridPaint.setStrokeWidth(1f * density);
        gridPaint.setStyle(Paint.Style.STROKE);

        fillPaint.setColor(Color.rgb(255, 107, 0));
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAlpha(34);

        thinPaint.setColor(Color.argb(120, 255, 140, 20));
        thinPaint.setStrokeWidth(1.5f * density);
        thinPaint.setStyle(Paint.Style.STROKE);

        cornerPaint.setColor(Color.rgb(255, 150, 30));
        cornerPaint.setStrokeWidth(4f * density);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);

        labelBg.setColor(0xE6B33A00);
        labelBg.setStyle(Paint.Style.FILL);

        labelText.setColor(Color.WHITE);
        labelText.setTextSize(12f * density);
        labelText.setFakeBoldText(true);

        crossPaint.setColor(Color.argb(200, 255, 190, 60));
        crossPaint.setStrokeWidth(1.5f * density);
        crossPaint.setStyle(Paint.Style.STROKE);
    }

    public void setBoxes(List<Box> newBoxes, int width, int height) {
        this.boxes = newBoxes;
        this.bmpW = width;
        this.bmpH = height;
        invalidate();
    }

    public void clear() {
        this.boxes = new ArrayList<>();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (bmpW == 0 || bmpH == 0 || getWidth() == 0) return;

        float viewW = getWidth();
        float viewH = getHeight();
        float scale = Math.max(viewW / bmpW, viewH / bmpH);
        float dx = (viewW - bmpW * scale) / 2f;
        float dy = (viewH - bmpH * scale) / 2f;
        float frameW = bmpW * scale;
        float frameH = bmpH * scale;

        for (int i = 1; i < GRID_LINES; i++) {
            float x = dx + frameW * i / GRID_LINES;
            float y = dy + frameH * i / GRID_LINES;
            canvas.drawLine(x, dy, x, dy + frameH, gridPaint);
            canvas.drawLine(dx, y, dx + frameW, y, gridPaint);
        }

        RectF rect = new RectF();
        for (Box b : boxes) {
            float l = dx + b.left * scale;
            float t = dy + b.top * scale;
            float r = dx + b.right * scale;
            float bo = dy + b.bottom * scale;
            if (r - l < 8 * density || bo - t < 8 * density) continue;

            rect.set(l, t, r, bo);
            canvas.drawRoundRect(rect, 10 * density, 10 * density, fillPaint);
            canvas.drawRoundRect(rect, 10 * density, 10 * density, thinPaint);

            float len = Math.min(30 * density, Math.min(r - l, bo - t) * 0.32f);
            canvas.drawLine(l, t, l + len, t, cornerPaint);
            canvas.drawLine(l, t, l, t + len, cornerPaint);
            canvas.drawLine(r, t, r - len, t, cornerPaint);
            canvas.drawLine(r, t, r, t + len, cornerPaint);
            canvas.drawLine(l, bo, l + len, bo, cornerPaint);
            canvas.drawLine(l, bo, l, bo - len, cornerPaint);
            canvas.drawLine(r, bo, r - len, bo, cornerPaint);
            canvas.drawLine(r, bo, r, bo - len, cornerPaint);

            float cx = (l + r) / 2f;
            float cy = (t + bo) / 2f;
            float ch = 5 * density;
            canvas.drawLine(cx - ch, cy, cx + ch, cy, crossPaint);
            canvas.drawLine(cx, cy - ch, cx, cy + ch, crossPaint);

            String label = String.format(Locale.getDefault(), "FUEGO %.0f%%", b.score * 100);
            float textW = labelText.measureText(label);
            float padH = 8 * density;
            float boxH = labelText.getTextSize() + 10 * density;
            float bl = l;
            float bt = t - boxH - 4 * density;
            if (bt < 0) bt = t + 4 * density;
            rect.set(bl, bt, bl + textW + padH * 2, bt + boxH);
            canvas.drawRoundRect(rect, 7 * density, 7 * density, labelBg);
            canvas.drawText(label, bl + padH, bt + boxH - 6 * density, labelText);
        }
    }
}
