package org.sugr.gearshift.viewmodel;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.sugr.gearshift.App;

public class RetainedFragment<VM extends RetainedViewModel<T>, T> extends Fragment {
    private VM viewModel;

    // Interface for creating a view model with a lambda
    public interface Factory<VM extends RetainedViewModel<T>, T> {
        VM create(SharedPreferences prefs);
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setRetainInstance(true);
    }

    @Override public void onDestroy() {
        super.onDestroy();

        if (viewModel != null) {
            viewModel.onDestroy();
        }
    }

    public void setViewModel(VM viewModel) {
        this.viewModel = viewModel;
    }

    public VM getViewModel() {
        return viewModel;
    }

    @SuppressWarnings("unchecked")
    public static <VM extends RetainedViewModel<T>, T> VM getViewModel(FragmentManager fm,
                                                                       String tag,
                                                                       Factory<VM, T> factory) {

        RetainedFragment<VM, T> fragment =
                (RetainedFragment<VM, T>) fm.findFragmentByTag(tag);

        if (fragment == null) {
            fragment = new RetainedFragment<>();
            fragment.setViewModel(factory.create(App.defaultPreferences()));

            // TODO: commit -> commitNow (support v24)
            fm.beginTransaction().add(fragment, tag).commit();
        }

        return fragment.getViewModel();
    }
}