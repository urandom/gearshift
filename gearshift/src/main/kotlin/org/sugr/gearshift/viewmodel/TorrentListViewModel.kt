package org.sugr.gearshift.viewmodel

import io.reactivex.Observable
import org.sugr.gearshift.Logger
import org.sugr.gearshift.viewmodel.api.Api

class TorrentListViewModel(tag: String, log: Logger,
                           private val apiObservable: Observable<Api>,
                           private val activityLifecycle: Observable<ActivityLifecycle>):
        RetainedViewModel<TorrentListViewModel.Consumer>(tag, log) {

    interface Consumer {

    }

}

