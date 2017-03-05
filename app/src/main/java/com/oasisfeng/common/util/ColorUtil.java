package com.oasisfeng.common.util;

import android.graphics.Color;
import android.support.annotation.ColorInt;
import android.support.v7.graphics.Palette;

/**
 * Created by heruoxin on 16/4/15.
 */
public class ColorUtil {

    public static @ColorInt int overlay(@ColorInt int fg, @ColorInt int bg) {
        int fgA = Color.alpha(fg);
        int bgA = Color.alpha(bg);
        int fgR = Color.red(fg);
        int bgR = Color.red(bg);
        int fgG = Color.green(fg);
        int bgG = Color.green(bg);
        int fgB = Color.blue(fg);
        int bgB = Color.blue(bg);

        int reA = fgA + bgA - fgA * bgA;
        int reR = (fgR * fgA + bgR * bgA - fgR * fgA * bgA) / reA;
        int reG = (fgG * fgA + bgG * bgA - fgG * fgA * bgA) / reA;
        int reB = (fgB * fgA + bgB * bgA - fgB * fgA * bgA) / reA;
        return Color.argb(reA, reR, reG, reB);
    }

    public static @ColorInt int setAlpha(@ColorInt int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    public static String toString(@ColorInt int color) {
        String a = "00" + Integer.toString(Color.alpha(color), 16);
        String r = "00" + Integer.toString(Color.red(color), 16);
        String g = "00" + Integer.toString(Color.green(color), 16);
        String b = "00" + Integer.toString(Color.blue(color), 16);
        StringBuilder builder = new StringBuilder("#");
        if (!a.contains("ff")) builder.append(a.substring(a.length() - 2, a.length()));
        return builder.append(r.substring(r.length() - 2, r.length()))
                .append(g.substring(g.length() - 2, g.length()))
                .append(b.substring(b.length() - 2, b.length()))
                .toString().toUpperCase();
    }

    private static float interpolate(final float a, final float b, final float proportion) {
        return a + (b - a) * proportion;
    }

    /** Returns an interpoloated color, between <code>a</code> and <code>b</code> */
    @ColorInt
    public static int interpolateColorHsv(@ColorInt final int a, @ColorInt final int b, final float proportion) {
        int alphaA = Color.alpha(a);
        int alphaB = Color.alpha(b);
        final float[] hsva = new float[3];
        final float[] hsvb = new float[3];
        Color.colorToHSV(a, hsva);
        Color.colorToHSV(b, hsvb);
        for (int i = 0; i < 3; ++i) {
            hsvb[i] = interpolate(hsva[i], hsvb[i], proportion);
        }
        int alpha = (int) interpolate(alphaA, alphaB, proportion);
        @ColorInt int color = Color.HSVToColor(hsvb);
        return (color & 0x00ffffff) | (alpha << 24);
    }

    @ColorInt
    public static int interpolateColorRGB(@ColorInt final int colorA, @ColorInt final int colorB, final float bAmount) {
        final float aAmount = 1.0f - bAmount;
        final int alpha = (int) (Color.alpha(colorA) * aAmount + Color.alpha(colorB) * bAmount);
        final int red = (int) (Color.red(colorA) * aAmount + Color.red(colorB) * bAmount);
        final int green = (int) (Color.green(colorA) * aAmount + Color.green(colorB) * bAmount);
        final int blue = (int) (Color.blue(colorA) * aAmount + Color.blue(colorB) * bAmount);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    @ColorInt
    public static int getTitleTextColor(@ColorInt int backgroundColor) {
        return new Palette.Swatch(setAlpha(backgroundColor, 0xff), 0).getTitleTextColor();
    }

    @ColorInt
    public static int getBodyTextColor(@ColorInt int backgroundColor) {
        return new Palette.Swatch(setAlpha(backgroundColor, 0xff), 0).getBodyTextColor();
    }

}
