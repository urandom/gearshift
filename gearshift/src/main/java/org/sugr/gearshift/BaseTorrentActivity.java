package org.sugr.gearshift;

import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;

import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.service.DataServiceManagerInterface;

public abstract class BaseTorrentActivity extends FragmentActivity
    implements TransmissionSessionInterface, DataServiceManagerInterface,
               TorrentDetailFragment.PagerCallbacks {

    protected TransmissionProfile profile;
    protected TransmissionSession session;
    protected DataServiceManager manager;

    protected boolean refreshing = false;
    protected String refreshType;

    protected Menu menu;

    @Override public TransmissionProfile getProfile() {
        return profile;
    }

    @Override public TransmissionSession getSession() {
        return session;
    }

    @Override public void setSession(TransmissionSession session) {
        this.session = session;
    }

    @Override public void setRefreshing(boolean refreshing, String type) {
        if (!refreshing && refreshType != null && !refreshType.equals(type)) {
            return;
        }

        this.refreshing = refreshing;
        refreshType = refreshing ? type : null;
        if (menu == null) {
            return;
        }

        MenuItem item = menu.findItem(R.id.menu_refresh);
        if (this.refreshing)
            item.setActionView(R.layout.action_progress_bar);
        else
            item.setActionView(null);
    }

    @Override public DataServiceManager getDataServiceManager() {
        return manager;
    }
}
