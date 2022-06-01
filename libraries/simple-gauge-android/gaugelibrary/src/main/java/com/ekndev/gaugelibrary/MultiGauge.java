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

import java.util.ArrayList;
import java.util.List;

public class MultiGauge extends FullGauge {

    private float distance = 30f;
    private float gaugeBGWidth = 20f;
    private double secondValue = 0;
    private double thirdValue = 0;
    private double secondMinValue = 0;
    private double thirdMinValue = 0;
    private double secondMaxValue = 100;
    private double thirdMaxValue = 100;

    private List<Range> secondRanges = new ArrayList<>();
    private List<Range> thirdRanges = new ArrayList<>();


    private RectF getSecondRect() {
        return new RectF(getRectLeft() + getPadding() + distance, getRectTop() + getPadding() + distance, getRectRight() - getPadding() - distance, getRectBottom() - getPadding() - distance);
    }

    private RectF getThirdRect() {
        return new RectF(getRectLeft() + getPadding() + distance * 2, getRectTop() + getPadding() + distance * 2, getRectRight() - getPadding() - distance * 2, getRectBottom() - getPadding() - distance * 2);
    }

    public MultiGauge(Context context) {
        super(context);
        init();
    }

    public MultiGauge(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MultiGauge(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public MultiGauge(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public void init() {
        getGaugeBackGround().setStrokeWidth(gaugeBGWidth);
        getGaugeBackGround().setColor(Color.parseColor("#EAEAEA"));
        getTextPaint().setTextSize(35f);
        setPadding(20f);
        setDrawValueText(false);
    }


    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //Draw Base Arc's
        drawBaseArc(canvas, getSecondRect(), getStartAngle(), getSweepAngle(), getGaugeBackGround(getSecondValue()));
        drawBaseArc(canvas, getThirdRect(), getStartAngle(), getSweepAngle(), getGaugeBackGround(getThirdValue()));

        //Draw Value Arc's
        drawValueArcOnCanvas(canvas, getSecondRect(), getStartAngle(),
                calculateSweepAngle(getSecondValue(), getSecondMinValue(), getSecondMaxValue()),
                getSecondValue(), getSecondRanges());
        
        drawValueArcOnCanvas(canvas, getThirdRect(), getStartAngle(),
                calculateSweepAngle(getThirdValue(), getThirdMinValue(), getThirdMaxValue()),
                getThirdValue(), getThirdRanges());


    }

    @Override
    protected void drawValuePoint(Canvas canvas) {
        //TODO : draw value point
    }

    private Paint getRangePaint(double value, List<Range> ranges) {
        Paint color = new Paint(Paint.ANTI_ALIAS_FLAG);
        color.setStrokeWidth(gaugeBGWidth);
        color.setStyle(Paint.Style.STROKE);
        color.setColor(getGaugeBackGround().getColor());
        color.setStrokeCap(Paint.Cap.ROUND);

        for (Range range : ranges) {
            if (range.getTo() <= value)
                color.setColor(range.getColor());

            if (range.getFrom() <= value && range.getTo() >= value)
                color.setColor(range.getColor());
        }
        return color;
    }


    public double getSecondValue() {
        return secondValue;
    }

    public void setSecondValue(double secondValue) {
        this.secondValue = secondValue;
        invalidate();
    }

    public double getThirdValue() {
        return thirdValue;
    }

    public void setThirdValue(double thirdValue) {
        this.thirdValue = thirdValue;
        invalidate();
    }

    public double getSecondMinValue() {
        return secondMinValue;
    }

    public void setSecondMinValue(double secondMinValue) {
        this.secondMinValue = secondMinValue;
    }

    public double getThirdMinValue() {
        return thirdMinValue;
    }

    public void setThirdMinValue(double thirdMinValue) {
        this.thirdMinValue = thirdMinValue;
    }

    public double getSecondMaxValue() {
        return secondMaxValue;
    }

    public void setSecondMaxValue(double secondMaxValue) {
        this.secondMaxValue = secondMaxValue;
    }

    public double getThirdMaxValue() {
        return thirdMaxValue;
    }

    public void setThirdMaxValue(double thirdMaxValue) {
        this.thirdMaxValue = thirdMaxValue;
    }

    public void addSecondRange(Range range) {
        this.secondRanges.add(range);
    }

    public void addThirdRange(Range range) {
        this.thirdRanges.add(range);
    }

    public List<Range> getSecondRanges() {
        return secondRanges;
    }

    public void setSeconRanges(List<Range> secondRanges) {
        this.secondRanges = secondRanges;
    }

    public List<Range> getThirdRanges() {
        return thirdRanges;
    }

    public void setThirdRanges(List<Range> thirdRanges) {
        this.thirdRanges = thirdRanges;
    }
}
