package org.sugr.gearshift.ui;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.util.Base64;
import org.sugr.gearshift.util.CheatSheet;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;
import java.util.List;

public class TorrentListActivity extends BaseTorrentActivity
        implements TorrentListFragment.Callbacks {

    public static final String ARG_FILE_URI = "torrent_file_uri";
    public static final String ARG_FILE_PATH = "torrent_file_path";
    public final static String ACTION_OPEN = "torrent_file_open_action";

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean twoPaneLayout;

    private boolean intentConsumed = false;
    private boolean newTorrentDialogVisible = false;
    private boolean hasNewIntent = false;

    private static final String STATE_INTENT_CONSUMED = "intent_consumed";
    private static final String STATE_CURRENT_PROFILE = "current_profile";

    private static final int BROWSE_REQUEST_CODE = 1;

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;

    private ValueAnimator detailSlideAnimator;

    private boolean detailPanelVisible;

    private int currentTorrentIndex = -1;

    private boolean altSpeed = false;

    private int expecting = 0;

    private static class Expecting {
        static int ALT_SPEED_ON = 1;
        static int ALT_SPEED_OFF = 1 << 1;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        G.DEBUG = prefs.getBoolean(G.PREF_DEBUG, false);

        setContentView(R.layout.activity_torrent_list);

        PreferenceManager.setDefaultValues(this, R.xml.general_preferences, false);
        PreferenceManager.setDefaultValues(this, R.xml.filters_preferences, true);
        PreferenceManager.setDefaultValues(this, R.xml.sort_preferences, true);

        if (findViewById(R.id.torrent_detail_panel) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-large and
            // res/values-sw600dp). If this view is present, then the
            // activity should be in two-pane mode.
            twoPaneLayout = true;

            // In two-pane mode, list items should be given the
            // 'activated' state when touched.
            ((TorrentListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.torrent_list))
                    .setActivateOnItemClick(true);

            final LinearLayout slidingLayout = (LinearLayout) findViewById(R.id.sliding_layout);
            final View detailPanel = findViewById(R.id.torrent_detail_panel);

            detailSlideAnimator = (ValueAnimator) AnimatorInflater.loadAnimator(this, R.anim.weight_animator);
            detailSlideAnimator.setDuration(getResources().getInteger(android.R.integer.config_shortAnimTime));
            detailSlideAnimator.setInterpolator(new DecelerateInterpolator());
            detailSlideAnimator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);

                    final FragmentManager fm = getSupportFragmentManager();
                    TorrentDetailFragment fragment = (TorrentDetailFragment) fm.findFragmentByTag(
                            G.DETAIL_FRAGMENT_TAG);
                    if (fragment == null) {
                        fragment = new TorrentDetailFragment();
                        fragment.setArguments(new Bundle());
                        fm.beginTransaction()
                                .replace(R.id.torrent_detail_container, fragment, G.DETAIL_FRAGMENT_TAG)
                                .commit();
                        fm.executePendingTransactions();
                    }

                    fragment.setCurrentTorrent(currentTorrentIndex);

                    G.logD("Opening the detail panel");
                    manager.setDetails(true);
                    new TorrentTask(TorrentListActivity.this, TorrentTask.Flags.UPDATE).execute();

                    fragment.onCreateOptionsMenu(menu, getMenuInflater());

                    Handler handler = new Handler();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            View bg=findViewById(R.id.torrent_detail_placeholder_background);
                            if (bg != null) {
                                bg.setVisibility(View.GONE);
                            }
                            View pager = findViewById(R.id.torrent_detail_pager);
                            pager.setVisibility(View.VISIBLE);
                            pager.animate().alpha((float) 1.0);
                        }
                    });
                }
            });
            detailSlideAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float value = (Float) animation.getAnimatedValue();
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                        detailPanel.getLayoutParams();

                    params.weight=value;
                    slidingLayout.requestLayout();
                }

            });
        }

        if (savedInstanceState == null) {
            setRefreshing(true, DataService.Requests.GET_TORRENTS);
        } else {
            if (savedInstanceState.containsKey(STATE_INTENT_CONSUMED)) {
                intentConsumed = savedInstanceState.getBoolean(STATE_INTENT_CONSUMED);
            }
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ContextThemeWrapper wrapper = new ContextThemeWrapper(
            getSupportActionBar().getThemedContext(), R.style.ToolbarControl);
        View.inflate(wrapper, R.layout.alt_speed_switch, toolbar);

        getWindow().setBackgroundDrawable(null);
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
            R.string.open_drawer, R.string.close_drawer) { };
        drawerLayout.setDrawerListener(drawerToggle);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED,
            findViewById(R.id.sliding_menu_frame));
        drawerToggle.setDrawerIndicatorEnabled(true);


        if (twoPaneLayout) {
            toggleRightPane(false);
        }

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(STATE_CURRENT_PROFILE)) {
                profile = savedInstanceState.getParcelable(STATE_CURRENT_PROFILE);
                if (profile != null) {
                    manager = new DataServiceManager(this, profile.getId())
                        .onRestoreInstanceState(savedInstanceState).startUpdating();
                    new SessionTask(this, SessionTask.Flags.START_TORRENT_TASK).execute();
                }
            }
        }

        SwitchCompat altSpeed = (SwitchCompat) findViewById(R.id.menu_alt_speed);

        if (altSpeed != null) {
            CheatSheet.setup(altSpeed, this);
            altSpeed.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    expecting&= ~(Expecting.ALT_SPEED_ON | Expecting.ALT_SPEED_OFF);
                    boolean altSpeed = TorrentListActivity.this.altSpeed;
                    if (altSpeed) {
                        setAltSpeed(false);
                        expecting|= Expecting.ALT_SPEED_OFF;
                    } else {
                        setAltSpeed(true);
                        expecting|= Expecting.ALT_SPEED_ON;
                    }
                    session.setAltSpeedLimitEnabled(!altSpeed);
                    manager.setSession(session, "alt-speed-enabled");
                }
            });
        }
    }

    @Override protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState();
    }

    @Override protected void onResume() {
        super.onResume();

        if (!newTorrentDialogVisible && hasNewIntent) {
            intentConsumed = false;

            /* Less than a minute ago */
            long now = new Date().getTime();
            if (now - lastServerActivity < 60000) {
                consumeIntent();
            }
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    /**
     * Callback method from {@link TorrentListFragment.Callbacks}
     * indicating that the item with the given ID was selected.
     */
    @Override
    public void onItemSelected(int position) {
        if (twoPaneLayout) {
            currentTorrentIndex = position;
            if (!toggleRightPane(true)) {
                TorrentDetailFragment fragment =
                    (TorrentDetailFragment) getSupportFragmentManager().findFragmentByTag(
                        G.DETAIL_FRAGMENT_TAG);
                if (fragment != null) {
                    fragment.setCurrentTorrent(currentTorrentIndex);
                }
            }
        } else {
            // In single-pane mode, simply start the detail activity
            // for the selected item ID.
            Intent detailIntent = new Intent(this, TorrentDetailActivity.class);
            detailIntent.putExtra(G.ARG_PAGE_POSITION, position);
            detailIntent.putExtra(G.ARG_PROFILE, profile);
            detailIntent.putExtra(G.ARG_SESSION, session);
            detailIntent.putExtra(G.ARG_LAST_SERVER_ACTIVITY, lastServerActivity);
            if (refreshing) {
                detailIntent.putExtra(G.ARG_REFRESH_TYPE, refreshType);
            }
            startActivity(detailIntent);

            overridePendingTransition(R.anim.slide_in_right, android.R.anim.fade_out);
        }
    }

    @Override public void onPageSelected(int position) {
        if (twoPaneLayout) {
            ((TorrentListFragment) getSupportFragmentManager()
             .findFragmentById(R.id.torrent_list))
                .getListView().setItemChecked(position, true);
        }
    }

    @Override public void onBackPressed() {
        TorrentListFragment fragment = ((TorrentListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.torrent_list));

        int position = fragment.getListView().getCheckedItemPosition();
        if (position == ListView.INVALID_POSITION) {
            super.onBackPressed();
        } else {
            toggleRightPane(false);
            fragment.getListView().setItemChecked(position, false);
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        this.menu = menu;

        getMenuInflater().inflate(R.menu.torrent_list_activity, menu);

        setSession(session);
        setAltSpeed(altSpeed);

        if (profile == null) {
            MenuItem item = menu.findItem(R.id.menu_refresh);

            item.setVisible(false);

            if (findViewById(R.id.swipe_container) != null) {
                findViewById(R.id.swipe_container).setEnabled(false);
            }
        } else {
            setRefreshing(refreshing, refreshType);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (drawerLayout.getDrawerLockMode(
                findViewById(R.id.sliding_menu_frame)
            ) == DrawerLayout.LOCK_MODE_UNLOCKED && drawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        Intent intent;
        switch(item.getItemId()) {
            case android.R.id.home:
                if (!twoPaneLayout) {
                    return super.onOptionsItemSelected(item);
                }

                TorrentListFragment fragment = ((TorrentListFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.torrent_list));

                int position = fragment.getListView().getCheckedItemPosition();
                if (position == ListView.INVALID_POSITION) {
                    return true;
                } else {
                    toggleRightPane(false);
                    fragment.getListView().setItemChecked(position, false);
                    return true;
                }
            case R.id.menu_refresh:
                manager.update();
                setRefreshing(true, DataService.Requests.GET_TORRENTS);
                return true;
            case R.id.menu_add_torrent:
                showAddTorrentDialog();
                break;
        }
        if (twoPaneLayout) {
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(G.DETAIL_FRAGMENT_TAG);
            if (fragment != null && fragment.onOptionsItemSelected(item)) {
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_INTENT_CONSUMED, intentConsumed);
        outState.putParcelable(STATE_CURRENT_PROFILE, profile);
    }

    @Override public void onNewIntent(Intent intent) {
        hasNewIntent = true;
        setIntent(intent);
    }

    @Override public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == BROWSE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                Uri uri = resultData.getData();

                new ReadTorrentDataTask().execute(uri);
                Toast.makeText(this, R.string.reading_torrent_file, Toast.LENGTH_SHORT).show();
            }
        }
    }

    public boolean isDetailPanelVisible() {
        return twoPaneLayout && detailPanelVisible;
    }

    private boolean toggleRightPane(boolean show) {
        if (!twoPaneLayout) return false;

        View pager = findViewById(R.id.torrent_detail_pager);
        if (show) {
            if (!detailPanelVisible) {
                detailPanelVisible = true;
                detailSlideAnimator.start();

                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                    findViewById(R.id.torrent_list).getLayoutParams();
                params.rightMargin = 0;

                return true;
            }
        } else {
            if (detailPanelVisible) {
                detailPanelVisible = false;
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                        findViewById(R.id.torrent_detail_panel).getLayoutParams();
                params.weight = 0;
                if (pager != null) {
                    pager.setAlpha(0);
                    pager.setVisibility(View.GONE);
                }
                int panelMargin = params.leftMargin;

                manager.setDetails(false);

                FragmentManager fm = getSupportFragmentManager();
                TorrentDetailFragment fragment = (TorrentDetailFragment) fm.findFragmentByTag(G.DETAIL_FRAGMENT_TAG);
                if (fragment != null) {
                    fragment.removeMenuEntries();
                }

                params = (LinearLayout.LayoutParams) findViewById(R.id.torrent_list).getLayoutParams();
                params.rightMargin = -1 * panelMargin;

                return true;
            }
        }
        return false;
    }

    @Override public void setSession(TransmissionSession session) {
        if (session == null) {
            boolean sessionRemoved = false;
            if (this.session != null) {
                invalidateOptionsMenu();
                sessionRemoved = true;
            }

            this.session = null;
            if (sessionRemoved) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(G.INTENT_SESSION_INVALIDATED).putExtra(G.ARG_SESSION_VALID, false));
            }
        } else {
            boolean initial = false;
            if (this.session == null) {
                invalidateOptionsMenu();
                initial = true;
            } else if (session.getRPCVersion() >= TransmissionSession.FREE_SPACE_METHOD_RPC_VERSION) {
                session.setDownloadDirFreeSpace(this.session.getDownloadDirFreeSpace());
            }

            this.session = session;

            if (initial) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(
                    new Intent(G.INTENT_SESSION_INVALIDATED).putExtra(G.ARG_SESSION_VALID, true));
            }

            if (initial && !intentConsumed && !newTorrentDialogVisible) {
                consumeIntent();
            }
        }
    }

    @Override protected boolean handleSuccessServiceBroadcast(String type, Intent intent) {
        if (manager == null) {
            return false;
        }

        int flags = TorrentTask.Flags.CONNECTED;
        switch (type) {
            case DataService.Requests.GET_SESSION:
                new SessionTask(TorrentListActivity.this, 0).execute();
                break;
            case DataService.Requests.SET_SESSION:
                manager.getSession();
                break;
            case DataService.Requests.GET_TORRENTS:
                boolean added = intent.getBooleanExtra(G.ARG_ADDED, false);
                boolean removed = intent.getBooleanExtra(G.ARG_REMOVED, false);
                boolean statusChanged = intent.getBooleanExtra(G.ARG_STATUS_CHANGED, false);
                boolean incomplete = intent.getBooleanExtra(G.ARG_INCOMPLETE_METADATA, false);

                if (added) {
                    flags |= TorrentTask.Flags.HAS_ADDED;
                }
                if (removed) {
                    flags |= TorrentTask.Flags.HAS_REMOVED;
                }
                if (statusChanged) {
                    flags |= TorrentTask.Flags.HAS_STATUS_CHANGED;
                }
                if (incomplete) {
                    flags |= TorrentTask.Flags.HAS_INCOMPLETE_METADATA;
                }

                new TorrentTask(TorrentListActivity.this, flags).execute();
                break;
            case DataService.Requests.ADD_TORRENT:
                manager.update();
                flags |= TorrentTask.Flags.HAS_ADDED | TorrentTask.Flags.HAS_INCOMPLETE_METADATA;
                new TorrentTask(TorrentListActivity.this, flags).execute();
                break;
            case DataService.Requests.REMOVE_TORRENT:
                manager.update();
                flags |= TorrentTask.Flags.HAS_REMOVED;
                new TorrentTask(TorrentListActivity.this, flags).execute();
                break;
            case DataService.Requests.SET_TORRENT_LOCATION:
                manager.update();
                flags |= TorrentTask.Flags.HAS_ADDED | TorrentTask.Flags.HAS_REMOVED;
                new TorrentTask(TorrentListActivity.this, flags).execute();
                break;
            case DataService.Requests.SET_TORRENT:
            case DataService.Requests.SET_TORRENT_ACTION:
                manager.update();
                flags |= TorrentTask.Flags.HAS_STATUS_CHANGED;
                new TorrentTask(TorrentListActivity.this, flags).execute();
                break;
            case DataService.Requests.GET_FREE_SPACE:
                long freeSpace = intent.getLongExtra(G.ARG_FREE_SPACE, 0);
                TransmissionSession session = getSession();
                if (session != null && freeSpace != 0) {
                    session.setDownloadDirFreeSpace(freeSpace);
                }
                break;
        }

        return true;
    }

    @Override protected boolean handleErrorServiceBroadcast(String type, int error, Intent intent) {
        expecting = 0;
        toggleRightPane(false);

        FragmentManager manager = getSupportFragmentManager();
        TorrentListFragment fragment = (TorrentListFragment) manager.findFragmentById(R.id.torrent_list);
        if (fragment != null) {
            fragment.notifyTorrentListChanged(null, error, false, false, false, false, false);
        }

        return true;
    }

    @Override protected void onSessionTaskPostExecute(TransmissionSession session) {
        if (hasNewIntent && session != null) {
            hasNewIntent = false;
            if (!intentConsumed && !newTorrentDialogVisible) {
                consumeIntent();
            }
        }
    }

    @Override protected void onTorrentTaskPostExecute(Cursor cursor, boolean added,
                                                      boolean removed, boolean statusChanged,
                                                      boolean incompleteMetadata, boolean connected) {
        if (session == null) {
            expecting = 0;
        } else {
            if (altSpeed == session.isAltSpeedLimitEnabled()) {
                expecting &= ~(Expecting.ALT_SPEED_ON | Expecting.ALT_SPEED_OFF);
            } else {
                if (expecting == 0
                    || (expecting & Expecting.ALT_SPEED_ON) > 0 && session.isAltSpeedLimitEnabled()
                    || (expecting & Expecting.ALT_SPEED_OFF) > 0 && !session.isAltSpeedLimitEnabled()) {
                    setAltSpeed(session.isAltSpeedLimitEnabled());
                    expecting &= ~(Expecting.ALT_SPEED_ON | Expecting.ALT_SPEED_OFF);
                }
            }
        }

        FragmentManager fm = getSupportFragmentManager();
        TorrentListFragment fragment = (TorrentListFragment) fm.findFragmentById(R.id.torrent_list);
        if (fragment != null) {
            long now = new Date().getTime();
            if (now - lastServerActivity < 30000) {
                connected = true;
            }
            fragment.notifyTorrentListChanged(cursor, 0, added, removed,
                statusChanged, incompleteMetadata, connected);
        }

        TorrentListMenuFragment menu = (TorrentListMenuFragment) fm.findFragmentById(R.id.torrent_list_menu);

        if (menu != null) {
            menu.notifyTorrentListChanged(cursor, 0, added, removed,
                statusChanged, incompleteMetadata, connected);
        }

        if (isDetailPanelVisible()) {
            TorrentDetailFragment detail = (TorrentDetailFragment) fm.findFragmentByTag(
                G.DETAIL_FRAGMENT_TAG);
            if (detail != null) {
                detail.notifyTorrentListChanged(cursor, 0, added, removed,
                    statusChanged, incompleteMetadata, connected);
            }
        }
    }

    @Override public void setProfile(TransmissionProfile profile) {
        super.setProfile(profile);

        toggleRightPane(false);
    }

    @Override protected void setProfiles(List<TransmissionProfile> profileList) {
        super.setProfiles(profileList);

        FragmentManager fm = getSupportFragmentManager();
        TorrentListMenuFragment menu = (TorrentListMenuFragment) fm.findFragmentById(R.id.torrent_list_menu);

        if (menu != null) {
            menu.notifyTransmissionProfileListChanged(getProfiles());
        }

        if (profiles.size() == 0) {
            TorrentListFragment list = (TorrentListFragment) fm.findFragmentById(R.id.torrent_list);
            if (list != null) {
                list.setEmptyMessage(R.string.no_profiles_empty_list);
            }
        }
    }

    private void setAltSpeed(boolean alt) {
        if (menu == null) {
            return;
        }
        SwitchCompat altSpeed = (SwitchCompat) findViewById(R.id.menu_alt_speed);
        MenuItem addTorrent = menu.findItem(R.id.menu_add_torrent);
        if (altSpeed == null || addTorrent == null) {
            return;
        }

        this.altSpeed = alt;

        if (session == null) {
            altSpeed.setVisibility(View.GONE);
            addTorrent.setVisible(false);
        } else {
            altSpeed.setVisibility(View.VISIBLE);
            addTorrent.setVisible(true);
            altSpeed.setChecked(this.altSpeed);
        }
    }

    private void consumeIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();

        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
                && (Intent.ACTION_VIEW.equals(action) || ACTION_OPEN.equals(action))) {

            Uri data = intent.getData();
            String fileURI = intent.getStringExtra(ARG_FILE_URI);
            String filePath = intent.getStringExtra(ARG_FILE_PATH);

            showNewTorrentDialog(data, fileURI, filePath, null);
        } else {
            intentConsumed = true;
        }
    }

    private void showNewTorrentDialog(final Uri data, final String fileURI,
                                      final String filePath, final Uri documentUri) {

        newTorrentDialogVisible = true;

        int title;
        DialogInterface.OnClickListener okListener;

        if (data != null && data.getScheme().equals("magnet")) {
            title = R.string.add_new_magnet;
            okListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    if (manager == null || session == null) {
                        newTorrentDialogVisible = false;
                        return;
                    }

                    Spinner location = (Spinner) ((AlertDialog) dialog).findViewById(R.id.location_choice);
                    EditText entry = (EditText) ((AlertDialog) dialog).findViewById(R.id.location_entry);
                    CheckBox paused = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.start_paused);
                    String dir;

                    if (location.getVisibility() != View.GONE) {
                        dir = (String) location.getSelectedItem();
                    } else {
                        dir = entry.getText().toString();
                    }

                    if (TextUtils.isEmpty(dir)) {
                        dir = session.getDownloadDir();
                    }
                    manager.addTorrent(
                        Uri.decode(data.toString()), null, dir, paused.isChecked(), null, null);

                    setRefreshing(true, DataService.Requests.ADD_TORRENT);
                    intentConsumed = true;
                    newTorrentDialogVisible = false;
                }
            };
        } else if (fileURI != null) {
            title = R.string.add_new_torrent;
            okListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(final DialogInterface dialog, int which) {
                    if (manager == null) {
                        newTorrentDialogVisible = false;
                        return;
                    }

                    Spinner location = (Spinner) ((AlertDialog) dialog).findViewById(R.id.location_choice);
                    EditText entry = (EditText) ((AlertDialog) dialog).findViewById(R.id.location_entry);
                    CheckBox paused = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.start_paused);
                    CheckBox deleteLocal = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.delete_local);
                    BufferedReader reader = null;

                    try {
                        String dir;
                        if (location.getVisibility() != View.GONE) {
                            dir = (String) location.getSelectedItem();
                        } else {
                            dir = entry.getText().toString();
                        }

                        File file = new File(new URI(fileURI));

                        reader = new BufferedReader(new FileReader(file));
                        StringBuilder filedata = new StringBuilder();
                        String line;

                        while ((line = reader.readLine()) != null) {
                            filedata.append(line).append("\n");
                        }

                        if (!file.delete()) {
                            Toast.makeText(TorrentListActivity.this,
                                R.string.error_deleting_torrent_file, Toast.LENGTH_SHORT).show();
                        }

                        String path = filePath;
                        Uri uri = documentUri;
                        if (!deleteLocal.isChecked()) {
                            path = null;
                            uri = null;
                        }

                        manager.addTorrent(null, filedata.toString(), dir, paused.isChecked(),
                            path, uri);

                        setRefreshing(true, DataService.Requests.ADD_TORRENT);
                    } catch (Exception e) {
                        Toast.makeText(TorrentListActivity.this,
                            R.string.error_reading_torrent_file, Toast.LENGTH_SHORT).show();
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    intentConsumed = true;
                    newTorrentDialogVisible = false;
                }
            };
        } else {
            return;
        }

        AlertDialog dialog = locationDialogHelper.showDialog(R.layout.new_torrent_dialog,
            title, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    intentConsumed = true;
                    newTorrentDialogVisible = false;
                }
            }, okListener
        );

        if (dialog == null) {
            return;
        }

        CheckBox startPaused = (CheckBox) dialog.findViewById(R.id.start_paused);
        startPaused.setChecked(profile != null && profile.getStartPaused());

        CheckBox deleteLocal = (CheckBox) dialog.findViewById(R.id.delete_local);
        deleteLocal.setChecked(profile != null && profile.getDeleteLocal());

        if (fileURI != null) {
            deleteLocal.setVisibility(View.VISIBLE);
        }
    }

    private void showAddTorrentDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.add_torrent_dialog, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setCancelable(true)
            .setView(view)
            .setTitle(R.string.add_torrent)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    EditText magnet = (EditText) ((AlertDialog) dialog).findViewById(R.id.magnet_link);
                    Uri data = Uri.parse(magnet.getText().toString());

                    if (data != null && "magnet".equals(data.getScheme())) {
                        showNewTorrentDialog(data, null, null, null);
                    } else {
                        Toast.makeText(TorrentListActivity.this,
                            R.string.invalid_magnet_link, Toast.LENGTH_SHORT).show();
                    }
                }
            });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            builder.setNeutralButton(R.string.browse, new DialogInterface.OnClickListener() {
                @TargetApi(Build.VERSION_CODES.KITKAT)
                @Override public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);

                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/x-bittorrent");

                    startActivityForResult(intent, BROWSE_REQUEST_CODE);
                }
            });
        }

        AlertDialog dialog = builder.create();
        dialog.show();

    }

    private class ReadTorrentDataTask extends AsyncTask<Uri, Void, ReadTorrentDataTask.TaskData> {
        public class TaskData {
            public File file;
            public Uri uri;
        }

        @Override protected TaskData doInBackground(Uri... params) {
            if (params.length == 0) {
                return null;
            }

            Uri uri = params[0];
            ContentResolver cr = getContentResolver();
            InputStream stream = null;
            Base64.InputStream base64 = null;

            try {
                stream = cr.openInputStream(uri);

                base64 = new Base64.InputStream(stream, Base64.ENCODE | Base64.DO_BREAK_LINES);
                StringBuilder fileContent = new StringBuilder("");
                int ch;

                while( (ch = base64.read()) != -1)
                    fileContent.append((char)ch);

                File file = new File(TorrentListActivity.this.getCacheDir(), "torrentdata");

                if (file.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }

                if (file.createNewFile()) {
                    BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                    bw.append(fileContent);
                    bw.close();

                    G.logD("Torrent file uri: " + uri.toString());

                    TaskData data = new TaskData();
                    data.file = file;
                    data.uri = uri;
                    return data;
                } else {
                    return  null;
                }
            } catch (Exception e) {
                G.logE("Error while reading the torrent file", e);
                return null;
            } finally {
                try {
                    if (base64 != null)
                        base64.close();
                    if (stream != null)
                        stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override protected void onPostExecute(TaskData data) {
            if (data == null) {
                Toast.makeText(TorrentListActivity.this,
                    R.string.error_reading_torrent_file, Toast.LENGTH_SHORT).show();
            } else {
                showNewTorrentDialog(null, data.file.toURI().toString(), null, data.uri);
            }
        }
    }
}
