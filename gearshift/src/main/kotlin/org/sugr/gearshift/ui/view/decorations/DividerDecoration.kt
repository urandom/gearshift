package org.sugr.gearshift.ui.view.decorations

import android.content.Context
import android.graphics.Canvas
import android.support.v7.widget.RecyclerView
import org.sugr.gearshift.R
import org.sugr.gearshift.ui.view.util.asSequence

class DividerDecoration(ctx: Context): RecyclerView.ItemDecoration() {
    val divider = ctx.getDrawable(R.drawable.line_divider)

    override fun onDrawOver(c: Canvas?, parent: RecyclerView, state: RecyclerView.State) {
        super.onDrawOver(c, parent, state)
        val left = parent.paddingLeft
        val right = parent.width - parent.paddingRight

        parent.asSequence().map { child ->
            child.bottom + (child.layoutParams as RecyclerView.LayoutParams).bottomMargin
        }.forEach { top ->
            val bottom = top + divider.intrinsicHeight

            divider.setBounds(left, top, right, bottom)
            divider.draw(c)
        }

    }
}


