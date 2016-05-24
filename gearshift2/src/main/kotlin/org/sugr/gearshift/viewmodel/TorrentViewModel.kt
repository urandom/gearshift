package org.sugr.gearshift.viewmodel

import android.content.SharedPreferences
import android.support.design.widget.NavigationView
import android.support.v7.widget.Toolbar
import android.view.MenuItem
import org.sugr.gearshift.logD
import org.sugr.gearshift.model.loadProfiles

class TorrentViewModel(prefs: SharedPreferences) : RetainedViewModel<TorrentViewModel.Consumer>(prefs) {
    val navigationListener = object : NavigationView.OnNavigationItemSelectedListener {
        override fun onNavigationItemSelected(item: MenuItem?): Boolean {
            logD("Navigation item ${item?.title}")

            consumer?.closeDrawer()

            return true
        }

    }

    val toolbarListener = object : Toolbar.OnMenuItemClickListener {
        override fun onMenuItemClick(item: MenuItem?): Boolean {
            logD("Toolbar item ${item?.itemId}")
            return true
        }

    }

    interface Consumer {
        fun closeDrawer()
    }

    init {
        val profiles = loadProfiles()
    }
}
