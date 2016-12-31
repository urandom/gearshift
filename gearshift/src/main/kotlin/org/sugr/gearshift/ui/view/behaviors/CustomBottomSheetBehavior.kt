package org.sugr.gearshift.ui.view.behaviors

import android.content.Context
import android.support.design.widget.BottomSheetBehavior
import android.support.design.widget.CoordinatorLayout
import android.support.v4.view.ViewCompat
import android.util.AttributeSet
import android.view.View

class CustomBottomSheetBehavior<V : View>(context: Context, attrs: AttributeSet):
        BottomSheetBehavior<V>(context, attrs) {

    override fun onLayoutChild(parent: CoordinatorLayout?, child: V, layoutDirection: Int): Boolean {
        val ret = super.onLayoutChild(parent, child, layoutDirection)

        ViewCompat.setFitsSystemWindows(child, false);

        return ret
    }
}

