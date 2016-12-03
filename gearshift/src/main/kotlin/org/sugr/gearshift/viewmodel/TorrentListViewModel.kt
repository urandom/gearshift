package org.sugr.gearshift.viewmodel

import io.reactivex.Observable

class TorrentListViewModel(tag: String, lifecycle: Observable<ActivityLifecycle>):
        RetainedViewModel<TorrentListViewModel.Consumer>(tag) {

    interface Consumer {

    }
}

