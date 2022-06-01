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
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.RequiresApi;

import com.ekndev.gaugelibrary.contract.ValueFormatter;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractGauge extends View {


	private List<Range> ranges = new ArrayList<>();
	private double value = 0;
	private double minValue = 0;
	private double maxValue = 100;
	private Paint needleColor;
	private Paint gaugeBackGround;
	private int gaugeBGColor = Color.parseColor("#EAEAEA");
	private Paint textPaint;
	private float rectTop = 0;
	private float rectLeft = 0;
	private float rectRight = 400;
	private float rectBottom = 400;
	private float padding = 0;
	private RectF rectF;
	private boolean useRangeBGColor = false;
	private ValueFormatter formatter = new ValueFormatterImpl();


	public AbstractGauge(Context context) {
		super(context);
	}

	public AbstractGauge(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public AbstractGauge(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
	public AbstractGauge(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}


	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		getScaleRatio();
	}


	protected RectF getRectF() {
		if (rectF == null)
			rectF = new RectF(rectLeft + padding, rectTop + padding, rectRight - padding, rectBottom - padding);
		return rectF;
	}

	protected Float getScaleRatio() {
		int measuredHeight = getMeasuredHeight();
		int measuredWidth = getMeasuredWidth();
		float minSize = Math.min(measuredHeight, measuredWidth) / 1f;
		float maxSize = Math.max(measuredHeight, measuredWidth) / 1f;
		float f1 = minSize / 400f;
		float f2 = minSize / 200f;
		if (measuredWidth > measuredHeight) {
			if (f2 > f1)
				return f1;
		} else {
			return minSize / 400f;

		}
		return maxSize / 400f;
	}


	public void addRange(Range range) {
		if (range == null)
			return;
		ranges.add(range);
	}


	public List<Range> getRanges() {
		return ranges;
	}

	public void setRanges(List<Range> ranges) {
		this.ranges = ranges;
	}


	protected Paint getNeedlePaint() {
		if (needleColor == null) {
			needleColor = new Paint();
			needleColor.setColor(Color.BLACK);
			needleColor.setAntiAlias(true);
			needleColor.setStyle(Paint.Style.FILL_AND_STROKE);
			needleColor.setStrokeWidth(5f);
			// needleColor.setShadowLayer(10.f,0f,5.0f,0X50000000);
			// setLayerType(LAYER_TYPE_SOFTWARE, needleColor);
		}
		return needleColor;
	}

	protected Paint getGaugeBackGround() {
		if (gaugeBackGround == null) {
			gaugeBackGround = new Paint();
			gaugeBackGround.setColor(gaugeBGColor);
			gaugeBackGround.setAntiAlias(true);
			gaugeBackGround.setStyle(Paint.Style.STROKE);
			// gaugeBackGround.setShadowLayer(15.0f,0f,5.0f,0X50000000);
			// setLayerType(LAYER_TYPE_SOFTWARE, gaugeBackGround);
		}
		return gaugeBackGround;
	}

	protected Paint getGaugeBackGround(double value) {
		if (useRangeBGColor) {
			getGaugeBackGround().setColor(getRangeColorForValue(value));
			getGaugeBackGround().setAlpha(20);
		}
		return getGaugeBackGround();
	}

	protected int getRangeColorForValue(double value) {
		return getRangeColorForValue(value, ranges);
	}

	protected int getRangeColorForValue(double value, List<Range> ranges) {
		int color = Color.GRAY;

		for (Range range : ranges) {
			if (range.getTo() <= value)
				color = range.getColor();


			if (range.getFrom() <= value && range.getTo() >= value)
				color = range.getColor();
		}
		return color;
	}

	protected int getCalculateValuePercentage() {
		int value = getCalculateValuePercentage(getValue());
		return value;

	}

	protected int getCalculateValuePercentage(double value) {
		return getCalculateValuePercentage(getMinValue(), getMaxValue(), value);
	}

	protected int getCalculateValuePercentageOld(double min, double max, double value) {
		if (min >= value)
			return 0;
		if (max <= value)
			return 100;
		return (int) ((value - min) / (max - min) * 100);
	}

	protected int getCalculateValuePercentage(double min, double max, double value) {
		if (min < 0 && max < 0 && min < max) {
			return getCalculateValuePercentageUseCaseOne(min, max, value);
		} else if (min < 0 && max < 0 && min > max) {
			return getCalculateValuePercentageUseCaseTwo(min, max, value);
		} else if ((min >= 0 && max < 0) || (min < 0 && max >= 0)) {
			if (min > max) {
				return getCalculateValuePercentageUseCaseThree(min, max, value);
			} else if (min < max) {
				return getCalculateValuePercentageUseCaseFoure(min, max, value);
			}
		}
		return getCalculateValuePercentageOld(min, max, value);
	}

	/**
	 * Use case when min and max negative
	 * and min smaller than max
	 */
	private int getCalculateValuePercentageUseCaseOne(double min, double max, double value) {
		if (value <= Math.min(min, max))
			return 0;
		if (value >= Math.max(min, max))
			return 100;
		else {
			double available = Math.abs(Math.min(min, max)) - Math.abs(Math.max(min, max));
			double minValue = Math.min(min, max);
			double result = Math.abs(((minValue - value) / (available) * 100));
			return (int) result;
		}
	}

	/**
	 * Use Case when min and max negative
	 * and min bigger than max
	 */
	private int getCalculateValuePercentageUseCaseTwo(double min, double max, double value) {
		if (value <= Math.min(min, max))
			return 100;
		if (value >= Math.max(min, max))
			return 0;
		else {
			double available = Math.abs(Math.min(min, max)) - Math.abs(Math.max(min, max));
			double maxValue = Math.max(min, max);
			double result = Math.abs(((maxValue - value) / (available) * 100));
			return (int) result;
		}
	}


	//TODO: Need improvements for calculation algorithms for next to methods

	/**
	 * Use case when one of the limits are negative and one is positive
	 * TODO: Need Improvements
	 */
	private int getCalculateValuePercentageUseCaseThree(double min, double max, double value) {
		double available = Math.abs(min) + Math.abs(max);
		if (value <= Math.min(min, max)) {
			return 100;
		} else if (value >= Math.max(min, max))
			return 0;
		else {
			double positive = Math.max(min, max);
			double result = Math.abs((positive - value) / (available) * 100);
			return (int) result;
		}
	}

	/**
	 * Use case when one of the limits are negative and one is positive
	 * and max is bigger than min
	 * TODO: Need Improvements
	 */
	private int getCalculateValuePercentageUseCaseFoure(double min, double max, double value) {
		double available = Math.abs(min) + Math.abs(max);
		if (value <= Math.min(min, max)) {
			return 0;
		} else if (value >= Math.max(min, max))
			return 100;
		else {
			double negative = Math.abs(Math.min(min, max));
			double result = Math.abs((negative + value) / (available) * 100);
			return (int) result;
		}
	}

	/**
	 * Set Value Color
	 *
	 * @param color {@link Integer}
	 */
	public void setValueColor(int color) {
		getTextPaint().setColor(color);
	}

	/**
	 * Get Current Value Color
	 *
	 * @return {@link int}
	 */
	public int getValueColor() {
		return getTextPaint().getColor();
	}

	protected Paint getTextPaint() {
		if (textPaint == null) {
			textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			textPaint.setColor(Color.BLACK);
			textPaint.setStyle(Paint.Style.FILL);
			textPaint.setTextSize(25f);
			textPaint.setTextAlign(Paint.Align.CENTER);
		}
		return textPaint;
	}

	public double getMinValue() {
		return minValue;
	}

	public void setMinValue(double minValue) {
		this.minValue = minValue;
	}

	public double getMaxValue() {
		return maxValue;
	}

	public void setMaxValue(double maxValue) {
		this.maxValue = maxValue;
	}

	public double getValue() {
		return value;
	}

	protected String getFormattedValue(double value) {
		String formatted = formatter.getFormattedValue(value);
		if (formatted == null)
			return new ValueFormatterImpl().getFormattedValue(value);
		return formatted;
	}

	protected String getFormattedValue() {
		return getFormattedValue(getValue());
	}


	public void setValue(double value) {
		this.value = value;
		invalidate();
	}

	/**
	 * Set Value Formatter
	 *
	 * @param formatter {@link ValueFormatter}
	 */
	public void setFormatter(ValueFormatter formatter) {
		this.formatter = formatter;
	}

	public void setNeedleColor(int color) {
		getNeedlePaint().setColor(color);
	}

	protected float getRectTop() {
		return rectTop;
	}

	protected void setRectTop(float rectTop) {
		this.rectTop = rectTop;
	}

	protected float getRectLeft() {
		return rectLeft;
	}

	protected void setRectLeft(float rectLeft) {
		this.rectLeft = rectLeft;
	}

	protected float getRectRight() {
		return rectRight;
	}

	protected void setRectRight(float rectRight) {
		this.rectRight = rectRight;
	}

	protected float getRectBottom() {
		return rectBottom;
	}

	protected void setRectBottom(float rectBottom) {
		this.rectBottom = rectBottom;
	}

	public float getPadding() {
		return padding;
	}

	public void setPadding(float padding) {
		this.padding = padding;
	}

	public boolean isUseRangeBGColor() {
		return useRangeBGColor;
	}

	public void setUseRangeBGColor(boolean useRangeBGColor) {
		this.useRangeBGColor = useRangeBGColor;
	}

	public void setGaugeBackGroundColor(int color) {
		this.gaugeBackGround.setColor(color);
		this.gaugeBGColor = color;
	}

	public int getGaugeBackgroundColor() {
		return this.gaugeBGColor;
	}

}
