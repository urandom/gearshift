package org.sugr.gearshift.viewmodel

import android.app.Fragment
import android.app.FragmentManager
import android.content.SharedPreferences
import android.os.Bundle
import org.sugr.gearshift.defaultPreferences

class RetainedFragment<VM : RetainedViewModel<T>, T> : Fragment() {
    var viewModel: VM? = null

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
}

inline fun <reified VM : RetainedViewModel<T>, T> viewModelFrom(fm: FragmentManager,
                                                                tag: String = VM::class.java.toString(),
                                                                factory: (tag: String, prefs: SharedPreferences) -> VM): VM {
    var fragment = fm.findFragmentByTag(tag) as? RetainedFragment<VM, T>

    if (fragment == null) {
        fragment = RetainedFragment<VM, T>()
        fragment.viewModel = factory(tag, defaultPreferences())

        // TODO: commit -> commitNow (support v24)
        fm.beginTransaction().add(fragment, tag).commit()
    }

    return fragment.viewModel as VM
}

inline fun <reified VM : RetainedViewModel<T>, T> destroyViewModel(fm: FragmentManager,
                                                                   viewModel: VM) {

    val fragment = fm.findFragmentByTag(viewModel.tag) as? RetainedFragment<*, *>

    if (fragment != null) {
        // TODO: commit -> commitNow (support v24)
        fm.beginTransaction().remove(fragment).commit()
    }
}
