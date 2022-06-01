/*******************************************************************************
 * Copyright 2018 Evstafiev Konstantin
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/


package com.ekndev.gaugelibrary;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;

import androidx.annotation.RequiresApi;

import java.util.List;

public class FullGauge extends AbstractGauge {

    private float sweepAngle = 360;
    private float startAngle = 270;
    private float gaugeBGWidth = 20f;
    private boolean displayValuePoint = false;
    protected boolean drawValueText = true;


    public FullGauge(Context context) {
        super(context);
        init();
    }

    public FullGauge(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public FullGauge(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public FullGauge(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        getGaugeBackGround().setStrokeWidth(gaugeBGWidth);
        getTextPaint().setTextSize(35f);
        setPadding(20f);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);


        //Draw Base Arc to display visual range
        drawBaseArc(canvas);

        //Draw Value Arc to display Value range
        drawValueArcOnCanvas(canvas);

        //drawText
        drawValueText(canvas);

        //draw value  point indicator
        drawValuePoint(canvas);

    }

    private void drawBaseArc(Canvas canvas) {

        drawBaseArc(canvas, getRectF(), startAngle, sweepAngle, getGaugeBackGround(getValue()));

    }

    protected void drawBaseArc(Canvas canvas, RectF rectF, float startAngle, float sweepAngle, Paint paint) {
        prepareCanvas(canvas);
        canvas.drawArc(rectF, startAngle, sweepAngle, false, paint);
        finishCanvas(canvas);
    }

    protected void drawValuePoint(Canvas canvas) {
        if (displayValuePoint) {
            prepareCanvas(canvas);
            //draw Value point indicator
            float rotateValue = calculateSweepAngle(getValue(), getMinValue(), getMaxValue());
            canvas.rotate(rotateValue, getRectRight() / 2f, getRectBottom() / 2f);
            canvas.drawCircle(400f / 2f, getPadding(), 8f, getRangePaintForValue(getValue(), getRanges()));
            canvas.drawLine(200f - 3f, 11f, 210f - 4f, 19f, getArrowPaint());
            canvas.drawLine(210f - 4f, 20f, 200f - 3f, 27f, getArrowPaint());
            finishCanvas(canvas);
        }
    }

    private Paint getArrowPaint() {
        Paint color = new Paint(Paint.ANTI_ALIAS_FLAG);
        color.setStrokeWidth(4f);
        color.setStyle(Paint.Style.STROKE);
        color.setColor(Color.WHITE);
        color.setStrokeCap(Paint.Cap.ROUND);
        return color;
    }

    protected void prepareCanvas(Canvas canvas) {
        canvas.save();
        canvas.translate((getWidth() / 2f) - ((getRectRight() / 2f) * getScaleRatio()), (getHeight() / 2f) - 200f * getScaleRatio());
        canvas.scale(getScaleRatio(), getScaleRatio());
    }

    protected void finishCanvas(Canvas canvas) {
        canvas.restore();
    }


    private void drawValueText(Canvas canvas) {
        if (drawValueText) {
            canvas.save();
            canvas.translate((getWidth() / 2f) - ((getRectRight() / 2f) * getScaleRatio()), (getHeight() / 2f) - 220f * getScaleRatio());
            canvas.scale(getScaleRatio(), getScaleRatio());
            canvas.drawText(getFormattedValue() + "", 200f, 240f, getTextPaint());
            canvas.restore();
        }
    }


    protected Paint getRangePaintForValue(double value, List<Range> ranges) {


        Paint color = new Paint(Paint.ANTI_ALIAS_FLAG);
        color.setStrokeWidth(gaugeBGWidth);
        color.setStyle(Paint.Style.STROKE);
        //color.setColor(getGaugeBackGround().getColor());
        color.setStrokeCap(Paint.Cap.ROUND);
        color.setColor(getRangeColorForValue(value, ranges));
        return color;
    }

    private void drawValueArcOnCanvas(Canvas canvas) {
        float sweepAngle = calculateSweepAngle(getValue(), getMinValue(), getMaxValue());
        drawValueArcOnCanvas(canvas, getRectF(), getStartAngle(), sweepAngle, getValue(), getRanges());
    }


    protected void drawValueArcOnCanvas(Canvas canvas, RectF rectF, float startAngle, float sweepAngle, double value, List<Range> ranges) {
        prepareCanvas(canvas);
        canvas.drawArc(rectF, startAngle, sweepAngle, false, getRangePaintForValue(value, ranges));
        finishCanvas(canvas);
    }

    protected float calculateSweepAngle(double to, double min, double max) {
        float valuePer = getCalculateValuePercentage(min, max, to);
        return sweepAngle / 100 * valuePer;
    }


    protected float getSweepAngle() {
        return sweepAngle;
    }

    protected void setSweepAngle(float sweepAngle) {
        this.sweepAngle = sweepAngle;
    }

    protected float getStartAngle() {
        return startAngle;
    }

    protected void setStartAngle(float startAngle) {
        this.startAngle = startAngle;
    }

    protected float getGaugeBGWidth() {
        return gaugeBGWidth;
    }

    protected void setGaugeBGWidth(float gaugeBGWidth) {
        this.gaugeBGWidth = gaugeBGWidth;
    }

    public boolean isDisplayValuePoint() {
        return displayValuePoint;
    }

    public void setDisplayValuePoint(boolean displayValuePoint) {
        this.displayValuePoint = displayValuePoint;
    }

    protected boolean isDrawValueText() {
        return drawValueText;
    }

    protected void setDrawValueText(boolean drawValueText) {
        this.drawValueText = drawValueText;
    }

}
