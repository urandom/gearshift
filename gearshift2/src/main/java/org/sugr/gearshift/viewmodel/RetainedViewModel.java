package org.sugr.gearshift.viewmodel;

import android.content.SharedPreferences;
import android.support.annotation.Nullable;

import com.f2prateek.rx.preferences.RxSharedPreferences;

import rx.Observable;
import rx.subjects.PublishSubject;

public class RetainedViewModel<T> {
    protected final RxSharedPreferences prefs;
    @Nullable protected T consumer;

    private PublishSubject<Void> unbindSubject = PublishSubject.create();
    private PublishSubject<Void> destroySubject = PublishSubject.create();

    public RetainedViewModel(SharedPreferences prefs) {
        this.prefs = RxSharedPreferences.create(prefs);
    }

    public void bind(T consumer) {
        this.consumer = consumer;
    }

    public void unbind() {
        unbindSubject.onNext(null);

        consumer = null;
    }

    public void onDestroy() {
        destroySubject.onNext(null);
    }

    // Useful for stopping observable emission when an unbind happens
    public <O> Observable.Transformer<O, O> takeUntilUnbind() {
        return o -> o.takeUntil(unbindSubject);
    }

    // Similar to takeUntilUnbind, but for onDestroy
    public <O> Observable.Transformer<O, O> takeUntilDestroy() {
        return o -> o.takeUntil(destroySubject);
    }
}
