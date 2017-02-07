package org.sugr.gearshift.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.support.v4.view.ViewCompat
import android.support.v4.view.animation.FastOutLinearInInterpolator
import android.support.v4.view.animation.LinearOutSlowInInterpolator
import android.support.v7.widget.Toolbar
import android.util.AttributeSet
import android.view.View
import org.sugr.gearshift.ui.view.util.asSequence
import android.support.design.widget.CollapsingToolbarLayout as CTL

class CollapsingToolbarLayout(ctx: Context, attrs: AttributeSet?) : CTL(ctx, attrs) {
	private var scrimAreShown = false
	private var scrimAnimator : ValueAnimator? = null
	private var scrimAlpha = 0x0
	private var toolbar : Toolbar? = null

	override fun drawChild(canvas: Canvas?, child: View?, drawingTime: Long): Boolean {
		var invalidate = super.drawChild(canvas, child, drawingTime)

		val scrim = super.getContentScrim()
		if (scrim != null && child is Toolbar) {
			scrim.mutate()?.setAlpha(scrimAlpha);
			scrim.draw(canvas);
			invalidate = true;
		}

		return invalidate
	}

	override fun setScrimsShown(shown: Boolean, animate: Boolean) {
		if (scrimAreShown != shown) {
			val alpha = if (shown) 0xff else 0x0

			if (animate) {
				animateScrim(alpha)
			} else {
				applyScrimAlpha(alpha)
			}

			scrimAreShown = shown
		}
	}

	private fun animateScrim(targetAlpha: Int) {
		if (scrimAnimator == null) {
			scrimAnimator = ValueAnimator().apply {
				duration = 600
				interpolator =
						if (targetAlpha > scrimAlpha) FastOutLinearInInterpolator()
						else LinearOutSlowInInterpolator()
				addUpdateListener { animator ->
					applyScrimAlpha(animator.animatedValue as Int)
				}
			}
		} else if (scrimAnimator?.isRunning ?: false) {
			scrimAnimator?.cancel()
		}

		scrimAnimator?.apply {
			setIntValues(scrimAlpha, targetAlpha)
			start()
		}
	}

	private fun applyScrimAlpha(alpha: Int) {
		if (alpha != scrimAlpha) {
			val scrim = super.getContentScrim()
			if (scrim != null && toolbar != null) {
				ViewCompat.postInvalidateOnAnimation(toolbar)
			}
			scrimAlpha = alpha
			ViewCompat.postInvalidateOnAnimation(this)
		}
	}

	private fun findToolbar() {
		toolbar = asSequence().filter { it is Toolbar }.map { it as Toolbar }.firstOrNull()
	}
}
