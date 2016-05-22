package org.sugr.gearshift.viewmodel

import android.app.Fragment
import android.app.FragmentManager
import android.content.SharedPreferences
import android.os.Bundle

import org.sugr.gearshift.App

class RetainedFragment<VM : RetainedViewModel<T>, T> : Fragment() {
    var viewModel: VM? = null

    // Interface for creating a view model with a lambda
    interface Factory<VM : RetainedViewModel<T>, T> {
        fun create(prefs: SharedPreferences): VM
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        retainInstance = true
    }

    override fun onDestroy() {
        super.onDestroy()

        if (viewModel != null) {
            viewModel!!.onDestroy()
        }
    }

    companion object {

        @SuppressWarnings("unchecked")
        fun <VM : RetainedViewModel<T>, T> getViewModel(fm: FragmentManager,
                                                        tag: String,
                                                        factory: Factory<VM, T>): VM {

            var fragment: RetainedFragment<VM, T>? = fm.findFragmentByTag(tag) as RetainedFragment<VM, T>

            if (fragment == null) {
                fragment = RetainedFragment<VM, T>()
                fragment.viewModel = factory.create(App.defaultPreferences())

                // TODO: commit -> commitNow (support v24)
                fm.beginTransaction().add(fragment, tag).commit()
            }

            return fragment.viewModel as VM
        }
    }
}