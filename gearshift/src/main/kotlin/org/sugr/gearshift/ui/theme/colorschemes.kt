package org.sugr.gearshift.ui.theme

import org.sugr.gearshift.R

data class ColorScheme(
	val toolbarColor : Int,
	val statusBarColor : Int
)

val defaultColorScheme = ColorScheme(
		toolbarColor = R.color.colorPrimary,
		statusBarColor = R.color.colorPrimaryDark
)

val selectionColorScheme = ColorScheme(
		toolbarColor = R.color.selectionToolbar,
		statusBarColor = R.color.selectionStatusBar
)
