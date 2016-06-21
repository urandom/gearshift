package org.sugr.gearshift.ui.path

import android.os.Bundle
import android.support.v4.app.FragmentManager
import org.sugr.gearshift.viewmodel.LeaveBlocker
import java.util.*

class PathNavigator(private var consumer: PathNavigator.Consumer) {
    private val contentHierarchy = mutableListOf<Path<*>>()

    interface Consumer {
        val defaultPath : Path<*>
        fun getSupportFragmentManager(): FragmentManager
        fun onSetContent(newPath: Path<*>, oldPath: Path<*>)
    }

    companion object {
        val STATE_CONTENT_HIERARCHY = "state_content_hierarchy"
    }

    fun onSaveInstanceState(state: Bundle) {
        val list = ArrayList<Path<*>>(contentHierarchy)
        state.putSerializable(STATE_CONTENT_HIERARCHY, list)
    }

    fun onRestoreInstanceState(state: Bundle?) {
        state ?: return
        val serializable = state.getSerializable(STATE_CONTENT_HIERARCHY)
        if (serializable is ArrayList<*>) {
            contentHierarchy.clear()
            contentHierarchy.addAll(serializable as ArrayList<Path<*>>)
        }
    }

    fun restorePath() {
        val path = if (contentHierarchy.size > 0) {
            contentHierarchy.removeAt(contentHierarchy.size - 1)
        } else {
            consumer.defaultPath
        }
        setPath(path)
    }

    fun setPath(path: Path<*>) {
        val current = contentHierarchy.lastOrNull() ?: consumer.defaultPath
        val currentDepth = current.depth
        val depth = path.depth

        if (currentDepth >= depth && current !== path) {
            current.destroyViewModel(consumer.getSupportFragmentManager())
        }

        consumer.onSetContent(path, current)

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
            val vm = contentHierarchy.last().getViewModel(consumer.getSupportFragmentManager())
            if (vm is LeaveBlocker && !vm.canLeave()) {
                return false
            }

            setPath(contentHierarchy[contentHierarchy.size - 2])

            return true
        }

        return false
    }
}

