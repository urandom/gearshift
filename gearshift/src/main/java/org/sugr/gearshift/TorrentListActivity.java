package org.sugr.gearshift;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;
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

    private ServiceReceiver serviceReceiver;

    private boolean intentConsumed = false;
    private boolean newTorrentDialogVisible = false;
    private boolean hasNewIntent = false;
    private boolean hasFatalError = false;

    private static final String STATE_INTENT_CONSUMED = "intent_consumed";
    private static final String STATE_LOCATION_POSITION = "location_position";
    private static final String STATE_CURRENT_PROFILE = "current_profile";
    private static final String STATE_FATAL_ERROR = "fatal_error";

    private static final int BROWSE_REQUEST_CODE = 1;

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;

    private ValueAnimator detailSlideAnimator;

    private boolean detailPanelVisible;

    private int locationPosition = AdapterView.INVALID_POSITION;

    private int currentTorrentIndex = -1;

    private TransmissionProfileListAdapter profileAdapter;

    private boolean altSpeed = false;

    private boolean preventRefreshIndicator;

    private int expecting = 0;

    private static class Expecting {
        static int ALT_SPEED_ON = 1;
        static int ALT_SPEED_OFF = 1 << 1;
    }

    private LoaderManager.LoaderCallbacks<TransmissionProfile[]> profileLoaderCallbacks= new LoaderManager.LoaderCallbacks<TransmissionProfile[]>() {
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
            profileAdapter.clear();
            if (profiles.length > 0) {
                profileAdapter.addAll(profiles);
            } else {
                profileAdapter.add(TransmissionProfileListAdapter.EMPTY_PROFILE);
                TransmissionProfile.setCurrentProfile(null, TorrentListActivity.this);
                setRefreshing(false, DataService.Requests.GET_TORRENTS);
            }

            String currentId = TransmissionProfile.getCurrentProfileId(TorrentListActivity.this);
            int index = 0;
            for (TransmissionProfile prof : profiles) {
                if (prof.getId().equals(currentId)) {
                    ActionBar actionBar = getActionBar();
                    if (actionBar != null)
                        actionBar.setSelectedNavigationItem(index);
                    setProfile(prof);
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
            } else {
                /* The torrents might be loaded before the navigation
                 * callback fires, which will cause the refresh indicator to
                 * appear until the next server request */
                preventRefreshIndicator = true;
            }

            TransmissionProfile.setCurrentProfile(profile, TorrentListActivity.this);
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

                TransmissionProfile.setCurrentProfile(profile, TorrentListActivity.this);
                setProfile(profile);
            }
        };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        serviceReceiver = new ServiceReceiver();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        G.DEBUG = prefs.getBoolean(G.PREF_DEBUG, false);

        setContentView(R.layout.activity_torrent_list);

        PreferenceManager.setDefaultValues(this, R.xml.general_preferences, false);
        PreferenceManager.setDefaultValues(this, R.xml.sort_preferences, false);

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
            if (savedInstanceState.containsKey(STATE_LOCATION_POSITION)) {
                locationPosition = savedInstanceState.getInt(STATE_LOCATION_POSITION);
            }
        }

        getWindow().setBackgroundDrawable(null);
        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
            findViewById(R.id.sliding_menu_frame));
        drawerToggle = new ActionBarDrawerToggle(this, drawerLayout, R.drawable.ic_drawer,
                R.string.open_drawer, R.string.close_drawer) { };
        drawerLayout.setDrawerListener(drawerToggle);

        if (twoPaneLayout) {
            toggleRightPane(false);
        }


        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

            profileAdapter = new TransmissionProfileListAdapter(this);

            actionBar.setListNavigationCallbacks(profileAdapter, new ActionBar.OnNavigationListener() {
                @Override
                public boolean onNavigationItemSelected(int pos, long id) {
                    TransmissionProfile profile = profileAdapter.getItem(pos);
                    if (profile != TransmissionProfileListAdapter.EMPTY_PROFILE) {
                        if (TorrentListActivity.this.profile != null) {
                            SharedPreferences prefs = TransmissionProfile.getPreferences(TorrentListActivity.this);
                            if (prefs != null)
                                prefs.unregisterOnSharedPreferenceChangeListener(profileChangeListener);
                        }

                        TransmissionProfile.setCurrentProfile(profile, TorrentListActivity.this);
                        setProfile(profile);

                        SharedPreferences prefs = TransmissionProfile.getPreferences(TorrentListActivity.this);
                        if (prefs != null)
                            prefs.registerOnSharedPreferenceChangeListener(profileChangeListener);

                        if (preventRefreshIndicator) {
                            preventRefreshIndicator = false;
                        } else {
                            setRefreshing(true, DataService.Requests.GET_TORRENTS);
                        }
                    }

                    return false;
                }
            });

            actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        }

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(STATE_CURRENT_PROFILE)) {
                profile = savedInstanceState.getParcelable(STATE_CURRENT_PROFILE);
                manager = new DataServiceManager(this, profile.getId())
                    .onRestoreInstanceState(savedInstanceState).startUpdating();
                new SessionTask(this).execute(SessionTask.Flags.START_TORRENT_TASK);
            }
            if (savedInstanceState.containsKey(STATE_FATAL_ERROR)) {
                hasFatalError = savedInstanceState.getBoolean(STATE_FATAL_ERROR, false);
            }
        }

        getSupportLoaderManager().initLoader(G.PROFILES_LOADER_ID, null, profileLoaderCallbacks);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        drawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        drawerToggle.onConfigurationChanged(newConfig);
    }

    @Override protected void onResume() {
        super.onResume();

        if (profile != null && manager == null) {
            manager = new DataServiceManager(this, profile.getId()).startUpdating();
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            serviceReceiver, new IntentFilter(G.INTENT_SERVICE_ACTION_COMPLETE));
    }

    @Override protected void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver);
        if (manager != null) {
            manager.reset();
            manager = null;
        }
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
        setRefreshing(refreshing, refreshType);
        setAltSpeed(altSpeed);

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

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_INTENT_CONSUMED, intentConsumed);
        outState.putInt(STATE_LOCATION_POSITION, locationPosition);
        outState.putParcelable(STATE_CURRENT_PROFILE, profile);
        outState.putBoolean(STATE_FATAL_ERROR, hasFatalError);
        if (manager != null) {
            manager.onSaveInstanceState(outState);
        }
    }

    @Override public void onNewIntent(Intent intent) {
        if (!newTorrentDialogVisible) {
            intentConsumed = false;
            hasNewIntent = true;
            setIntent(intent);
            setRefreshing(true, null);
            if (manager != null) {
                manager.getSession();
            }
        }
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

    public DataServiceManager getDataServiceManager() {
        return manager;
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

                FragmentManager manager = getSupportFragmentManager();
                TorrentDetailFragment fragment = (TorrentDetailFragment) manager.findFragmentByTag(G.DETAIL_FRAGMENT_TAG);
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
        if (profile != null) {
            manager = new DataServiceManager(this, profile.getId()).startUpdating();
            new SessionTask(this).execute(SessionTask.Flags.START_TORRENT_TASK);
        }
    }

    @Override public void setSession(TransmissionSession session) {
        if (session == null) {
            if (this.session != null) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
                    findViewById(R.id.sliding_menu_frame));
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

    private void setAltSpeed(boolean alt) {
        if (menu == null) {
            return;
        }
        altSpeed = alt;

        MenuItem altSpeed = menu.findItem(R.id.menu_alt_speed);
        MenuItem addTorrent = menu.findItem(R.id.menu_add_torrent);
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
        newTorrentDialogVisible = true;

        LayoutInflater inflater = getLayoutInflater();
        final View view = inflater.inflate(R.layout.new_torrent_dialog, null);

        final AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setCancelable(false)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    intentConsumed = true;
                    newTorrentDialogVisible = false;
                }

            });

        final TransmissionProfileDirectoryAdapter adapter =
            new TransmissionProfileDirectoryAdapter(
                this, android.R.layout.simple_spinner_item);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adapter.addAll(getSession().getDownloadDirectories());
        adapter.sort();
        adapter.add(getString(R.string.spinner_custom_directory));

        final Spinner location = (Spinner) view.findViewById(R.id.location_choice);
        final LinearLayout container = (LinearLayout) view.findViewById(R.id.location_container);
        final int duration = getResources().getInteger(android.R.integer.config_shortAnimTime);
        final Runnable swapLocationSpinner = new Runnable() {
            @Override public void run() {
                container.setAlpha(0f);
                container.setVisibility(View.VISIBLE);
                container.animate().alpha(1f).setDuration(duration);

                location.animate().alpha(0f).setDuration(duration).setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        location.setVisibility(View.GONE);
                        location.animate().setListener(null).cancel();
                        if (location.getSelectedItemPosition() != adapter.getCount() - 1) {
                            ((EditText) view.findViewById(R.id.location_entry)).setText((String) location.getSelectedItem());
                        }
                        container.requestFocus();
                    }
                });
            }
        };
        location.setAdapter(adapter);
        location.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(final View v) {
                swapLocationSpinner.run();
                return true;
            }
        });

        if (locationPosition == AdapterView.INVALID_POSITION) {
            if (profile != null && profile.getLastDownloadDirectory() != null) {
                int position = adapter.getPosition(profile.getLastDownloadDirectory());

                if (position > -1) {
                    location.setSelection(position);
                }
            }
        } else {
            location.setSelection(locationPosition);
        }
        location.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                if (adapter.getCount() == i + 1) {
                    swapLocationSpinner.run();
                }
                locationPosition = i;
            }
            @Override public void onNothingSelected(AdapterView<?> adapterView) {}
        });
        View collapse = view.findViewById(R.id.location_collapse);
        collapse.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                location.setAlpha(0f);
                location.setVisibility(View.VISIBLE);
                location.animate().alpha(1f).setDuration(duration);

                container.animate().alpha(0f).setDuration(duration).setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        container.setVisibility(View.GONE);
                        container.animate().setListener(null).cancel();
                    }
                });
            }
        });

        ((CheckBox) view.findViewById(R.id.start_paused)).setChecked(profile != null && profile.getStartPaused());

        final CheckBox deleteLocal = ((CheckBox) view.findViewById(R.id.delete_local));
        deleteLocal.setChecked(profile != null && profile.getDeleteLocal());

        if (data != null && data.getScheme().equals("magnet")) {
            builder.setTitle(R.string.add_new_magnet).setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
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
                });

            AlertDialog dialog = builder.create();
            dialog.show();
        } else if (fileURI != null) {
            deleteLocal.setVisibility(View.VISIBLE);

            builder.setTitle(R.string.add_new_torrent).setPositiveButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, int which) {
                        Spinner location = (Spinner) ((AlertDialog) dialog).findViewById(R.id.location_choice);
                        EditText entry = (EditText) ((AlertDialog) dialog).findViewById(R.id.location_entry);
                        CheckBox paused = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.start_paused);
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
                });

            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    private void showAddTorrentDialog() {
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.add_torrent_dialog, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setCancelable(true)
            .setView(view)
            .setTitle(R.string.add_torrent)
            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                }

            })
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
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
        public static final TransmissionProfile EMPTY_PROFILE = new TransmissionProfile(null);

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

    private class ServiceReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            int error = intent.getIntExtra(G.ARG_ERROR, 0);
            hasFatalError = false;

            String type = intent.getStringExtra(G.ARG_REQUEST_TYPE);
            switch (type) {
                case DataService.Requests.GET_SESSION:
                case DataService.Requests.SET_SESSION:
                case DataService.Requests.GET_TORRENTS:
                case DataService.Requests.ADD_TORRENT:
                case DataService.Requests.REMOVE_TORRENT:
                case DataService.Requests.SET_TORRENT:
                case DataService.Requests.SET_TORRENT_ACTION:
                case DataService.Requests.SET_TORRENT_LOCATION:
                case DataService.Requests.GET_FREE_SPACE:
                    setRefreshing(false, type);
                    if (error == 0) {

                        findViewById(R.id.fatal_error_layer).setVisibility(View.GONE);

                        int flags = TorrentTask.Flags.CONNECTED;
                        switch (type) {
                            case DataService.Requests.GET_SESSION:
                                new SessionTask(TorrentListActivity.this).execute();
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
                    } else {
                        if (error == TransmissionData.Errors.DUPLICATE_TORRENT) {
                            Toast.makeText(TorrentListActivity.this,
                                R.string.duplicate_torrent, Toast.LENGTH_SHORT).show();
                        } else if (error == TransmissionData.Errors.INVALID_TORRENT) {
                            Toast.makeText(TorrentListActivity.this,
                                R.string.invalid_torrent, Toast.LENGTH_SHORT).show();
                        } else {
                            findViewById(R.id.fatal_error_layer).setVisibility(View.VISIBLE);
                            TextView text = (TextView) findViewById(R.id.transmission_error);
                            expecting = 0;
                            hasFatalError = true;
                            toggleRightPane(false);
                            FragmentManager manager = getSupportFragmentManager();
                            TorrentListFragment fragment = (TorrentListFragment) manager.findFragmentById(R.id.torrent_list);
                            if (fragment != null) {
                                fragment.notifyTorrentListChanged(null, error, false, false, false, false, false);
                            }

                            if (error == TransmissionData.Errors.NO_CONNECTIVITY) {
                                text.setText(Html.fromHtml(getString(R.string.no_connectivity_empty_list)));
                            } else if (error == TransmissionData.Errors.ACCESS_DENIED) {
                                text.setText(Html.fromHtml(getString(R.string.access_denied_empty_list)));
                            } else if (error == TransmissionData.Errors.NO_JSON) {
                                text.setText(Html.fromHtml(getString(R.string.no_json_empty_list)));
                            } else if (error == TransmissionData.Errors.NO_CONNECTION) {
                                text.setText(Html.fromHtml(getString(R.string.no_connection_empty_list)));
                            } else if (error == TransmissionData.Errors.THREAD_ERROR) {
                                text.setText(Html.fromHtml(getString(R.string.thread_error_empty_list)));
                            } else if (error == TransmissionData.Errors.RESPONSE_ERROR) {
                                text.setText(Html.fromHtml(getString(R.string.response_error_empty_list)));
                            } else if (error == TransmissionData.Errors.TIMEOUT) {
                                text.setText(Html.fromHtml(getString(R.string.timeout_empty_list)));
                            } else if (error == TransmissionData.Errors.OUT_OF_MEMORY) {
                                text.setText(Html.fromHtml(getString(R.string.out_of_memory_empty_list)));
                            } else if (error == TransmissionData.Errors.JSON_PARSE_ERROR) {
                                text.setText(Html.fromHtml(getString(R.string.json_parse_empty_list)));
                            }
                        }
                    }
                    break;
            }
        }
    }

    private class SessionTask extends AsyncTask<Integer, Void, TransmissionSession> {
        DataSource readSource;
        boolean startTorrentTask;

        public class Flags {
            public static final int START_TORRENT_TASK = 1;
        }

        public SessionTask(Context context) {
            super();

            readSource = new DataSource(context);
        }

        @Override protected TransmissionSession doInBackground(Integer... flags) {
            try {
                readSource.open();

                TransmissionSession session = readSource.getSession();
                if (profile != null) {
                    session.setDownloadDirectories(profile, readSource.getDownloadDirectories());
                }

                if (flags.length == 1) {
                    if ((flags[0] & Flags.START_TORRENT_TASK) == Flags.START_TORRENT_TASK) {
                        startTorrentTask = true;
                    }
                }

                return session;
            } finally {
                if (readSource.isOpen()) {
                    readSource.close();
                }
            }
        }

        @Override protected void onPostExecute(TransmissionSession session) {
            setSession(session);

            if (session.getRPCVersion() >= TransmissionSession.FREE_SPACE_METHOD_RPC_VERSION && manager != null) {
                manager.getFreeSpace(session.getDownloadDir());
            }

            if (startTorrentTask) {
                new TorrentTask(TorrentListActivity.this, TorrentTask.Flags.UPDATE).execute();
            }

            if (hasNewIntent && session != null) {
                hasNewIntent = false;
                if (!intentConsumed && !newTorrentDialogVisible) {
                    consumeIntent();
                }
            }
        }
    }

    private class TorrentTask extends AsyncTask<Void, Void, Cursor> {
        DataSource readSource;
        boolean added, removed, statusChanged, incompleteMetadata, update, connected;

        public class Flags {
            public static final int HAS_ADDED = 1;
            public static final int HAS_REMOVED = 1 << 1;
            public static final int HAS_STATUS_CHANGED = 1 << 2;
            public static final int HAS_INCOMPLETE_METADATA = 1 << 3;
            public static final int UPDATE = 1 << 4;
            public static final int CONNECTED = 1 << 5;
        }

        public TorrentTask(Context context, int flags) {
            super();

            readSource = new DataSource(context);
            if ((flags & Flags.HAS_ADDED) == Flags.HAS_ADDED) {
                added = true;
            }
            if ((flags & Flags.HAS_REMOVED) == Flags.HAS_REMOVED) {
                removed = true;
            }
            if ((flags & Flags.HAS_STATUS_CHANGED) == Flags.HAS_STATUS_CHANGED) {
                statusChanged = true;
            }
            if ((flags & Flags.HAS_INCOMPLETE_METADATA) == Flags.HAS_INCOMPLETE_METADATA) {
                incompleteMetadata = true;
            }
            if ((flags & Flags.UPDATE) == Flags.UPDATE) {
                update = true;
            }
            if ((flags & Flags.CONNECTED) == Flags.CONNECTED) {
                connected = true;
            }
        }

        @Override protected Cursor doInBackground(Void... unused) {
            try {
                readSource.open();

                Cursor cursor = readSource.getTorrentCursor();

                /* Fill the cursor window */
                cursor.getCount();

                return cursor;
            } finally {
                if (readSource.isOpen()) {
                    readSource.close();
                }
            }
        }

        @Override protected void onPostExecute(Cursor cursor) {
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
                fragment.notifyTorrentListChanged(cursor, 0, added, removed,
                    statusChanged, incompleteMetadata, connected);
            }

            if (update) {
                update = false;
                if (manager != null) {
                    manager.update();
                }
            }
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
