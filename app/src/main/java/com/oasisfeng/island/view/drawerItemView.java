package com.oasisfeng.island.view;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import com.oasisfeng.island.databinding.AppDrawerItemBinding;

/**
 * Selectable island/mainland item in drawer.
 *
 * Created by heruoxin on 2016/12/17.
 */
public class drawerItemView extends FrameLayout {

    private AppDrawerItemBinding mBinding;

    public drawerItemView(Context context) {
        super(context);
        init(context, null);
    }

    public drawerItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public drawerItemView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public drawerItemView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    private void init(Context context, @Nullable AttributeSet attrs) {
        mBinding = AppDrawerItemBinding.inflate(LayoutInflater.from(context), this, true);
    }

    public void setItemTitle(@StringRes int titleRes) {
        setItemTitle(getContext().getString(titleRes));
    }

    public void setItemTitle(String title) {
        mBinding.setTitle(title);
    }

    public void setItemDescription(@StringRes int descriptionRes) {
        setItemDescription(getContext().getString(descriptionRes));
    }

    public void setItemDescription(String description) {
        mBinding.setDescription(description);
    }

    public void setItemSelected(boolean selected) {
        mBinding.setSelected(selected);
    }

}
