package org.sugr.gearshift.viewmodel

import android.content.SharedPreferences

class TorrentViewModel(prefs: SharedPreferences) : RetainedViewModel<TorrentViewModel.Consumer>(prefs) {
    interface Consumer {

    }
}
