package org.sugr.gearshift;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.ValueAnimator;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.service.DataServiceManagerInterface;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

public class TorrentListActivity extends FragmentActivity
        implements TransmissionSessionInterface, TorrentListFragment.Callbacks,
                   TorrentDetailFragment.PagerCallbacks,
                   DataServiceManagerInterface {

    public static final String ARG_FILE_URI = "torrent_file_uri";
    public static final String ARG_FILE_PATH = "torrent_file_path";
    public final static String ACTION_OPEN = "torrent_file_open_action";

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean twoPaneLayout;

    private TransmissionProfile profile;
    private TransmissionSession session;
    private DataServiceManager manager;

    private ServiceReceiver serviceReceiver;

    private boolean intentConsumed = false;
    private boolean dialogShown = false;

    private static final String STATE_INTENT_CONSUMED = "intent_consumed";
    private static final String STATE_LOCATION_POSITION = "location_position";
    private static final String STATE_CURRENT_PROFILE = "current_profile";

    private DrawerLayout drawerLayout;
    private ActionBarDrawerToggle drawerToggle;

    private ValueAnimator detailSlideAnimator;

    private boolean detailPanelVisible;

    private int locationPosition = AdapterView.INVALID_POSITION;

    private int currentTorrentIndex = -1;

    private TransmissionProfileListAdapter profileAdapter;

    private boolean altSpeed = false;
    private boolean refreshing = false;

    private boolean preventRefreshIndicator;

    private int expecting = 0;

    private static class Expecting {
        static int ALT_SPEED_ON = 1;
        static int ALT_SPEED_OFF = 1 << 1;
    }

    private Menu menu;

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
                setRefreshing(false);
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
                    final FragmentManager manager=getSupportFragmentManager();
                    TorrentDetailFragment fragment=(TorrentDetailFragment) manager.findFragmentByTag(
                        G.DETAIL_FRAGMENT_TAG);
                    if (fragment == null) {
                        fragment=new TorrentDetailFragment();
                        fragment.setArguments(new Bundle());
                        manager.beginTransaction()
                            .replace(R.id.torrent_detail_container, fragment, G.DETAIL_FRAGMENT_TAG)
                            .commit();
                        manager.executePendingTransactions();
                    }

                    fragment.setCurrentTorrent(currentTorrentIndex);

                    G.logD("Opening the detail panel");
                    TorrentListActivity.this.manager.setDetails(true);
                    new TorrentTask(TorrentListActivity.this).execute(true);

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
            setRefreshing(true);
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
                            setRefreshing(true);
                        }
                    }

                    return false;
                }
            });

            actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        }

        if (savedInstanceState != null
            && savedInstanceState.containsKey(STATE_CURRENT_PROFILE)) {
            profile = savedInstanceState.getParcelable(STATE_CURRENT_PROFILE);
            manager = new DataServiceManager(this, profile.getId()).startUpdating();
            new SessionTask(this).execute(true);
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

        if (profile != null) {
            manager = new DataServiceManager(this, profile.getId())
                .setSessionOnly(true).startUpdating();
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(
            serviceReceiver, new IntentFilter(G.INTENT_SERVICE_ACTION_COMPLETE));
    }

    @Override protected void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver);
        if (manager != null) {
            manager.reset();
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

    @Override
    public void onPageSelected(int position) {
        if (twoPaneLayout) {
            ((TorrentListFragment) getSupportFragmentManager()
             .findFragmentById(R.id.torrent_list))
                .getListView().setItemChecked(position, true);
        }
    }

    @Override
    public void onBackPressed() {
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
        setRefreshing(refreshing);
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
                setRefreshing(!refreshing);
                return true;
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
            if (fragment.onOptionsItemSelected(item)) {
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
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (!dialogShown) {
            intentConsumed = false;
            setIntent(intent);
            if (session != null) {
                consumeIntent();
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
        if (profile == null) {
            manager.reset();
        } else {
            manager = new DataServiceManager(this, profile.getId()).startUpdating();
            new SessionTask(this).execute(true);
        }
    }

    @Override
    public TransmissionProfile getProfile() {
        return profile;
    }

    @Override
    public void setSession(TransmissionSession session) {
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
            }

            this.session = session;
            if (menu != null) {
                menu.findItem(R.id.menu_session_settings).setVisible(true);
            }

            if (initial && !intentConsumed && !dialogShown) {
                consumeIntent();
            }
        }
    }

    @Override
    public TransmissionSession getSession() {
        return session;
    }

    @Override
    public void setRefreshing(boolean refreshing) {
        this.refreshing = refreshing;
        if (menu == null) {
            return;
        }

        MenuItem item = menu.findItem(R.id.menu_refresh);
        if (this.refreshing)
            item.setActionView(R.layout.action_progress_bar);
        else
            item.setActionView(null);
    }

    private void setAltSpeed(boolean alt) {
        if (menu == null) {
            return;
        }
        altSpeed = alt;

        MenuItem item = menu.findItem(R.id.menu_alt_speed);
        if (session == null) {
            item.setVisible(false);
        } else {
            item.setVisible(true);
            if (altSpeed) {
                item.setIcon(R.drawable.ic_action_data_usage_on);
                item.setTitle(R.string.alt_speed_label_off);
            } else {
                item.setIcon(R.drawable.ic_action_data_usage);
                item.setTitle(R.string.alt_speed_label_on);
            }
        }
    }

    private void consumeIntent() {
        Intent intent = getIntent();
        String action = intent.getAction();

        if ((intent.getFlags() & Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
                && (Intent.ACTION_VIEW.equals(action) || ACTION_OPEN.equals(action))) {
            dialogShown = true;
            final Uri data = intent.getData();

            LayoutInflater inflater = getLayoutInflater();
            View view = inflater.inflate(R.layout.add_torrent_dialog, null);

            final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        intentConsumed = true;
                        dialogShown = false;
                    }

                });

            TransmissionProfileDirectoryAdapter adapter =
                    new TransmissionProfileDirectoryAdapter(
                    this, android.R.layout.simple_spinner_item);

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            adapter.addAll(getSession().getDownloadDirectories());
            adapter.sort();

            Spinner location = (Spinner) view.findViewById(R.id.location_choice);
            location.setAdapter(adapter);

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
                    locationPosition = i;
                }
                @Override public void onNothingSelected(AdapterView<?> adapterView) {}
            });

            ((CheckBox) view.findViewById(R.id.start_paused)).setChecked(profile != null && profile.getStartPaused());

            final CheckBox deleteLocal = ((CheckBox) view.findViewById(R.id.delete_local));
            deleteLocal.setChecked(profile != null && profile.getDeleteLocal());

            if (data.getScheme().equals("magnet")) {
                builder.setTitle(R.string.add_magnet).setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Spinner location = (Spinner) ((AlertDialog) dialog).findViewById(R.id.location_choice);
                        CheckBox paused = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.start_paused);

                        String dir = (String) location.getSelectedItem();
                        manager.addTorrent(data.toString(), null, dir, paused.isChecked(), null);

                        setRefreshing(true);
                        intentConsumed = true;
                        dialogShown = false;
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            } else {
                final String fileURI = intent.getStringExtra(ARG_FILE_URI);
                final String filePath = intent.getStringExtra(ARG_FILE_PATH);

                deleteLocal.setVisibility(View.VISIBLE);
                builder.setTitle(R.string.add_torrent).setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(final DialogInterface dialog, int which) {
                        Spinner location = (Spinner) ((AlertDialog) dialog).findViewById(R.id.location_choice);
                        CheckBox paused = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.start_paused);
                        BufferedReader reader = null;

                        try {
                            String dir = (String) location.getSelectedItem();
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
                            if (!deleteLocal.isChecked()) {
                                path = null;
                            }
                            manager.addTorrent(null, filedata.toString(), dir, paused.isChecked(), path);

                            setRefreshing(true);
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
                        dialogShown = false;
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        } else {
            intentConsumed = true;
        }
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

            String type = intent.getStringExtra(G.ARG_REQUEST_TYPE);
            switch (type) {
                case DataService.Requests.GET_SESSION:
                case DataService.Requests.SET_SESSION:
                case DataService.Requests.GET_ACTIVE_TORRENTS:
                case DataService.Requests.GET_ALL_TORRENTS:
                case DataService.Requests.ADD_TORRENT:
                case DataService.Requests.REMOVE_TORRENT:
                case DataService.Requests.SET_TORRENT:
                case DataService.Requests.SET_TORRENT_ACTION:
                case DataService.Requests.SET_TORRENT_LOCATION:
                    setRefreshing(false);
                    if (error == 0) {

                        findViewById(R.id.fatal_error_layer).setVisibility(View.GONE);

                        switch (type) {
                            case DataService.Requests.GET_SESSION:
                                new SessionTask(TorrentListActivity.this).execute();
                                break;
                            case DataService.Requests.SET_SESSION:
                                manager.getSession();
                                break;
                            case DataService.Requests.GET_ACTIVE_TORRENTS:
                            case DataService.Requests.GET_ALL_TORRENTS:
                                boolean added = intent.getBooleanExtra(G.ARG_ADDED, false);
                                boolean removed = intent.getBooleanExtra(G.ARG_REMOVED, false);
                                boolean statusChanged = intent.getBooleanExtra(G.ARG_STATUS_CHANGED, false);
                                boolean incomplete = intent.getBooleanExtra(G.ARG_INCOMPLETE_METADATA, false);

                                new TorrentTask(TorrentListActivity.this).execute(added, removed, statusChanged, incomplete);
                                break;
                            case DataService.Requests.ADD_TORRENT:
                                manager.update();
                                new TorrentTask(TorrentListActivity.this).execute(true, false, false, true);
                                break;
                            case DataService.Requests.REMOVE_TORRENT:
                                manager.update();
                                new TorrentTask(TorrentListActivity.this).execute(false, true, false, false);
                                break;
                            case DataService.Requests.SET_TORRENT_LOCATION:
                                manager.update();
                                new TorrentTask(TorrentListActivity.this).execute(true, true, false, false);
                                break;
                            case DataService.Requests.SET_TORRENT:
                            case DataService.Requests.SET_TORRENT_ACTION:
                                manager.update();
                                new TorrentTask(TorrentListActivity.this).execute(false, false, true, false);
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
                            toggleRightPane(false);
                            FragmentManager manager = getSupportFragmentManager();
                            TorrentListFragment fragment = (TorrentListFragment) manager.findFragmentById(R.id.torrent_list);
                            if (fragment != null) {
                                fragment.notifyTorrentListChanged(null, error, false, false, false, false);
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

    private class SessionTask extends AsyncTask<Boolean, Void, TransmissionSession> {
        DataSource readSource;
        boolean startTorrentTask;

        public SessionTask(Context context) {
            super();

            readSource = new DataSource(context);
        }

        @Override protected TransmissionSession doInBackground(Boolean... startTorrentTask) {
            try {
                readSource.open();

                TransmissionSession session = readSource.getSession();
                session.setDownloadDirectories(profile, readSource.getDownloadDirectories());

                if (startTorrentTask.length == 1 && startTorrentTask[0]) {
                    this.startTorrentTask = true;
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

            if (startTorrentTask) {
                new TorrentTask(TorrentListActivity.this).execute();
            }
        }
    }

    private class TorrentTask extends AsyncTask<Boolean, Void, Cursor> {
        DataSource readSource;
        boolean added, removed, statusChanged, incompleteMetadata, update;

        public TorrentTask(Context context) {
            super();

            readSource = new DataSource(context);
        }

        @Override protected Cursor doInBackground(Boolean... flags) {
            try {
                readSource.open();

                Cursor cursor = readSource.getTorrentCursor();

                added = true;
                removed = true;
                if (flags.length == 1) {
                    update = flags[0];
                } else if (flags.length == 4) {
                    added = flags[0];
                    removed = flags[1];
                    statusChanged = flags[2];
                    incompleteMetadata = flags[3];
                }

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

            if (refreshing && !update) {
                setRefreshing(false);
            }
            FragmentManager manager = getSupportFragmentManager();
            TorrentListFragment fragment = (TorrentListFragment) manager.findFragmentById(R.id.torrent_list);
            if (fragment != null) {
                fragment.notifyTorrentListChanged(cursor, 0, added, removed,
                    statusChanged, incompleteMetadata);
            }

            if (update) {
                update = false;
                TorrentListActivity.this.manager.update();
            }
        }
    }
}
