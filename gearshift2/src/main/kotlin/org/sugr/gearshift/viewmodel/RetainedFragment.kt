package org.sugr.gearshift.viewmodel

import android.content.SharedPreferences
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import org.sugr.gearshift.defaultPreferences

class RetainedFragment<VM : RetainedViewModel<*>> : Fragment() {
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

inline fun <reified VM : RetainedViewModel<*>> viewModelFrom(fm: FragmentManager,
                                                             tag: String = VM::class.java.toString(),
                                                             factory: (tag: String, prefs: SharedPreferences) -> VM): VM {
    var fragment = fm.findFragmentByTag(tag) as? RetainedFragment<VM>

    if (fragment == null) {
        fragment = RetainedFragment<VM>()
        fragment.viewModel = factory(tag, defaultPreferences())

        fm.beginTransaction().add(fragment, tag).commitNow()
    }

    return fragment.viewModel as VM
}

fun <VM : RetainedViewModel<*>> destroyViewModel(fm: FragmentManager,
                                                    viewModel: VM) {

    val fragment = fm.findFragmentByTag(viewModel.tag) as? RetainedFragment<*>

    if (fragment != null) {
        fm.beginTransaction().remove(fragment).commitNow()
    }
}
