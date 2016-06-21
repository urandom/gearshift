package org.sugr.gearshift.ui.view.util

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import java.util.*

fun ViewGroup.asSequence(): Sequence<View> = object : Sequence<View> {
    override fun iterator(): Iterator<View> = object : Iterator<View> {
        private var nextValue: View? = null
        private var done = false
        private var position: Int = 0

        override fun hasNext(): Boolean {
            if (nextValue == null && !done && childCount > position) {
                nextValue = getChildAt(position)
                position++
                if (nextValue == null) done = true
            }
            return nextValue != null
        }

        override fun next(): View {
            if (!hasNext()) {
                throw NoSuchElementException()
            }
            val answer = nextValue
            nextValue = null
            return answer!!
        }
    }
}

fun Menu.asSequence(): Sequence<MenuItem> = object : Sequence<MenuItem> {
    override fun iterator(): Iterator<MenuItem> = object  : Iterator<MenuItem> {
        private var nextValue: MenuItem? = null
        private var done = false
        private var position: Int = 0

        override fun hasNext(): Boolean {
            if (nextValue == null && !done && size() > position) {
                nextValue = getItem(position)
                position++
                if (nextValue == null) done = true
            }
            return nextValue != null
        }

        override fun next(): MenuItem {
            if (!hasNext()) {
                throw NoSuchElementException()
            }
            val answer = nextValue
            nextValue = null
            return answer!!
        }
    }
}