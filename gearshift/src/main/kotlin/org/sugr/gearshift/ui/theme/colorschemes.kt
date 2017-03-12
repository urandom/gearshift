package org.sugr.gearshift.ui.theme

import org.sugr.gearshift.R

data class ColorScheme(
	val toolbarColor : Int,
	val statusBarColor : Int,
	val textColor: Int = R.color.md_white
)

val defaultColorScheme = ColorScheme(
		toolbarColor = R.color.defaultToolbar,
		statusBarColor = R.color.defaultStatusBar
)

val selectionColorScheme = ColorScheme(
		toolbarColor = R.color.selectionToolbar,
		statusBarColor = R.color.selectionStatusBar
)

val redColorScheme = ColorScheme(
		toolbarColor = R.color.redToolbar,
		statusBarColor = R.color.redStatusBar
)

val pinkColorScheme = ColorScheme(
		toolbarColor = R.color.pinkToolbar,
		statusBarColor = R.color.pinkStatusBar
)

val purpleColorScheme = ColorScheme(
		toolbarColor = R.color.purpleToolbar,
		statusBarColor = R.color.purpleStatusBar
)

val deepPurpleColorScheme = ColorScheme(
		toolbarColor = R.color.deepPurpleToolbar,
		statusBarColor = R.color.deepPurpleStatusBar
)

val indigoColorScheme = ColorScheme(
		toolbarColor = R.color.indigoToolbar,
		statusBarColor = R.color.selectionStatusBar
)

val blueColorScheme = ColorScheme(
		toolbarColor = R.color.blueToolbar,
		statusBarColor = R.color.blueStatusBar
)

val lightBlueColorScheme = ColorScheme(
		toolbarColor = R.color.lightBlueToolbar,
		statusBarColor = R.color.lightBlueStatusBar,
		textColor = R.color.md_black
)

val cyanColorScheme = ColorScheme(
		toolbarColor = R.color.cyanToolbar,
		statusBarColor = R.color.cyanStatusBar
)

val tealColorScheme = ColorScheme(
		toolbarColor = R.color.tealToolbar,
		statusBarColor = R.color.tealStatusBar
)

val greenColorScheme = ColorScheme(
		toolbarColor = R.color.greenToolbar,
		statusBarColor = R.color.greenStatusBar
)

val lightGreenColorScheme = ColorScheme(
		toolbarColor = R.color.lightGreenToolbar,
		statusBarColor = R.color.lightGreenStatusBar,
		textColor = R.color.md_black
)

val limeColorScheme = ColorScheme(
		toolbarColor = R.color.limeToolbar,
		statusBarColor = R.color.limeStatusBar,
		textColor = R.color.md_black
)

val yellowColorScheme = ColorScheme(
		toolbarColor = R.color.yellowToolbar,
		statusBarColor = R.color.yellowStatusBar,
		textColor = R.color.md_black
)

val orangeColorScheme = ColorScheme(
		toolbarColor = R.color.orangeToolbar,
		statusBarColor = R.color.orangeStatusBar,
		textColor = R.color.md_black
)

val deepOrangeColorScheme = ColorScheme(
		toolbarColor = R.color.deepOrangeToolbar,
		statusBarColor = R.color.deepOrangeStatusBar
)

val brownColorScheme = ColorScheme(
		toolbarColor = R.color.brownToolbar,
		statusBarColor = R.color.brownStatusBar
)

val greyColorScheme = ColorScheme(
		toolbarColor = R.color.greyToolbar,
		statusBarColor = R.color.greyStatusBar
)

val colorSchemes = arrayOf(
		defaultColorScheme, redColorScheme, pinkColorScheme, purpleColorScheme,
		deepPurpleColorScheme, indigoColorScheme, blueColorScheme, lightBlueColorScheme,
		cyanColorScheme, tealColorScheme, greenColorScheme, lightGreenColorScheme,
		limeColorScheme, yellowColorScheme, orangeColorScheme, deepOrangeColorScheme,
		brownColorScheme, greyColorScheme
)

fun colorSchemeFor(color: Int): ColorScheme {
	return colorSchemes.filter { it.toolbarColor == color }.first()
}
