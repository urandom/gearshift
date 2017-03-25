package org.sugr.gearshift.ui.view

import android.content.Context
import android.databinding.DataBindingUtil
import android.support.design.widget.Snackbar
import android.support.v4.app.FragmentManager
import android.support.v4.widget.NestedScrollView
import android.support.v7.app.AlertDialog
import android.util.AttributeSet
import android.view.MenuItem
import android.view.View
import com.thebluealliance.spectrum.SpectrumDialog
import io.reactivex.Single
import org.sugr.gearshift.R
import org.sugr.gearshift.databinding.FirstTimeProfileEditorBinding
import org.sugr.gearshift.ui.path.PathNavigator
import org.sugr.gearshift.viewmodel.ProfileEditorViewModel
import org.sugr.gearshift.viewmodel.api.AuthException
import org.sugr.gearshift.viewmodel.rxutil.single

class FirstTimeProfileEditorView(context: Context?, attrs: AttributeSet?) :
        NestedScrollView(context, attrs),
        ProfileEditorViewModel.Consumer,
        ViewModelConsumer<ProfileEditorViewModel>,
		PathNavigatorConsumer,
        ToolbarMenuItemClickListener {

	lateinit private var viewModel : ProfileEditorViewModel
	lateinit private var pathNavigator : PathNavigator

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (!inLayout()) {
            return
        }

        val binding : FirstTimeProfileEditorBinding = DataBindingUtil.bind(this)
        binding.viewModel = viewModel

        viewModel.bind(this)
    }

    override fun showUpdateIntervalPicker(current: Int) =
        single<Int> { e ->
            val builder = AlertDialog.Builder(context)

            builder.setTitle(R.string.update_interval)
                    .setItems(R.array.pref_update_interval_entries, { dialog, which ->
                        e.onSuccess(resources.getIntArray(R.array.pref_update_interval_values)[which])
                    })

            builder.show()
        }

    override fun selectColor(colors: IntArray, currentColor: Int, fragmentManager: FragmentManager): Single<Int> {
		return single<Int> { e ->
			SpectrumDialog.Builder(context)
					.setColors(colors)
					.setSelectedColor(currentColor)
					.setDismissOnColorSelected(true)
					.setNegativeButtonText(android.R.string.cancel)
					.setOutlineWidth(context.resources.getDimensionPixelSize(R.dimen.color_outline))
					.setOnColorSelectedListener { positiveResult, color ->
						e.onSuccess(color)
					}
					.build()
					.show(fragmentManager, "color_selector")

		}
	}

	override fun setPathNavigator(navigator: PathNavigator) {
		this.pathNavigator = navigator
	}


    override fun setViewModel(viewModel: ProfileEditorViewModel) {
        this.viewModel = viewModel
    }

    override fun onToolbarMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.save -> {
				if (!viewModel.canLeave()) {
					return true
				}

                item.setActionView(R.layout.action_view_progress)
                viewModel.check().subscribe({success ->
                    item.setActionView(null)
					if (success) {
						viewModel.save();
						pathNavigator.restorePath()
					} else {
						Snackbar.make((parent as View), R.string.profile_check_failure_generic, Snackbar.LENGTH_LONG).show()
					}
                }) { err ->
					val message = when (err) {
						is AuthException -> R.string.profile_check_failure_auth
						else -> R.string.profile_check_failure_generic
					}

					Snackbar.make((parent as View), message, Snackbar.LENGTH_LONG).show()
                    viewModel.log.E("profile check", err)
                    item.setActionView(null)
                }
                return true
            }
        }

        return false
    }

}

