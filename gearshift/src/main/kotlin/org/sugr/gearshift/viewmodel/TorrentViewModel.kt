package org.sugr.gearshift.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.databinding.ObservableBoolean
import android.databinding.ObservableField
import android.databinding.ObservableInt
import org.sugr.gearshift.Logger
import org.sugr.gearshift.model.Torrent

class TorrentViewModel(log: Logger, ctx: Context, prefs: SharedPreferences) {
    val isDirectory = ObservableBoolean(false)
    val isChecked = ObservableBoolean(false)
    val downloadProgress = ObservableInt(0)
    val uploadProgress = ObservableInt(0)
    val name = ObservableField("")
    val traffic = ObservableField("")
    val status = ObservableField("")
    val error = ObservableField("")
    val hasError = ObservableBoolean(false)

    interface Consumer {

    }

    fun updateTorrent(torrent: Torrent) {

    }

    fun destroy() {
    }
}
