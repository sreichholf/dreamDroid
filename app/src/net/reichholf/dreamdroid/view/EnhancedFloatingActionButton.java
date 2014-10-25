package net.reichholf.dreamdroid.view;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsListView;

import com.shamanland.fab.FloatingActionButton;

import net.reichholf.dreamdroid.R;

/**
 * Created by Stephan on 25.10.2014.
 */
public class EnhancedFloatingActionButton extends FloatingActionButton implements Animation.AnimationListener {
    private FabOnScrollListener mOnScrollListener;
    protected AbsListView mListView;
    private final int mShow;
    private final int mHide;


    public EnhancedFloatingActionButton(Context context) {
        super(context);
        mShow = R.anim.floating_action_button_show;
        mHide = R.anim.floating_action_button_hide;
    }

    public EnhancedFloatingActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        mShow = R.anim.floating_action_button_show;
        mHide = R.anim.floating_action_button_hide;
    }

    public EnhancedFloatingActionButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mShow = R.anim.floating_action_button_show;
        mHide = R.anim.floating_action_button_hide;
    }

    public void show(){
        if (getVisibility() != View.VISIBLE) {
            setVisibility(View.VISIBLE);
            animate(mShow);
        }
    }

    public void hide(){
        if (getVisibility() == View.VISIBLE) {
            setVisibility(View.GONE);
            animate(mHide);
        }
    }

    private void animate(int anim) {
        if (anim != 0) {
            Animation a = AnimationUtils.loadAnimation(getContext(), anim);
            a.setAnimationListener(this);
            startAnimation(a);
        }
    }

    public void attachToListView(@NonNull AbsListView listView) {
        attachToListView(listView, new FabOnScrollListener());
    }
    public void attachToListView(@NonNull AbsListView listView, @NonNull FabOnScrollListener onScrollListener) {
        mListView = listView;
        mOnScrollListener = onScrollListener;
        onScrollListener.setFloatingActionButton(this);
        onScrollListener.setListView(listView);
        mListView.setOnScrollListener(onScrollListener);
    }

    @Override
    public void onAnimationStart(Animation animation) {

    }

    @Override
    public void onAnimationEnd(Animation animation) {

    }

    @Override
    public void onAnimationRepeat(Animation animation) {

    }

    public static class FabOnScrollListener extends ScrollDirectionDetector {
        private EnhancedFloatingActionButton mFloatingActionButton;
        public FabOnScrollListener() {
            setScrollDirectionListener(new ScrollDirectionListener() {
                @Override public void onScrollDown() {
                    mFloatingActionButton.show();
                }
                @Override public void onScrollUp() {
                    mFloatingActionButton.hide();
                }
            });
        }
        public void setFloatingActionButton(EnhancedFloatingActionButton floatingActionButton) {
            mFloatingActionButton = floatingActionButton;
        }
    }
}
