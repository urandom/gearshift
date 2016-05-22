package org.sugr.gearshift.viewmodel.databinding;

import android.databinding.Observable;

import java.util.concurrent.TimeUnit;

import rx.Subscription;
import rx.functions.Action1;
import rx.subjects.PublishSubject;
import rx.subjects.Subject;

public class PropertyChangedDebouncer extends Observable.OnPropertyChangedCallback {
    private final Subject<Observable, Observable> subject = PublishSubject.create();

    @Override
    public void onPropertyChanged(Observable observable, int i) {
        subject.onNext(observable);
    }

    public Subscription subscribe(Action1<Observable> onNext) {
        return debouncer().subscribe(onNext);
    }

    public Subscription subscribe(Action1<Observable> onNext, Action1<Throwable> onError) {
        return debouncer().subscribe(onNext, onError);
    }

    private rx.Observable<Observable> debouncer() {
        return subject.debounce(300, TimeUnit.MILLISECONDS);
    }
}
