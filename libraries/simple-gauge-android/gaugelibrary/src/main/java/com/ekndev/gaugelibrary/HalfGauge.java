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
import android.os.Handler;
import android.util.AttributeSet;
import android.util.TypedValue;

import androidx.annotation.RequiresApi;

public class HalfGauge extends AbstractGauge {

	private float needleStart = 30;
	private float needleEnd = 150;
	private float currentAngle = 30;
	private float startAngle = 210;
	private float sweepAngle = 120;
	private Integer needleAngleNext;
	private Handler handler = new Handler();
	private boolean enableBackGroundShadow = true;
	private boolean enableNeedleShadow = true;
	private boolean enableAnimation = true;
	private int minValueTextColor = Color.GRAY;
	private int maxValueTextColor = Color.GRAY;


	private Runnable runnable = new Runnable() {
		@Override
		public void run() {
			invalidate();
		}
	};

	public HalfGauge(Context context) {
		super(context);
		init();
	}

	public HalfGauge(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public HalfGauge(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		init();
	}

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	public HalfGauge(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
		init();
	}

	private void init() {

		getGaugeBackGround().setStrokeWidth(100f);
		//add BG Shadow
		//drawShadow();

		setPadding(20f);


	}

	private void drawShadow() {
		if (enableBackGroundShadow) {
			getGaugeBackGround().setShadowLayer(15.0f, 0f, 5.0f, 0X50000000);
			setLayerType(LAYER_TYPE_HARDWARE, getGaugeBackGround());

		}
		if (enableNeedleShadow) {
			//add Needle Shadow
			getNeedlePaint().setShadowLayer(10.f, 0f, 5.0f, 0X50000000);
			setLayerType(LAYER_TYPE_HARDWARE, getNeedlePaint());
		}

	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		if (canvas == null)
			return;

		//Add shadow
		drawShadow();

		canvas.save();
		canvas.translate((getWidth() / 2f) - ((getRectRight() / 2f) * getScaleRatio()), getHeight() / 2f - 50f * getScaleRatio());
		canvas.scale(getScaleRatio(), getScaleRatio());
		canvas.drawArc(getRectF(), startAngle, sweepAngle, false, getGaugeBackGround());
		drawRange(canvas);
		canvas.restore();

		canvas.save();
		canvas.translate((getWidth() / 2f) - ((getRectRight() / 2f) * getScaleRatio()), getHeight() / 2f - 50f * getScaleRatio());
		canvas.scale(getScaleRatio(), getScaleRatio());
		canvas.rotate(getNeedleAngle(), getRectRight() / 2f, getRectBottom() / 2f);
		canvas.drawLine(-30f, 400f / 2f, 400f / 2f, 400f / 2f, getNeedlePaint());
		canvas.drawOval(new RectF(190f, 190f, 210f, 210f), getNeedlePaint());
		canvas.restore();


		canvas.save();
		canvas.translate((getWidth() / 2f) - ((getRectRight() / 2f) * getScaleRatio()), getHeight() / 2f - 50f * getScaleRatio());
		canvas.scale(getScaleRatio(), getScaleRatio());
		canvas.drawText(getFormattedValue() + "", 200f, 240f, getTextPaint());
		canvas.restore();


		//draw Text Value
		drawValueText(canvas);
		//drawMinValue
		drawMinValue(canvas);
		//drawMaxValue
		drawMaxValue(canvas);
	}

	private void drawValueText(Canvas canvas) {
		canvas.save();
		canvas.translate((getWidth() / 2f) - ((getRectRight() / 2f) * getScaleRatio()), getHeight() / 2f - 50f * getScaleRatio());
		canvas.scale(getScaleRatio(), getScaleRatio());
		canvas.drawText(getFormattedValue() + "", 200f, 240f, getTextPaint());
		canvas.restore();
	}

	private void drawMinValue(Canvas canvas) {
		canvas.save();
		canvas.translate((getWidth() / 2f) - ((getRectRight() / 2f) * getScaleRatio()), getHeight() / 2f - 50f * getScaleRatio());
		canvas.scale(getScaleRatio(), getScaleRatio());
		canvas.rotate(26, 10f, 130f);
		canvas.drawText(getFormattedValue(getMinValue()) + "", 10f + getPadding(), 130f, getRangeValue(getMinValueTextColor()));
		canvas.restore();
	}

	private void drawMaxValue(Canvas canvas) {
		canvas.save();
		canvas.translate((getWidth() / 2f) - ((getRectRight() / 2f) * getScaleRatio()), getHeight() / 2f - 50f * getScaleRatio());
		canvas.scale(getScaleRatio(), getScaleRatio());
		canvas.rotate(-26, 390f, 130f);
		canvas.drawText(getFormattedValue(getMaxValue()) + "", 390f - getPadding(), 130f, getRangeValue(getMaxValueTextColor()));
		canvas.restore();
	}

	private void drawRange(Canvas canvas) {
		for (Range range : getRanges()) {
			float startAngle = calculateStartAngle(range.getFrom());
			float sweepAngle = calculateSweepAngle(range.getFrom(), range.getTo());
			canvas.drawArc(getRectF(), startAngle, sweepAngle, false, getRangePaint(range.getColor()));
		}
	}

	private float calculateStartAngle(double from) {
		return sweepAngle / 100 * getCalculateValuePercentage(from) + startAngle;
	}

	private float calculateSweepAngle(double from, double to) {
		return sweepAngle / 100 * getCalculateValuePercentage(to) - sweepAngle / 100 * getCalculateValuePercentage(from) + 0.5f;

	}

	public int getNeedleAngle() {
		if (needleAngleNext != null && enableAnimation) {
			if (needleAngleNext != currentAngle) {
				if (needleAngleNext < currentAngle)
					currentAngle--;
				else
					currentAngle++;
				handler.postDelayed(runnable, 5);
			}
		} else {
			currentAngle = (needleEnd - needleStart) / 100 * getCalculateValuePercentage() + needleStart;
		}

		return (int) currentAngle;
	}

	@Override
	public void setValue(double value) {
		super.setValue(value);
		needleAngleNext = (int) ((needleEnd - needleStart) / 100 * getCalculateValuePercentage() + needleStart);
	}


	protected Paint getRangePaint(int color) {
		Paint range = new Paint();
		range.setColor(color);
		range.setAntiAlias(true);
		range.setStyle(Paint.Style.STROKE);
		range.setStrokeWidth(getGaugeBackGround().getStrokeWidth());
		return range;
	}

	protected Paint getRangeValue(int color) {
		Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		textPaint.setColor(color);
		textPaint.setStyle(Paint.Style.FILL);
		textPaint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9, getResources().getDisplayMetrics()));
		textPaint.setTextAlign(Paint.Align.CENTER);
		return textPaint;
	}

	public boolean isEnableBackGroundShadow() {
		return enableBackGroundShadow;
	}

	public void setEnableBackGroundShadow(boolean enableBackGroundShadow) {
		this.enableBackGroundShadow = enableBackGroundShadow;
	}

	public boolean isEnableNeedleShadow() {
		return enableNeedleShadow;
	}

	public void setEnableNeedleShadow(boolean enableNeedleShadow) {
		this.enableNeedleShadow = enableNeedleShadow;
	}

	/**
	 * Enable or disable animation for needle
	 * true will enable animation
	 * false will disable animation
	 *
	 * @param enableAnimation [boolean]
	 */
	public void enableAnimation(boolean enableAnimation) {
		this.enableAnimation = enableAnimation;
	}

	/**
	 * Check if animation enable or disable for needle
	 *
	 * @return boolean value
	 */
	public boolean isEnableAnimation() {
		return enableAnimation;
	}


	/**
	 * Set Min value text color
	 *
	 * @param minValueTextColor {@link int}
	 */
	public void setMinValueTextColor(int minValueTextColor) {
		this.minValueTextColor = minValueTextColor;
	}

	/**
	 * Set Max value text color
	 *
	 * @param maxValueTextColor {@link int}
	 */
	public void setMaxValueTextColor(int maxValueTextColor) {
		this.maxValueTextColor = maxValueTextColor;
	}

	/**
	 * Get current min value color
	 *
	 * @return {@link int}
	 */
	public int getMinValueTextColor() {
		return minValueTextColor;
	}

	/**
	 * Get current max value color
	 *
	 * @return {@link int}
	 */
	public int getMaxValueTextColor() {
		return maxValueTextColor;
	}
}
