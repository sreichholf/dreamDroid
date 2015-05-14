package net.reichholf.dreamdroid.widget;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.widget.AbsListView;

import com.melnykov.fab.ScrollDirectionListener;

import net.reichholf.dreamdroid.R;

/**
 * Created by Stephan on 03.05.2015.
 */
public class FloatingActionButton extends com.melnykov.fab.FloatingActionButton {

	protected boolean mTopAligned;
	protected boolean mIsInverted;

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
		mIsInverted = false;
		setColorNormalResId(R.color.material_blue_400);
	}

	public void attachToListView(@NonNull AbsListView listView) {
		attachToListView(listView, null, false);
	}

	public void attachToListView(@NonNull AbsListView listView, boolean isTopAligned) {
		attachToListView(listView, null, isTopAligned);
	}

	public void attachToListView(@NonNull AbsListView listView, boolean isTopAligned, boolean isInverted) {
		attachToListView(listView, null, isTopAligned, isInverted);
	}

	public void attachToListView(@NonNull AbsListView listView, @NonNull ScrollDirectionListener scrollDirectionListener) {
		attachToListView(listView, scrollDirectionListener, false, false);
	}

	public void attachToListView(@NonNull AbsListView listView, @NonNull ScrollDirectionListener scrollDirectionListener, boolean topAligned) {
		attachToListView(listView, scrollDirectionListener, topAligned, false);
	}

	public void attachToListView(@NonNull AbsListView listView, @NonNull ScrollDirectionListener scrollDirectionListener, boolean topAligned, boolean isInverted) {
		mTopAligned = topAligned;
		mIsInverted = isInverted;
		super.attachToListView(listView, scrollDirectionListener);
	}

	public void attachToRecyclerView(@NonNull RecyclerView recyclerView) {
		attachToRecyclerView(recyclerView, null, false, false);
	}

	public void attachToRecyclerView(@NonNull RecyclerView recyclerView, boolean topAligned) {
		attachToRecyclerView(recyclerView, null, topAligned, false);
	}

	public void attachToRecyclerView(@NonNull RecyclerView recyclerView, boolean topAligned, boolean isInverted) {
		attachToRecyclerView(recyclerView, null, topAligned, isInverted);
	}

	public void attachToRecyclerView(@NonNull RecyclerView recyclerView, @NonNull ScrollDirectionListener scrollDirectionListener, boolean topAligned, boolean isInverted) {
		mTopAligned = topAligned;
		mIsInverted = isInverted;
		super.attachToRecyclerView(recyclerView, scrollDirectionListener);
	}







}
