package org.sugr.gearshift.ui.path

import android.os.Bundle
import org.sugr.gearshift.ui.view.DetachBlocker
import org.sugr.gearshift.ui.view.ViewDestructor
import java.util.*

class PathNavigator(private var bridge: PathViewBridge) {
    private val contentHierarchy = mutableListOf<Path>()

    companion object {
        val STATE_CONTENT_HIERARCHY = "state_content_hierarchy"
    }

    fun onSaveInstanceState(state: Bundle) {
        val list = ArrayList<Path>(contentHierarchy)
        state.putSerializable(STATE_CONTENT_HIERARCHY, list)
    }

    fun onRestoreInstanceState(state: Bundle?) {
        state ?: return
        val serializable = state.getSerializable(STATE_CONTENT_HIERARCHY)
        if (serializable is ArrayList<*>) {
            contentHierarchy.clear()
            contentHierarchy.addAll(serializable as ArrayList<Path>)
        }
    }

    fun restorePath() {
        val path = if (contentHierarchy.size > 0) {
            contentHierarchy.removeAt(contentHierarchy.size - 1)
        } else {
            bridge.defaultPath
        }
        setPath(path)
    }

    fun setPath(path: Path) {
        val current = contentHierarchy.lastOrNull() ?: bridge.defaultPath
        val currentDepth = current.depth
        val depth = path.depth

        if (currentDepth >= depth && current !== path) {
            val view = bridge.getContentView()
            if (view is ViewDestructor) {
                view.onDestroy()
            }
        }

        bridge.onSetContent(path, current)

        if (currentDepth > depth) {
            contentHierarchy.removeAt(contentHierarchy.size - 1)
        } else if (currentDepth == depth && contentHierarchy.size > 0) {
            contentHierarchy[contentHierarchy.size - 1] = path
        } else {
            contentHierarchy.add(path)
        }
    }

    fun navigateUp() : Boolean {
        if (contentHierarchy.size > 1) {
            val view = bridge.getContentView()
            if (view is DetachBlocker && !view.canDetach()) {
                return false
            }

            setPath(contentHierarchy[contentHierarchy.size - 2])

            return true
        }

        return false
    }
}

interface PathViewBridge {
    val defaultPath : Path
    fun getContentView(): Any?
    fun onSetContent(newPath: Path, oldPath: Path)
}