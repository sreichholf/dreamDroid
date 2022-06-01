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
import android.os.Build;
import android.util.AttributeSet;

import androidx.annotation.RequiresApi;

public class ArcGauge extends FullGauge {

    private float sweepAngle = 240;
    private float startAngle = 150;
    private float gaugeBGWidth = 20f;

    public ArcGauge(Context context) {
        super(context);
        init();
    }

    public ArcGauge(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ArcGauge(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public ArcGauge(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        getGaugeBackGround().setStrokeWidth(gaugeBGWidth);
        getGaugeBackGround().setStrokeCap(Paint.Cap.ROUND);
        getGaugeBackGround().setColor(Color.parseColor("#D6D6D6"));
        getTextPaint().setTextSize(35f);
        setPadding(20f);
        setSweepAngle(sweepAngle);
        setStartAngle(startAngle);
    }

    protected void drawValuePoint(Canvas canvas) {
       //no point
    }
}
