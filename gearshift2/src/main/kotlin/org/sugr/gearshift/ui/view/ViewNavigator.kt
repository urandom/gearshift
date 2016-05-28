package org.sugr.gearshift.ui.view

import android.support.annotation.LayoutRes
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

interface ViewNavigator {
    val viewContainer : ViewGroup
    val contentIndex : Int
    val contentHierarchy : MutableList<Int>
    val defaultContent : Int

    fun setContent(@LayoutRes layout : Int) {
        val existing = getContent()
        val existingDepth = if (existing == null) Depth.TOP_LEVEL else viewDepth(existing)

        val content = getLayoutInflater().inflate(layout, viewContainer, false)
        val contentDepth = viewDepth(content)

        if (existingDepth >= contentDepth && existing is ViewDestructor) {
            existing.onDestroy()
        }

        if (existing != null) {
            viewContainer.removeViewAt(contentIndex)
        }
        viewContainer.addView(content, contentIndex)

        if (existingDepth > contentDepth) {
            contentHierarchy.removeAt(contentHierarchy.size - 1)
        } else if (existingDepth == contentDepth) {
            contentHierarchy[contentHierarchy.size - 1] = layout
        } else {
            contentHierarchy.add(layout)
        }

        onSetContent(existingDepth, contentDepth)
    }

    fun navigateUp(fromBackButton: Boolean = false) {
        if (contentHierarchy.size > 1) {
            val existing = getContent()
            if (existing is DetachBlocker && !existing.canDetach()) {
                return
            }

            setContent(
                    if (contentHierarchy.size > 1) contentHierarchy[contentHierarchy.size - 2]
                    else defaultContent
            )

            onNavigateUp(true, fromBackButton)
        } else {
            onNavigateUp(false, fromBackButton)
        }
    }

    fun onSetContent(oldDepth: Int, newDepth: Int)
    fun onNavigateUp(didNavigate: Boolean, fromBackButton: Boolean)
    fun getLayoutInflater() : LayoutInflater
    fun getContent() : View?
}

