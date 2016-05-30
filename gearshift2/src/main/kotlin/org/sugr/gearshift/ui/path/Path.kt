package org.sugr.gearshift.ui.path

import java.io.Serializable

interface Path: Serializable {
    val layout : Int

    val title : Int
        get() = 0
    val menu : Int
        get() = 0
    val depth : Int
        get() = 0

    fun isTopLevel() : Boolean {
        return depth == TOP_LEVEL
    }

    companion object {
        val TOP_LEVEL = 0
    }
}
