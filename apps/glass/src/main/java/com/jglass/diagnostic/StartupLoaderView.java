/*
 * Adapted from Kartik Gupta's public CodePen "J.A.R.V.I.S loader screen",
 * https://codepen.io/Kartik-Gupta-the-typescripter/pen/PorbjBV (MIT License).
 * Copyright (c) 2023 Kartik Gupta.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.jglass.diagnostic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;

final class StartupLoaderView extends View {
    private static final int CYAN = Color.rgb(0, 255, 255);
    private static final int YELLOW = Color.rgb(255, 255, 0);
    private static final float[] OUTER_ARCS = {101, 38, 151, 25, 198, 54, 270, 31, 311, 13, 340, 40};
    private static final float[] MIDDLE_ARCS = {239, 143, 72, 72};
    private static final float[] INNER_ARCS = {214, 112, 34, 112};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private boolean running;
    private long startedAt;

    StartupLoaderView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);
    }

    void start() {
        if (running) return;
        running = true;
        startedAt = SystemClock.uptimeMillis();
        postInvalidateOnAnimation();
    }

    void stop() {
        running = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight()) * 0.82f;
        float scale = size / 200f;
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 2f;
        long elapsed = Math.max(0L, SystemClock.uptimeMillis() - startedAt);

        paint.setColor(CYAN);
        paint.setStrokeWidth(2f * scale);
        canvas.drawCircle(centerX, centerY, 100f * scale, paint);
        canvas.drawCircle(centerX, centerY, 25.322f * scale, paint);

        drawSegmentedRing(canvas, centerX, centerY, 82f * scale, 4f * scale,
                alternatingDegrees(elapsed, 7000L, 1), CYAN,
                OUTER_ARCS);
        drawArc(canvas, centerX, centerY, 69f * scale, 315f,
                alternatingDegrees(elapsed, 3000L, -1), YELLOW, 1.4f * scale);
        drawSegmentedRing(canvas, centerX, centerY, 55f * scale, 5f * scale,
                alternatingDegrees(elapsed, 4000L, -1), CYAN,
                MIDDLE_ARCS);
        drawSegmentedRing(canvas, centerX, centerY, 43f * scale, 1.2f * scale,
                alternatingDegrees(elapsed, 4000L, 1), CYAN,
                INNER_ARCS);
        drawArc(canvas, centerX, centerY, 16f * scale, 318f,
                alternatingDegrees(elapsed, 2000L, 1), YELLOW, 2f * scale);
        paint.setColor(CYAN);
        paint.setStrokeWidth(Math.max(1f, 0.8f * scale));
        canvas.drawCircle(centerX, centerY, 5.55f * scale, paint);

        if (running) postInvalidateOnAnimation();
    }

    private void drawSegmentedRing(Canvas canvas, float centerX, float centerY,
            float radius, float width, float rotation, int color, float[] arcs) {
        paint.setColor(color);
        paint.setStrokeWidth(width);
        bounds.set(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius);
        canvas.save();
        canvas.rotate(rotation, centerX, centerY);
        for (int index = 0; index < arcs.length; index += 2) {
            canvas.drawArc(bounds, arcs[index], arcs[index + 1], false, paint);
        }
        canvas.restore();
    }

    private void drawArc(Canvas canvas, float centerX, float centerY, float radius,
            float sweep, float rotation, int color, float width) {
        paint.setColor(color);
        paint.setStrokeWidth(width);
        bounds.set(centerX - radius, centerY - radius,
                centerX + radius, centerY + radius);
        canvas.save();
        canvas.rotate(rotation, centerX, centerY);
        canvas.drawArc(bounds, -45f, sweep, false, paint);
        canvas.restore();
    }

    private static float alternatingDegrees(long elapsed, long duration, int direction) {
        long position = elapsed % (duration * 2L);
        float progress = position <= duration
                ? position / (float) duration
                : (duration * 2L - position) / (float) duration;
        float eased = 0.5f - 0.5f * (float) Math.cos(Math.PI * progress);
        return direction * 360f * eased;
    }
}
