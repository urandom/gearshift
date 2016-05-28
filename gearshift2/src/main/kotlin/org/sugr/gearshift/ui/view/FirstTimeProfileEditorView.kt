package org.sugr.gearshift.ui.view

import android.content.Context
import android.databinding.DataBindingUtil
import android.util.AttributeSet
import android.widget.ScrollView
import org.sugr.gearshift.databinding.FirstTimeProfileEditorBinding
import org.sugr.gearshift.model.transmissionProfile
import org.sugr.gearshift.viewmodel.ProfileEditorViewModel
import org.sugr.gearshift.viewmodel.destroyViewModel
import org.sugr.gearshift.viewmodel.viewModelFrom
import rx.Observable

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
    }

    override fun showFullUpdatePicker(current: Int): Observable<Int> {
        throw UnsupportedOperationException()
    }

    override fun showUpdateIntervalPicker(current: Int): Observable<Int> {
        throw UnsupportedOperationException()
    }

    override fun onDestroy() {
        destroyViewModel(getActivity().fragmentManager, viewModel)
    }

    override fun canDetach(): Boolean {
        return viewModel.save()
    }

}

