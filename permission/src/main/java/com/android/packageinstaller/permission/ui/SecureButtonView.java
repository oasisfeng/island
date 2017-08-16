/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.packageinstaller.permission.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.Button;

/**
 * Extension of Button that uses the hidden MotionEvent flag for partially obscured windows to
 * prevent tapjacking attacks.
 */
public class SecureButtonView extends Button {

    /**
     * This flag indicates that the window that received this motion event is partly
     * or wholly obscured by another visible window above it.  This flag is set to true
     * even if the event did not directly pass through the obscured area.
     * A security sensitive application can check this flag to identify situations in which
     * a malicious application may have covered up part of its content for the purpose
     * of misleading the user or hijacking touches.  An appropriate response might be
     * to drop the suspect touches or to take additional precautions to confirm the user's
     * actual intent.
     *
     * Unlike FLAG_WINDOW_IS_OBSCURED, this is actually true.
     */
    public static final int FLAG_WINDOW_IS_PARTIALLY_OBSCURED = 0x2;        // Same as MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED

    public SecureButtonView(Context context) {
        this(context, null);
    }

    public SecureButtonView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SecureButtonView(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public SecureButtonView(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean onFilterTouchEventForSecurity(MotionEvent event) {
        if ((event.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED) != 0
                || (event.getFlags() & FLAG_WINDOW_IS_PARTIALLY_OBSCURED) != 0) {
            // Window is obscured, drop this touch.
            return false;
        }
        return true;
    }
}
