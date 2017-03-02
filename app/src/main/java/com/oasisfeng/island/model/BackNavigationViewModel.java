package com.oasisfeng.island.model;

import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.ListIterator;

/**
 *
 * On back pressed navigation stack.
 *
 * Created by heruoxin on 2017/2/25.
 */
public class BackNavigationViewModel {

    private final LinkedHashMap<String, BackEntry> mStack = new LinkedHashMap<>();

    public void add(String key, BackEntry back) {
        mStack.put(key, back);
    }

    @Nullable
    public BackEntry remove(String key) {
        return mStack.remove(key);
    }

    public boolean onBackPressed() {
        ListIterator<BackEntry> iterator = new ArrayList<>(mStack.values()).listIterator(mStack.size());
        while (iterator.hasPrevious()) {
            if (iterator.previous().onBackPressed()) return true;
        }
        return false;
    }

    public interface BackEntry {
        boolean onBackPressed();
    }
}
