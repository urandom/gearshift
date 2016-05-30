package org.sugr.gearshift.ui.view

import android.content.Context
import android.databinding.DataBindingUtil
import android.support.v7.app.AlertDialog
import android.util.AttributeSet
import android.widget.NumberPicker
import android.widget.ScrollView
import org.sugr.gearshift.R
import org.sugr.gearshift.databinding.FirstTimeProfileEditorBinding
import org.sugr.gearshift.model.transmissionProfile
import org.sugr.gearshift.viewmodel.ProfileEditorViewModel
import org.sugr.gearshift.viewmodel.destroyViewModel
import org.sugr.gearshift.viewmodel.viewModelFrom
import rx.Observable
import rx.lang.kotlin.observable
import rx.lang.kotlin.subscriber

@ViewDepth(1)
class FirstTimeProfileEditorView(context: Context?, attrs: AttributeSet?) :
        ScrollView(context, attrs), ViewDestructor, DetachBlocker, ProfileEditorViewModel.Consumer {
    lateinit private var viewModel : ProfileEditorViewModel

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (!inLayout()) {
            return
        }

        val activity = getActivity()
        val profile = transmissionProfile()
        viewModel = viewModelFrom(activity.fragmentManager) { tag, prefs ->
            ProfileEditorViewModel(tag, prefs, profile)
        }
        val binding : FirstTimeProfileEditorBinding = DataBindingUtil.bind(this)
        binding.viewModel = viewModel

        viewModel.bind(this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        viewModel.save()
    }

    override fun showFullUpdatePicker(current: Int): Observable<Int> {
        return observable { subscriber ->
            val builder = AlertDialog.Builder(context)

            builder.setTitle(R.string.full_update)
                    .setItems(R.array.pref_full_update_entries, { dialog, which ->
                        subscriber.onNext(resources.getIntArray(R.array.pref_full_update_values)[which])
                        subscriber.onCompleted()
                    })

            builder.show()
        }
    }

    override fun showUpdateIntervalPicker(current: Int): Observable<Int> {
        return observable { subscriber ->
            val builder = AlertDialog.Builder(context)

            builder.setTitle(R.string.update_interval)
                    .setItems(R.array.pref_update_interval_entries, { dialog, which ->
                        subscriber.onNext(resources.getIntArray(R.array.pref_update_interval_values)[which])
                        subscriber.onCompleted()
                    })

            builder.show()
        }
    }

    override fun onDestroy() {
        destroyViewModel(getActivity().fragmentManager, viewModel)
    }

    override fun canDetach(): Boolean {
        if (viewModel.isValid()) {
            return true
        }

        viewModel.refreshValidity()
        return false
    }

}

