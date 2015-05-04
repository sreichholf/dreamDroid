package net.reichholf.dreamdroid.widget;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.AbsListView;

import com.melnykov.fab.ScrollDirectionListener;
import com.nineoldandroids.animation.ObjectAnimator;

import net.reichholf.dreamdroid.R;

import java.lang.reflect.Field;

/**
 * Created by Stephan on 03.05.2015.
 */
public class FloatingActionButton extends com.melnykov.fab.FloatingActionButton {

	private static final int TRANSLATE_DURATION_MILLIS = 200;

	protected boolean mTopAligned;
	protected boolean mVisible;

	private final Interpolator mInterpolator = new AccelerateDecelerateInterpolator();

	public FloatingActionButton(Context context) {
		super(context);
		init();
	}

	public FloatingActionButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public FloatingActionButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init();
	}

	private void init(){
		mTopAligned = false;
		setColorNormalResId(R.color.material_blue_400);
		show();
	}

	public void attachToListView(@NonNull AbsListView listView) {
		attachToListView(listView, null, false);
	}

	public void attachToListView(@NonNull AbsListView listView, boolean isTopAligned) {
		attachToListView(listView, null, isTopAligned);
	}

	public void attachToListView(@NonNull AbsListView listView, @NonNull ScrollDirectionListener scrollDirectionListener) {
		attachToListView(listView, scrollDirectionListener, false);
	}
	public void attachToListView(@NonNull AbsListView listView, @NonNull ScrollDirectionListener scrollDirectionListener, boolean topAligned) {
		mTopAligned = topAligned;
		super.attachToListView(listView, scrollDirectionListener);
	}

	public void attachToRecyclerView(@NonNull RecyclerView recyclerView) {
		attachToRecyclerView(recyclerView, null, false);
	}

	public void attachToRecyclerView(@NonNull RecyclerView recyclerView, boolean topAligned) {
		attachToRecyclerView(recyclerView, null, topAligned);
	}

	public void attachToRecyclerView(@NonNull RecyclerView recyclerView, @NonNull ScrollDirectionListener scrollDirectionListener, boolean topAligned) {
		mTopAligned = topAligned;
		super.attachToRecyclerView(recyclerView, scrollDirectionListener);
	}

	private void applyVisibleFromSuper(){
		try {
			Field visible = getClass().getSuperclass().getField("mVisible");
			visible.setAccessible(true);
			visible.getBoolean(mVisible);
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void show(boolean animate) {
		toggle(true && !mTopAligned, animate, false);
	}

	@Override
	public void hide(boolean animate) {
		toggle(false || mTopAligned, animate, false);
	}

	protected void toggle(final boolean visible, final boolean animate, boolean force) {
		applyVisibleFromSuper();
		if (mVisible != visible || force) {
			mVisible = visible;
			int height = getHeight();
			if (height == 0 && !force) {
				ViewTreeObserver vto = getViewTreeObserver();
				if (vto.isAlive()) {
					vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
						@Override
						public boolean onPreDraw() {
							ViewTreeObserver currentVto = getViewTreeObserver();
							if (currentVto.isAlive()) {
								currentVto.removeOnPreDrawListener(this);
							}
							toggle(visible, animate, true);
							return true;
						}
					});
					return;
				}
			}

			int translationY = visible ? 0 : height + getMarginBottom();
			int from = visible ? height : 0;
			if (mTopAligned) {
				translationY = visible ? 0 : -(2 * height + getMarginTop());
				from = visible ? -(2 * height) : 0;
			}

			ObjectAnimator animator = ObjectAnimator.ofFloat(this,
					"translationY", from, translationY);
			animator.setInterpolator(mInterpolator);
			if (animate) {
				animator.setDuration(TRANSLATE_DURATION_MILLIS).start();
			} else {
				animator.setDuration(0).start();
			}
		}
	}

	private int getMarginTop() {
		int marginTop = 0;
		final ViewGroup.LayoutParams layoutParams = getLayoutParams();
		if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
			marginTop = ((ViewGroup.MarginLayoutParams) layoutParams).topMargin;
		}
		return marginTop;
	}

	private int getMarginBottom() {
		int marginBottom = 0;
		final ViewGroup.LayoutParams layoutParams = getLayoutParams();
		if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
			marginBottom = ((ViewGroup.MarginLayoutParams) layoutParams).bottomMargin;
		}
		return marginBottom;
	}

}
