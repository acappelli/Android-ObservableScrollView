/*
 * Copyright 2014 Soichiro Kashima
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.ksoichiro.android.observablescrollview.samples;

import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.github.ksoichiro.android.observablescrollview.ObservableScrollViewCallbacks;
import com.github.ksoichiro.android.observablescrollview.ScrollState;
import com.github.ksoichiro.android.observablescrollview.Scrollable;
import com.github.ksoichiro.android.observablescrollview.TouchInterceptionFrameLayout;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.animation.ValueAnimator;
import com.nineoldandroids.view.ViewHelper;
import com.nineoldandroids.view.ViewPropertyAnimator;

import org.w3c.dom.Text;

public abstract class SlidingUpBaseActivity<S extends Scrollable> extends ActionBarActivity implements ObservableScrollViewCallbacks {

    private View mHeader;
    private TextView mTitle;
    private View mImageView;
    private View mFab;
    private Toolbar mToolbar;
    private S mScrollable;
    private TouchInterceptionFrameLayout mInterceptionLayout;
    private int mActionBarSize;
    private int mIntersectionHeight;
    private int mHeaderBarHeight;
    private int mSlidingSlop;
    private float mScrollYOnDownMotion;
    private boolean mMoved;
    private float mInitialY;
    private float mMovedDistanceY;
    private int mFabMargin;
    private boolean mFabIsShown;
    private int mFlexibleSpaceImageHeight;
    private int mToolbarColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());

        mToolbar = (Toolbar) findViewById(R.id.toolbar);

        setSupportActionBar(mToolbar);
        ViewHelper.setScaleY(mToolbar, 0);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        mToolbarColor = getResources().getColor(R.color.primary);
        mToolbar.setBackgroundColor(Color.TRANSPARENT);

        mFlexibleSpaceImageHeight = getResources().getDimensionPixelSize(R.dimen.flexible_space_image_height);
        mIntersectionHeight = getResources().getDimensionPixelSize(R.dimen.intersection_height);
        mHeaderBarHeight = getResources().getDimensionPixelSize(R.dimen.header_bar_height);
        mSlidingSlop = getResources().getDimensionPixelSize(R.dimen.sliding_slop);
        mActionBarSize = getActionBarSize();

        mHeader = findViewById(R.id.header);
        mImageView = findViewById(R.id.image);
        mImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                slideOnClick();
            }
        });
        mScrollable = createScrollable();

        mFab = findViewById(R.id.fab);
        mFabMargin = getResources().getDimensionPixelSize(R.dimen.margin_standard);
        mFabIsShown = true;

        mInterceptionLayout = (TouchInterceptionFrameLayout) findViewById(R.id.scroll_wrapper);
        mInterceptionLayout.setScrollInterceptionListener(mInterceptionListener);
        mTitle = (TextView) findViewById(R.id.title);
        mTitle.setText(getTitle());
        ViewHelper.setTranslationY(mTitle, (mHeaderBarHeight - mActionBarSize) / 2);

        ViewTreeObserver vto = mInterceptionLayout.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                    mInterceptionLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                } else {
                    mInterceptionLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                }
                ViewHelper.setTranslationY(mInterceptionLayout, getScreenHeight() - mHeaderBarHeight);
                ViewHelper.setTranslationY(mImageView, getScreenHeight() - mHeaderBarHeight);
                if (mFab != null) {
                    ViewHelper.setTranslationX(mFab, mTitle.getWidth() - mFabMargin - mFab.getWidth());
                    ViewHelper.setTranslationY(mFab, ViewHelper.getX(mTitle) - (mFab.getHeight() / 2));
                }
            }
        });
    }

    protected abstract int getLayoutResId();
    protected abstract S createScrollable();

    @Override
    public void onScrollChanged(int scrollY, boolean firstScroll, boolean dragging) {
    }

    @Override
    public void onDownMotionEvent() {
    }

    @Override
    public void onUpOrCancelMotionEvent(ScrollState scrollState) {
    }

    private TouchInterceptionFrameLayout.TouchInterceptionListener mInterceptionListener = new TouchInterceptionFrameLayout.TouchInterceptionListener() {
        @Override
        public boolean shouldInterceptTouchEvent(MotionEvent ev, boolean moving, float diffX, float diffY) {
            final int minInterceptionLayoutY = -mIntersectionHeight;
            return minInterceptionLayoutY < (int) ViewHelper.getY(mInterceptionLayout)
                    || (moving && mScrollable.getCurrentScrollY() - diffY < 0);
        }

        @Override
        public void onDownMotionEvent(MotionEvent ev) {
            mScrollYOnDownMotion = mScrollable.getCurrentScrollY();
            mInitialY = ViewHelper.getTranslationY(mInterceptionLayout);
        }

        @Override
        public void onMoveMotionEvent(MotionEvent ev, float diffX, float diffY) {
            mMoved = true;
            float translationY = ViewHelper.getTranslationY(mInterceptionLayout) - mScrollYOnDownMotion + diffY;
            if (translationY < -mIntersectionHeight) {
                translationY = -mIntersectionHeight;
            } else if (getScreenHeight() - mHeaderBarHeight < translationY) {
                translationY = getScreenHeight() - mHeaderBarHeight;
            }

            slideTo(translationY);

            mMovedDistanceY = ViewHelper.getTranslationY(mInterceptionLayout) - mInitialY;
        }

        @Override
        public void onUpOrCancelMotionEvent(MotionEvent ev) {
            if (!mMoved) {
                // Invoke slide animation only on header view
                Rect outRect = new Rect();
                mHeader.getHitRect(outRect);
                if (outRect.contains((int) ev.getX(), (int) ev.getY())) {
                    slideOnClick();
                }
            } else {
                stickToAnchors();
            }
            mMoved = false;
        }
    };

    private void slideOnClick() {
        float translationY = ViewHelper.getTranslationY(mInterceptionLayout);
        if (translationY == getAnchorYBottom()) {
            slideWithAnimation(getAnchorYImage());
        } else if (translationY == getAnchorYImage()) {
            slideWithAnimation(getAnchorYBottom());
        }
    }

    private void stickToAnchors() {
        // Slide to some points automatically
        if (0 < mMovedDistanceY) {
            // Sliding down
            if (mSlidingSlop < mMovedDistanceY) {
                // Sliding down to an anchor
                if (getAnchorYImage() < ViewHelper.getTranslationY(mInterceptionLayout)) {
                    slideWithAnimation(getAnchorYBottom());
                } else {
                    slideWithAnimation(getAnchorYImage());
                }
            } else {
                // Sliding up(back) to an anchor
                if (getAnchorYImage() < ViewHelper.getTranslationY(mInterceptionLayout)) {
                    slideWithAnimation(getAnchorYImage());
                } else {
                    slideWithAnimation(0);
                }
            }
        } else if (mMovedDistanceY < 0) {
            // Sliding up
            if (mMovedDistanceY < -mSlidingSlop) {
                // Sliding up to an anchor
                if (getAnchorYImage() < ViewHelper.getTranslationY(mInterceptionLayout)) {
                    slideWithAnimation(getAnchorYImage());
                } else {
                    slideWithAnimation(0);
                }
            } else {
                // Sliding down(back) to an anchor
                if (getAnchorYImage() < ViewHelper.getTranslationY(mInterceptionLayout)) {
                    slideWithAnimation(getAnchorYBottom());
                } else {
                    slideWithAnimation(getAnchorYImage());
                }
            }
        }
    }

    private void slideTo(float translationY) {
        ViewHelper.setTranslationY(mInterceptionLayout, translationY);

        if (translationY < 0) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) mInterceptionLayout.getLayoutParams();
            lp.height = (int) -translationY + getScreenHeight();
            mInterceptionLayout.requestLayout();
        }

        // Translate title
        float hiddenHeight = translationY < 0 ? -translationY : 0;
        ViewHelper.setTranslationY(mTitle, Math.min(mIntersectionHeight, (mHeaderBarHeight + hiddenHeight - mActionBarSize) / 2));

        // Translate image
        float imageAnimatableHeight = getScreenHeight() - mHeaderBarHeight;
        float imageTranslationScale = imageAnimatableHeight / (imageAnimatableHeight - mImageView.getHeight());
        float imageTranslationY = Math.max(0, imageAnimatableHeight - (imageAnimatableHeight - translationY) * imageTranslationScale);
        ViewHelper.setTranslationY(mImageView, imageTranslationY);

        // Show/hide FAB
        if (ViewHelper.getTranslationY(mInterceptionLayout) < mFlexibleSpaceImageHeight) {
            hideFab();
        } else {
            ViewPropertyAnimator.animate(mToolbar).scaleY(0).setDuration(200).start();
            showFab();
        }
        if (ViewHelper.getTranslationY(mInterceptionLayout) <= mFlexibleSpaceImageHeight) {
            ViewPropertyAnimator.animate(mToolbar).scaleY(1).setDuration(200).start();
            setBackgroundAlpha(mToolbar, 0, mToolbarColor);
        }

        if(ViewHelper.getTranslationY(mInterceptionLayout) <= (mToolbar.getHeight())) {
            ViewPropertyAnimator.animate(mTitle).scaleY(0).start();
            getSupportActionBar().setTitle(mTitle.getText());
        }
        else {
            ViewPropertyAnimator.animate(mTitle).scaleY(1).start();
            getSupportActionBar().setTitle(null);
        }
    }

    private void slideWithAnimation(float toY) {
        float layoutTranslationY = ViewHelper.getTranslationY(mInterceptionLayout);
        if (layoutTranslationY != toY) {
            ValueAnimator animator = ValueAnimator.ofFloat(ViewHelper.getTranslationY(mInterceptionLayout), toY).setDuration(200);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    slideTo((float) animation.getAnimatedValue());
                }
            });
            animator.start();
        }
    }

    private float getAnchorYBottom() {
        return getScreenHeight() - mHeaderBarHeight;
    }

    private float getAnchorYImage() {
        return mImageView.getHeight();
    }

    private int getActionBarSize() {
        TypedValue typedValue = new TypedValue();
        int[] textSizeAttr = new int[]{R.attr.actionBarSize};
        int indexOfAttrTextSize = 0;
        TypedArray a = obtainStyledAttributes(typedValue.data, textSizeAttr);
        int actionBarSize = a.getDimensionPixelSize(indexOfAttrTextSize, -1);
        a.recycle();
        return actionBarSize;
    }

    private int getScreenHeight() {
        return findViewById(android.R.id.content).getHeight();
    }

    private void showFab() {
        if (!mFabIsShown && mFab != null) {
            ViewPropertyAnimator.animate(mFab).cancel();
            ViewPropertyAnimator.animate(mFab).scaleX(1).scaleY(1).setDuration(200).start();
            mFabIsShown = true;
        }
    }

    private void hideFab() {
        if (mFabIsShown && mFab != null) {
            ViewPropertyAnimator.animate(mFab).cancel();
            ViewPropertyAnimator.animate(mFab).scaleX(0).scaleY(0).setDuration(200).start();
            mFabIsShown = false;
        }
    }

    private void setBackgroundAlpha(View view, float alpha, int baseColor) {
        int a = Math.min(255, Math.max(0, (int) (alpha * 255))) << 24;
        int rgb = 0x00ffffff & baseColor;
        view.setBackgroundColor(a + rgb);
    }

}
