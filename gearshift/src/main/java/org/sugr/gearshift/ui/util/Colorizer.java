package org.sugr.gearshift.ui.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.ImageView;
import android.widget.TextView;

import org.sugr.gearshift.R;

public class Colorizer {
    public static void colorizeView(ImageView view, int color, int shape) {
        Resources res = view.getContext().getResources();

        Drawable currentDrawable = view.getDrawable();
        GradientDrawable colorChoiceDrawable;
        if (currentDrawable != null && currentDrawable instanceof GradientDrawable) {
            // Reuse drawable
            colorChoiceDrawable = (GradientDrawable) currentDrawable;
        } else {
            colorChoiceDrawable = new GradientDrawable();
            colorChoiceDrawable.setShape(shape);
        }

        int darkenedColor;
        if (color == defaultColor(view.getContext())) {
            darkenedColor = Color.rgb(95, 95, 95);
        } else {
            // Set stroke to dark version of color
            darkenedColor = Color.rgb(
                    Color.red(color) * 192 / 256,
                    Color.green(color) * 192 / 256,
                    Color.blue(color) * 192 / 256);
        }

        colorChoiceDrawable.setColor(color);
        colorChoiceDrawable.setStroke((int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 1, res.getDisplayMetrics()), darkenedColor);
        view.setImageDrawable(colorChoiceDrawable);

    }

    public static void colorizeView(TextView view, int color) {
        view.setTextColor(color);
    }

    public static int defaultColor(Context context) {

        int[] colorArray = context.getResources().getIntArray(R.array.default_color_choice_values);

        if (colorArray != null && colorArray.length > 0) {
            return colorArray[0];
        } else {
            return context.getResources().getColor(R.color.primary);
        }
    }

}
