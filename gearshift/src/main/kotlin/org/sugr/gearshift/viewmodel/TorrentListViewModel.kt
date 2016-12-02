package org.sugr.gearshift.viewmodel

import io.reactivex.Flowable

class TorrentListViewModel(tag: String, lifecycle: Flowable<ActivityLifecycle>):
        RetainedViewModel<TorrentListViewModel.Consumer>(tag) {

    interface Consumer {

    }
}

