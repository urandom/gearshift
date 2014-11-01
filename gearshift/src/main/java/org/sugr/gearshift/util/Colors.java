package org.sugr.gearshift.util;

import android.content.res.Resources;
import android.graphics.Color;
import android.support.annotation.ColorRes;

import org.sugr.gearshift.R;

public class Colors {
    @ColorRes public static int colorFromCharSequence(CharSequence seq, Resources res) {
        int num = 0;
        if (seq.length() > 0) {
            char c = Character.toLowerCase(seq.charAt(0));

            int cint = (int) c;
            num = cint % 10 + 1;
        }
        int resId;

        switch (num) {
            case 2:
                resId = R.color.torrent_color_2;
                break;
            case 3:
                resId = R.color.torrent_color_3;
                break;
            case 4:
                resId = R.color.torrent_color_4;
                break;
            case 5:
                resId = R.color.torrent_color_5;
                break;
            case 6:
                resId = R.color.torrent_color_6;
                break;
            case 7:
                resId = R.color.torrent_color_7;
                break;
            case 8:
                resId = R.color.torrent_color_8;
                break;
            case 9:
                resId = R.color.torrent_color_9;
                break;
            case 10:
                resId = R.color.torrent_color_10;
                break;
            default:
                resId = R.color.torrent_color_1;
                break;
        }

        return res.getColor(resId);
    }
}
