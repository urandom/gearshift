package org.sugr.gearshift.viewmodel

import io.reactivex.Flowable
import org.sugr.gearshift.App

class TorrentListViewModel(tag: String, app: App, lifecycle: Flowable<ActivityLifecycle>):
        RetainedViewModel<TorrentListViewModel.Consumer>(tag, app) {

    interface Consumer {

    }
}

