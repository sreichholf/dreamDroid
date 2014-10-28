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
    protected AbsListView mListView;
    private boolean mStickWhenScrolled;
    private int mShow;
    private int mHide;


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

    public void setAnimations(int show, int hide){
        mShow = show;
        mHide = hide;
    }

    private void invertAnimations(){
        mShow = R.anim.slide_in_top;
        mHide = R.anim.slide_out_top;
    }

    public void show(){
        if (getVisibility() != View.VISIBLE) {
            setVisibility(View.VISIBLE);
            animate(mShow);
        }
    }

    public void hide(){
        boolean isSticky = false;
        if(mListView != null)
            isSticky = mStickWhenScrolled && mListView.getFirstVisiblePosition() != 0;

        if (getVisibility() == View.VISIBLE && !isSticky) {
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

    public void attachToListView(@NonNull AbsListView listView, boolean inverted) {
        attachToListView(listView, new FabOnScrollListener(inverted), inverted);
    }

    public void attachToListView(@NonNull AbsListView listView, @NonNull FabOnScrollListener onScrollListener, boolean inverted) {
        mListView = listView;
        onScrollListener.setFloatingActionButton(this);
        onScrollListener.setListView(listView);
        mListView.setOnScrollListener(onScrollListener);
        if(inverted) {
            mStickWhenScrolled = true;
            invertAnimations();
        }
    }

    public void detachFromListView() {
        hide();
        if(mListView == null)
            return;
        mListView.setOnScrollListener(null);
        mListView = null;
        mStickWhenScrolled = false;
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
        private boolean mInverted;
        public FabOnScrollListener(boolean inverted) {
            mInverted = inverted;
            setScrollDirectionListener(new ScrollDirectionListener() {
                @Override public void onScrollDown() {
                    if(mInverted)
                        mFloatingActionButton.hide();
                    else
                        mFloatingActionButton.show();
                }
                @Override public void onScrollUp() {
                    if(mInverted)
                        mFloatingActionButton.show();
                    else
                        mFloatingActionButton.hide();
                }
            });
        }
        public void setFloatingActionButton(EnhancedFloatingActionButton floatingActionButton) {
            mFloatingActionButton = floatingActionButton;
        }
    }
}
