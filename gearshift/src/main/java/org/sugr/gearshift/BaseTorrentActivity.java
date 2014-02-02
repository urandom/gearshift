package org.sugr.gearshift;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.view.Menu;
import android.view.MenuItem;

import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.service.DataServiceManagerInterface;

public abstract class BaseTorrentActivity extends FragmentActivity
    implements TransmissionSessionInterface, DataServiceManagerInterface,
               LocationDialogHelperInterface,
               TorrentDetailFragment.PagerCallbacks {

    protected TransmissionProfile profile;
    protected TransmissionSession session;
    protected DataServiceManager manager;

    protected boolean refreshing = false;
    protected String refreshType;

    protected Menu menu;

    protected BroadcastReceiver serviceReceiver;

    protected LocationDialogHelper locationDialogHelper;

    @Override protected void onCreate(Bundle savedInstanceState) {
        locationDialogHelper = new LocationDialogHelper(this);

        super.onCreate(savedInstanceState);
    }

    @Override protected void onResume() {
        super.onResume();

        if (profile != null && manager == null) {
            manager = new DataServiceManager(this, profile.getId()).startUpdating();
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            serviceReceiver, new IntentFilter(G.INTENT_SERVICE_ACTION_COMPLETE));

        GearShiftApplication.setActivityVisible(true);
    }

    @Override protected void onPause() {
        super.onPause();

        if (manager != null) {
            manager.reset();
            manager = null;
        }

        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver);

        GearShiftApplication.setActivityVisible(false);

        locationDialogHelper.reset();
    }

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

    @Override public LocationDialogHelper getLocationDialogHelper() {
        return locationDialogHelper;
    }
}
