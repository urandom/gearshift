package org.sugr.gearshift.viewmodel

import android.content.SharedPreferences
import android.support.design.widget.NavigationView
import android.view.MenuItem
import org.sugr.gearshift.logD
import org.sugr.gearshift.model.loadProfiles

class MainNavigationViewModel(tag: String, prefs: SharedPreferences) :
        RetainedViewModel<MainNavigationViewModel.Consumer>(tag, prefs) {

    val navigationListener = object : NavigationView.OnNavigationItemSelectedListener {
        override fun onNavigationItemSelected(item: MenuItem?): Boolean {
            logD("Navigation item ${item?.title}")

            consumer?.closeDrawer()

            return true
        }

    }

    var firstTimeProfile = true

    interface Consumer {
        fun closeDrawer()
        fun createProfile()
    }

    override fun bind(consumer: Consumer) {
        super.bind(consumer)

        var profiles = loadProfiles()
        if (profiles.size == 0 && firstTimeProfile) {
            firstTimeProfile = false
            consumer.createProfile()
        }
    }
}
