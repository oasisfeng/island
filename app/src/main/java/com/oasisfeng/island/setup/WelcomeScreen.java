package com.oasisfeng.island.setup;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.FrameLayout;

/**
 * This view is designed to show the Introduction about Island.
 *
 * @author Lody
 *
 */
public class WelcomeScreen extends FrameLayout {

    public WelcomeScreen(Context context) {
        super(context);
        setupView();
    }

    public WelcomeScreen(Context context, AttributeSet attrs) {
        super(context, attrs);
        setupView();
    }

    public WelcomeScreen(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setupView();
    }

    private void setupView() {
        setBackgroundColor(Color.parseColor("#dddddd"));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }
}
