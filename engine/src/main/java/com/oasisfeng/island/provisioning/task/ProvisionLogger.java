/*
 * Copyright 2014, The Android Open Source Project
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

package com.oasisfeng.island.provisioning.task;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

/**
 * Utility class to centralize the logging in the Provisioning app.
 */
class ProvisionLogger {
    private static final String TAG = "ManagedProvisioning";
    private static final boolean LOG_ENABLED = true;

    // Never commit this as true.
    public static final boolean IS_DEBUG_BUILD = false;

    /**
     * Log the message at DEBUG level.
     */
    public static void logd(String message) {
        if (LOG_ENABLED) {
            Log.d(getTag(), message);
        }
    }

    /**
     * Log the message at DEBUG level.
     */
    public static void logd(String message, Throwable t) {
        if (LOG_ENABLED) {
            Log.d(getTag(), message, t);
        }
    }

    /**
     * Log the message at DEBUG level.
     */
    public static void logd(Throwable t) {
        if (LOG_ENABLED) {
            Log.d(getTag(), "", t);
        }
    }

    /**
     * Log the message at VERBOSE level.
     */
    public static void logv(String message) {
        if (LOG_ENABLED) {
            Log.v(getTag(), message);
        }
    }

    /**
     * Log the message at VERBOSE level.
     */
    public static void logv(String message, Throwable t) {
        if (LOG_ENABLED) {
            Log.v(getTag(), message, t);
        }
    }

    /**
     * Log the message at VERBOSE level.
     */
    public static void logv(Throwable t) {
        if (LOG_ENABLED) {
            Log.v(getTag(), "", t);
        }
    }

    /**
     * Log the message at INFO level.
     */
    public static void logi(String message) {
        if (LOG_ENABLED) {
            Log.i(getTag(), message);
        }
    }

    /**
     * Log the message at INFO level.
     */
    public static void logi(String message, Throwable t) {
        if (LOG_ENABLED) {
            Log.i(getTag(), message, t);
        }
    }

    /**
     * Log the message at INFO level.
     */
    public static void logi(Throwable t) {
        if (LOG_ENABLED) {
            Log.i(getTag(), "", t);
        }
    }

    /**
     * Log the message at WARNING level.
     */
    public static void logw(String message) {
        if (LOG_ENABLED) {
            Log.w(getTag(), message);
        }
    }

    /**
     * Log the message at WARNING level.
     */
    public static void logw(String message, Throwable t) {
        if (LOG_ENABLED) {
            Log.w(getTag(), message, t);
        }
    }

    /**
     * Log the message at WARNING level.
     */
    public static void logw(Throwable t) {
        if (LOG_ENABLED) {
            Log.w(getTag(), "", t);
        }
    }

    /**
     * Log the message at ERROR level.
     */
    public static void loge(String message) {
        if (LOG_ENABLED) {
            Log.e(getTag(), message);
        }
    }

    /**
     * Log the message at ERROR level.
     */
    public static void loge(String message, Throwable t) {
        if (LOG_ENABLED) {
            Log.e(getTag(), message, t);
        }
    }

    /**
     * Log the message at ERROR level.
     */
    public static void loge(Throwable t) {
        if (LOG_ENABLED) {
            Log.e(getTag(), "", t);
        }
    }

    /**
     * Walks the stack trace to figure out where the logging call came from.
     */
    static String getTag() {
        if (IS_DEBUG_BUILD) {
            String className = ProvisionLogger.class.getName();

            StackTraceElement[] trace = Thread.currentThread().getStackTrace();
            if (trace == null) {
                return TAG;
            }

            boolean thisClassFound = false;
            for (StackTraceElement item : trace) {
                if (item.getClassName().equals(className)) {
                    // we are at the current class, keep eating all items from this
                    // class.
                    thisClassFound = true;
                    continue;
                }

                if (thisClassFound) {
                    // This is the first instance of another class, which is most
                    // likely the caller class.
                    return TAG + String.format(
                            "[%s(%s): %s]", item.getFileName(), item.getLineNumber(),
                            item.getMethodName());
                }
            }
        }
        return TAG;
    }

    public static void toast(Context context, String toast) {
        if (IS_DEBUG_BUILD) {
            Toast.makeText(context, toast, Toast.LENGTH_LONG).show();
        }
    }
}
