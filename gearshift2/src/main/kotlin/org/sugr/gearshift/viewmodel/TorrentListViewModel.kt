package org.sugr.gearshift.viewmodel

import android.content.SharedPreferences

class TorrentListViewModel(tag: String, prefs: SharedPreferences):
        RetainedViewModel<TorrentListViewModel.Consumer>(tag, prefs) {
    interface Consumer {

    }
}

