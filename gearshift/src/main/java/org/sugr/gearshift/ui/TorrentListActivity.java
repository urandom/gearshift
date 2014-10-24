package org.sugr.gearshift.ui;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
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
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.ui.loader.TransmissionProfileSupportLoader;
import org.sugr.gearshift.util.Base64;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Date;

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

    private TransmissionProfileListAdapter profileAdapter;

    private boolean altSpeed = false;

    private int expecting = 0;

    private static class Expecting {
        static int ALT_SPEED_ON = 1;
        static int ALT_SPEED_OFF = 1 << 1;
    }

    private LoaderManager.LoaderCallbacks<TransmissionProfile[]> profileLoaderCallbacks = new LoaderManager.LoaderCallbacks<TransmissionProfile[]>() {
        @Override
        public android.support.v4.content.Loader<TransmissionProfile[]> onCreateLoader(
            int id, Bundle args) {
            return new TransmissionProfileSupportLoader(TorrentListActivity.this);
        }

        @Override
        public void onLoadFinished(
            android.support.v4.content.Loader<TransmissionProfile[]> loader,
            TransmissionProfile[] profiles) {

            profile = null;
            profileAdapter.setNotifyOnChange(false);
            profileAdapter.clear();
            if (profiles.length > 0) {
                profileAdapter.addAll(profiles);
            } else {
                profileAdapter.add(TransmissionProfileListAdapter.EMPTY_PROFILE);
                TransmissionProfile.setCurrentProfile(null,
                    PreferenceManager.getDefaultSharedPreferences(TorrentListActivity.this));
                setRefreshing(false, DataService.Requests.GET_TORRENTS);
            }

            String currentId = TransmissionProfile.getCurrentProfileId(
                PreferenceManager.getDefaultSharedPreferences(TorrentListActivity.this));

            int index = 0;
            for (TransmissionProfile prof : profiles) {
                if (prof.getId().equals(currentId)) {
                    setProfile(prof);
                    ActionBar actionBar = getActionBar();
                    if (actionBar != null) {
                        actionBar.setSelectedNavigationItem(index);
                    }
                    break;
                }
                index++;
            }

            if (profile == null) {
                if (profiles.length > 0) {
                    setProfile(profiles[0]);
                } else {
                    setProfile(null);
                    /* TODO: should display the message that the user hasn't created a profile yet */
                }
                TransmissionProfile.setCurrentProfile(profile,
                    PreferenceManager.getDefaultSharedPreferences(TorrentListActivity.this));
            }

            profileAdapter.notifyDataSetChanged();
        }

        @Override
        public void onLoaderReset(
            android.support.v4.content.Loader<TransmissionProfile[]> loader) {
            profileAdapter.clear();
        }

    };

    /* The callback will get garbage collected if its a mere anon class */
    private SharedPreferences.OnSharedPreferenceChangeListener profileChangeListener =
        new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if (profile == null) return;

                if (!key.endsWith(profile.getId())) return;

                profile.load();

                TransmissionProfile.setCurrentProfile(profile,
                    PreferenceManager.getDefaultSharedPreferences(TorrentListActivity.this));
                setProfile(profile);
            }
        };

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
            detailSlideAnimator.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
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

                @Override
                public void onAnimationCancel(Animator animation) {
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

        getWindow().setBackgroundDrawable(null);
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
            findViewById(R.id.sliding_menu_frame));
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
            R.string.open_drawer, R.string.close_drawer) { };
        drawerToggle.setDrawerIndicatorEnabled(false);
        drawerLayout.setDrawerListener(drawerToggle);

        if (twoPaneLayout) {
            toggleRightPane(false);
        }

        ActionBar actionBar = null;//getActionBar();
        if (actionBar != null) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

            profileAdapter = new TransmissionProfileListAdapter(this);

            actionBar.setListNavigationCallbacks(profileAdapter, new ActionBar.OnNavigationListener() {
                @Override
                public boolean onNavigationItemSelected(int pos, long id) {
                    TransmissionProfile profile = profileAdapter.getItem(pos);
                    if (profile != TransmissionProfileListAdapter.EMPTY_PROFILE) {
                        SharedPreferences prefs = getSharedPreferences(
                            TransmissionProfile.getPreferencesName(),
                            Activity.MODE_PRIVATE);

                        boolean newProfile = getProfile() == null;

                        if (!newProfile) {
                            if (getProfile().getId().equals(profile.getId())) {
                                return false;
                            }
                        }

                        TransmissionProfile.setCurrentProfile(profile,
                            PreferenceManager.getDefaultSharedPreferences(TorrentListActivity.this));
                        setProfile(profile);

                        if (prefs != null && newProfile)
                            prefs.registerOnSharedPreferenceChangeListener(profileChangeListener);

                        setRefreshing(true, DataService.Requests.GET_TORRENTS);
                    }

                    return false;
                }
            });

            actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
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

        //getSupportLoaderManager().initLoader(G.PROFILES_LOADER_ID, null, profileLoaderCallbacks);
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
            case R.id.menu_alt_speed:
                expecting&= ~(Expecting.ALT_SPEED_ON | Expecting.ALT_SPEED_OFF);
                if (altSpeed) {
                    setAltSpeed(false);
                    expecting|= Expecting.ALT_SPEED_OFF;
                } else {
                    setAltSpeed(true);
                    expecting|= Expecting.ALT_SPEED_ON;
                }
                session.setAltSpeedLimitEnabled(altSpeed);
                manager.setSession(session, "alt-speed-enabled");
                return true;
            case R.id.menu_refresh:
                manager.update();
                setRefreshing(true, DataService.Requests.GET_TORRENTS);
                return true;
            case R.id.menu_add_torrent:
                showAddTorrentDialog();
                break;
            case R.id.menu_session_settings:
                intent = new Intent(this, TransmissionSessionActivity.class);
                intent.putExtra(G.ARG_PROFILE, profile);
                intent.putExtra(G.ARG_SESSION, session);
                startActivity(intent);
                return true;
            case R.id.menu_settings:
                intent = new Intent(this, SettingsActivity.class);
                if (session != null) {
                    ArrayList<String> directories = new ArrayList<>(session.getDownloadDirectories());
                    directories.remove(session.getDownloadDir());
                    intent.putExtra(G.ARG_DIRECTORIES, directories);
                }
                if (profile != null) {
                    intent.putExtra(G.ARG_PROFILE_ID, profile.getId());
                }
                startActivity(intent);
                return true;
            case R.id.menu_about:
                intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
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
                manager.setDetails(false);

                FragmentManager fm = getSupportFragmentManager();
                TorrentDetailFragment fragment = (TorrentDetailFragment) fm.findFragmentByTag(G.DETAIL_FRAGMENT_TAG);
                if (fragment != null) {
                    fragment.removeMenuEntries();
                }

                return true;
            }
        }
        return false;
    }

    public void setProfile(TransmissionProfile profile) {
        if (this.profile == profile
            || (this.profile != null && profile != null && profile.getId().equals(this.profile.getId()))) {
            return;
        }
        this.profile = profile;
        toggleRightPane(false);

        if (manager != null) {
            manager.reset();
        }

        if (menu != null) {
            MenuItem item = menu.findItem(R.id.menu_refresh);
            item.setVisible(profile != null);

            if (findViewById(R.id.swipe_container) != null) {
                findViewById(R.id.swipe_container).setEnabled(false);
            }
        }

        if (profile != null) {
            manager = new DataServiceManager(this, profile.getId()).startUpdating();
            new SessionTask(this, SessionTask.Flags.START_TORRENT_TASK).execute();
        }
    }

    @Override public void setSession(TransmissionSession session) {
        if (session == null) {
            if (this.session != null) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
                    findViewById(R.id.sliding_menu_frame));
                drawerToggle.setDrawerIndicatorEnabled(false);
                getActionBar().setDisplayHomeAsUpEnabled(false);

                invalidateOptionsMenu();
            }

            this.session = null;
            if (menu != null) {
                menu.findItem(R.id.menu_session_settings).setVisible(false);
            }
        } else {
            boolean initial = false;
            if (this.session == null) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED,
                    findViewById(R.id.sliding_menu_frame));
                drawerToggle.setDrawerIndicatorEnabled(true);
                getActionBar().setDisplayHomeAsUpEnabled(true);

                invalidateOptionsMenu();
                initial = true;
            } else if (session.getRPCVersion() >= TransmissionSession.FREE_SPACE_METHOD_RPC_VERSION) {
                session.setDownloadDirFreeSpace(this.session.getDownloadDirFreeSpace());
            }

            this.session = session;
            if (menu != null) {
                menu.findItem(R.id.menu_session_settings).setVisible(true);
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

    private void setAltSpeed(boolean alt) {
        if (menu == null) {
            return;
        }
        MenuItem altSpeed = menu.findItem(R.id.menu_alt_speed);
        MenuItem addTorrent = menu.findItem(R.id.menu_add_torrent);
        if (altSpeed == null || addTorrent == null) {
            return;
        }

        this.altSpeed = alt;

        if (session == null) {
            altSpeed.setVisible(false);
            addTorrent.setVisible(false);
        } else {
            altSpeed.setVisible(true);
            addTorrent.setVisible(true);
            if (this.altSpeed) {
                altSpeed.setIcon(R.drawable.ic_action_data_usage_on);
                altSpeed.setTitle(R.string.alt_speed_label_off);
            } else {
                altSpeed.setIcon(R.drawable.ic_action_data_usage);
                altSpeed.setTitle(R.string.alt_speed_label_on);
            }
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

        if (manager == null || session == null) {
            return;
        }

        newTorrentDialogVisible = true;

        int title;
        DialogInterface.OnClickListener okListener;

        if (data != null && data.getScheme().equals("magnet")) {
            title = R.string.add_new_magnet;
            okListener = new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
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
                    manager.addTorrent(data.toString(), null, dir, paused.isChecked(), null, null);

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

    private static class TransmissionProfileListAdapter extends ArrayAdapter<TransmissionProfile> {
        public static final TransmissionProfile EMPTY_PROFILE = new TransmissionProfile(null, null);

        public TransmissionProfileListAdapter(Context context) {
            super(context, 0);

            add(EMPTY_PROFILE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            TransmissionProfile profile = getItem(position);

            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_profile_selector, null);
            }

            TextView name = (TextView) rowView.findViewById(R.id.name);
            TextView summary = (TextView) rowView.findViewById(R.id.summary);

            if (profile == EMPTY_PROFILE) {
                name.setText(R.string.no_profiles);
                if (summary != null)
                    summary.setText(R.string.create_profile_in_settings);
            } else {
                name.setText(profile.getName());
                if (summary != null)
                    summary.setText((profile.getUsername().length() > 0 ? profile.getUsername() + "@" : "")
                        + profile.getHost() + ":" + profile.getPort());
            }

            return rowView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;

            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_profile_selector_dropdown, null);
            }

            return getView(position, rowView, parent);
        }
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
