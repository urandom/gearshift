package org.sugr.gearshift;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.ValueAnimator;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;

public class TorrentListActivity extends FragmentActivity
        implements TransmissionSessionInterface, TorrentListFragment.Callbacks,
                   TorrentDetailFragment.PagerCallbacks {

    public static final String ARG_FILE_URI = "torrent_file_uri";
    public static final String ARG_FILE_PATH = "torrent_file_path";
    public final static String ACTION_OPEN = "torrent_file_open_action";

    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;

    private TransmissionProfile mProfile;
    private TransmissionSession mSession;

    private boolean mIntentConsumed = false;
    private boolean mDialogShown = false;

    private static final String STATE_INTENT_CONSUMED = "intent_consumed";
    private static final String STATE_LOCATION_POSITION = "location_position";
    private static final String STATE_CURRENT_PROFILE = "current_profile";

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;

    private ValueAnimator mDetailSlideAnimator;

    private boolean mDetailPanelShown;

    private int mLocationPosition = AdapterView.INVALID_POSITION;

    private int currentTorrentIndex = -1;

    private TransmissionProfileListAdapter mProfileAdapter;

    private boolean mAltSpeed = false;
    private boolean mRefreshing = false;

    private boolean mPreventRefreshIndicator;

    private int mExpecting = 0;

    private static class Expecting {
        static int ALT_SPEED_ON = 1;
        static int ALT_SPEED_OFF = 1 << 1;
    }

    private Menu menu;

    private LoaderManager.LoaderCallbacks<TransmissionProfile[]> mProfileLoaderCallbacks = new LoaderManager.LoaderCallbacks<TransmissionProfile[]>() {
        @Override
        public android.support.v4.content.Loader<TransmissionProfile[]> onCreateLoader(
            int id, Bundle args) {
            return new TransmissionProfileSupportLoader(TorrentListActivity.this);
        }

        @Override
        public void onLoadFinished(
            android.support.v4.content.Loader<TransmissionProfile[]> loader,
            TransmissionProfile[] profiles) {

            TransmissionProfile oldProfile = mProfile;

            mProfile = null;
            mProfileAdapter.clear();
            if (profiles.length > 0) {
                mProfileAdapter.addAll(profiles);
            } else {
                mProfileAdapter.add(TransmissionProfileListAdapter.EMPTY_PROFILE);
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

            if (mProfile == null) {
                if (profiles.length > 0) {
                    setProfile(profiles[0]);
                } else {
                    setProfile(null);
                }
                getSupportLoaderManager().destroyLoader(G.TORRENTS_LOADER_ID);
            } else {
                /* The torrents might be loaded before the navigation
                 * callback fires, which will cause the refresh indicator to
                 * appear until the next server request */
                mPreventRefreshIndicator = true;

                /* The old cursor will probably already be closed, so start fresh */
                getSupportLoaderManager().restartLoader(
                    G.TORRENTS_LOADER_ID,
                    null, mTorrentLoaderCallbacks);
            }

            TransmissionProfile.setCurrentProfile(mProfile, TorrentListActivity.this);
        }

        @Override
        public void onLoaderReset(
            android.support.v4.content.Loader<TransmissionProfile[]> loader) {
            mProfileAdapter.clear();
        }

    };

    private LoaderManager.LoaderCallbacks<TransmissionData> mTorrentLoaderCallbacks = new LoaderManager.LoaderCallbacks<TransmissionData>() {

        @Override
        public android.support.v4.content.Loader<TransmissionData> onCreateLoader(
            int id, Bundle args) {
            G.logD("Starting the torrents loader with profile " + mProfile);
            if (mProfile == null) return null;

            return new TransmissionDataLoader(TorrentListActivity.this, mProfile);
        }

        @Override
        public void onLoadFinished(
            android.support.v4.content.Loader<TransmissionData> loader,
            TransmissionData data) {

            G.logD("Data loaded: " + data.cursor.getCount() + " torrents, error: " + data.error + " , removed: " + data.hasRemoved + ", added: " + data.hasAdded + ", changed: " + data.hasStatusChanged + ", metadata: " + data.hasMetadataNeeded);
            setSession(data.session);

            View error = findViewById(R.id.fatal_error_layer);
            if (data.error == 0 && error.getVisibility() != View.GONE) {
                error.setVisibility(View.GONE);
            }
            if (data.error > 0) {
                if (data.error == TransmissionData.Errors.DUPLICATE_TORRENT) {
                    Toast.makeText(TorrentListActivity.this, R.string.duplicate_torrent, Toast.LENGTH_SHORT).show();
                } else if (data.error == TransmissionData.Errors.INVALID_TORRENT) {
                    Toast.makeText(TorrentListActivity.this, R.string.invalid_torrent, Toast.LENGTH_SHORT).show();
                } else {
                    error.setVisibility(View.VISIBLE);
                    TextView text = (TextView) findViewById(R.id.transmission_error);
                    toggleRightPane(false);

                    if (data.error == TransmissionData.Errors.NO_CONNECTIVITY) {
                        text.setText(Html.fromHtml(getString(R.string.no_connectivity_empty_list)));
                    } else if (data.error == TransmissionData.Errors.ACCESS_DENIED) {
                        text.setText(Html.fromHtml(getString(R.string.access_denied_empty_list)));
                    } else if (data.error == TransmissionData.Errors.NO_JSON) {
                        text.setText(Html.fromHtml(getString(R.string.no_json_empty_list)));
                    } else if (data.error == TransmissionData.Errors.NO_CONNECTION) {
                        text.setText(Html.fromHtml(getString(R.string.no_connection_empty_list)));
                    } else if (data.error == TransmissionData.Errors.GENERIC_HTTP) {
                        text.setText(Html.fromHtml(String.format(
                            getString(R.string.generic_http_empty_list), data.errorCode)));
                    } else if (data.error == TransmissionData.Errors.THREAD_ERROR) {
                        text.setText(Html.fromHtml(getString(R.string.thread_error_empty_list)));
                    } else if (data.error == TransmissionData.Errors.RESPONSE_ERROR) {
                        text.setText(Html.fromHtml(getString(R.string.response_error_empty_list)));
                    } else if (data.error == TransmissionData.Errors.TIMEOUT) {
                        text.setText(Html.fromHtml(getString(R.string.timeout_empty_list)));
                    } else if (data.error == TransmissionData.Errors.OUT_OF_MEMORY) {
                        text.setText(Html.fromHtml(getString(R.string.out_of_memory_empty_list)));
                    } else if (data.error == TransmissionData.Errors.JSON_PARSE_ERROR) {
                        text.setText(Html.fromHtml(getString(R.string.json_parse_empty_list)));
                    }
                }
            }
            if (data.error == 0) {
                if (mAltSpeed == mSession.isAltSpeedLimitEnabled()) {
                    mExpecting &= ~(Expecting.ALT_SPEED_ON | Expecting.ALT_SPEED_OFF);
                } else {
                    if (mExpecting == 0
                        || (mExpecting & Expecting.ALT_SPEED_ON) > 0 && mSession.isAltSpeedLimitEnabled()
                        || (mExpecting & Expecting.ALT_SPEED_OFF) > 0 && !mSession.isAltSpeedLimitEnabled()) {
                        setAltSpeed(mSession.isAltSpeedLimitEnabled());
                        mExpecting &= ~(Expecting.ALT_SPEED_ON | Expecting.ALT_SPEED_OFF);
                    }
                }
            } else {
                switch(data.error) {
                    case TransmissionData.Errors.DUPLICATE_TORRENT:
                    case TransmissionData.Errors.INVALID_TORRENT:
                        break;
                    default:
                        mExpecting = 0;
                        break;
                }
            }

            if (mRefreshing) {
                setRefreshing(false);
            }

            FragmentManager manager = getSupportFragmentManager();
            TorrentListFragment fragment = (TorrentListFragment) manager.findFragmentById(R.id.torrent_list);
            if (fragment != null) {
                fragment.notifyTorrentListChanged(data.cursor, data.error, data.hasAdded,
                    data.hasRemoved, data.hasStatusChanged, data.hasMetadataNeeded);
            }
        }

        @Override
        public void onLoaderReset(
            android.support.v4.content.Loader<TransmissionData> loader) {
            FragmentManager manager = getSupportFragmentManager();
            TorrentListFragment fragment = (TorrentListFragment) manager.findFragmentById(R.id.torrent_list);
            if (fragment != null) {
                fragment.notifyTorrentListChanged(null, -1, false, false, false, false);
            }
        }

    };

    /* The callback will get garbage collected if its a mere anon class */
    private SharedPreferences.OnSharedPreferenceChangeListener mProfileChangeListener =
        new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                if (TorrentListActivity.this == null || mProfile == null) return;

                if (!key.endsWith(mProfile.getId())) return;

                Loader<TransmissionData> loader = getSupportLoaderManager()
                    .getLoader(G.TORRENTS_LOADER_ID);

                mProfile.load();

                TransmissionProfile.setCurrentProfile(mProfile, TorrentListActivity.this);
                setProfile(mProfile);
                if (loader != null) {
                    ((TransmissionDataLoader) loader).setProfile(mProfile);
                }
            }
        };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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
            mTwoPane = true;

            // In two-pane mode, list items should be given the
            // 'activated' state when touched.
            ((TorrentListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.torrent_list))
                    .setActivateOnItemClick(true);

            final LinearLayout slidingLayout = (LinearLayout) findViewById(R.id.sliding_layout);
            final View detailPanel = findViewById(R.id.torrent_detail_panel);

            mDetailSlideAnimator = (ValueAnimator) AnimatorInflater.loadAnimator(this, R.anim.weight_animator);
            mDetailSlideAnimator.setDuration(getResources().getInteger(android.R.integer.config_shortAnimTime));
            mDetailSlideAnimator.setInterpolator(new DecelerateInterpolator());
            mDetailSlideAnimator.addListener(new Animator.AnimatorListener() {
                @Override public void onAnimationStart(Animator animation) { }

                @Override public void onAnimationRepeat(Animator animation) { }

                @Override public void onAnimationEnd(Animator animation) {
                    final FragmentManager manager = getSupportFragmentManager();
                    TorrentDetailFragment fragment = (TorrentDetailFragment) manager.findFragmentByTag(
                            G.DETAIL_FRAGMENT_TAG);
                    if (fragment == null) {
                        fragment = new TorrentDetailFragment();
                        fragment.setArguments(new Bundle());
                        manager.beginTransaction()
                            .replace(R.id.torrent_detail_container, fragment, G.DETAIL_FRAGMENT_TAG)
                            .commit();
                        manager.executePendingTransactions();
                    } else {
                        fragment.resetPagerAdapter();
                    }

                    fragment.onCreateOptionsMenu(menu, getMenuInflater());
                    fragment.setCurrentTorrent(currentTorrentIndex);

                    Cursor cursor = ((TorrentListFragment) manager.findFragmentById(R.id.torrent_list)).getCursor();
                    fragment.changeCursor(cursor);

                    Handler handler = new Handler();
                    handler.post(new Runnable() {
                       @Override public void run() {
                           View bg = findViewById(R.id.torrent_detail_placeholder_background);
                           if (bg != null) {
                               bg.setVisibility(View.GONE);
                           }
                           View pager = findViewById(R.id.torrent_detail_pager);
                           pager.setVisibility(View.VISIBLE);
                           pager.animate().alpha((float) 1.0);
                       }
                    });
                }

                @Override public void onAnimationCancel(Animator animation) { }
            });
            mDetailSlideAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float value = (Float) animation.getAnimatedValue();
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                            detailPanel.getLayoutParams();

                    params.weight = value;
                    slidingLayout.requestLayout();
                }

            });
        }

        if (savedInstanceState == null) {
            mRefreshing = true;
        } else {
            if (savedInstanceState.containsKey(STATE_INTENT_CONSUMED)) {
                mIntentConsumed = savedInstanceState.getBoolean(STATE_INTENT_CONSUMED);
            }
            if (savedInstanceState.containsKey(STATE_LOCATION_POSITION)) {
                mLocationPosition = savedInstanceState.getInt(STATE_LOCATION_POSITION);
            }
        }

        getWindow().setBackgroundDrawable(null);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
                findViewById(R.id.sliding_menu_frame));
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, R.drawable.ic_drawer,
                R.string.open_drawer, R.string.close_drawer) { };
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        if (mTwoPane) {
            toggleRightPane(false);
        }


        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

            mProfileAdapter = new TransmissionProfileListAdapter(this);

            actionBar.setListNavigationCallbacks(mProfileAdapter, new ActionBar.OnNavigationListener() {
                @Override
                public boolean onNavigationItemSelected(int pos, long id) {
                    TransmissionProfile profile = mProfileAdapter.getItem(pos);
                    if (profile != TransmissionProfileListAdapter.EMPTY_PROFILE) {
                        final Loader<TransmissionData> loader = getSupportLoaderManager()
                            .getLoader(G.TORRENTS_LOADER_ID);

                        if (mProfile != null) {
                            SharedPreferences prefs = TransmissionProfile.getPreferences(TorrentListActivity.this);
                            if (prefs != null)
                                prefs.unregisterOnSharedPreferenceChangeListener(mProfileChangeListener);
                        }

                        TransmissionProfile.setCurrentProfile(profile, TorrentListActivity.this);
                        setProfile(profile);
                        if (loader != null) {
                            ((TransmissionDataLoader) loader).setProfile(profile);
                        }

                        SharedPreferences prefs = TransmissionProfile.getPreferences(TorrentListActivity.this);
                        if (prefs != null)
                            prefs.registerOnSharedPreferenceChangeListener(mProfileChangeListener);

                        if (mPreventRefreshIndicator) {
                            mPreventRefreshIndicator = false;
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
            mProfile = savedInstanceState.getParcelable(STATE_CURRENT_PROFILE);
        }

        getSupportLoaderManager().initLoader(G.PROFILES_LOADER_ID, null, mProfileLoaderCallbacks);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        mDrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    /**
     * Callback method from {@link TorrentListFragment.Callbacks}
     * indicating that the item with the given ID was selected.
     */
    @Override
    public void onItemSelected(int position) {
        if (mTwoPane) {
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
            detailIntent.putExtra(G.ARG_PROFILE, mProfile);
            detailIntent.putExtra(G.ARG_SESSION, mSession);
            startActivity(detailIntent);
        }
    }

    @Override
    public void onPageSelected(int position) {
        if (mTwoPane) {
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

        setSession(mSession);
        setRefreshing(mRefreshing);
        setAltSpeed(mAltSpeed);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (mDrawerLayout.getDrawerLockMode(
                findViewById(R.id.sliding_menu_frame)
            ) == DrawerLayout.LOCK_MODE_UNLOCKED && mDrawerToggle.onOptionsItemSelected(item)) {
            return true;
        }

        Intent intent;
        Loader<TransmissionData> loader;
        switch(item.getItemId()) {
            case android.R.id.home:
                if (!mTwoPane) {
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
                loader = getSupportLoaderManager()
                    .getLoader(G.TORRENTS_LOADER_ID);
                if (loader != null) {
                    mExpecting &= ~(Expecting.ALT_SPEED_ON | Expecting.ALT_SPEED_OFF);
                    if (mAltSpeed) {
                        setAltSpeed(false);
                        mExpecting |= Expecting.ALT_SPEED_OFF;
                    } else {
                        setAltSpeed(true);
                        mExpecting |= Expecting.ALT_SPEED_ON;
                    }
                    mSession.setAltSpeedLimitEnabled(mAltSpeed);
                    ((TransmissionDataLoader) loader).setSession(mSession, "alt-speed-enabled");
                }
                return true;
            case R.id.menu_refresh:
                loader = getSupportLoaderManager()
                    .getLoader(G.TORRENTS_LOADER_ID);
                if (loader != null) {
                    loader.onContentChanged();
                    setRefreshing(!mRefreshing);
                }
                return true;
            case R.id.menu_session_settings:
                intent = new Intent(this, TransmissionSessionActivity.class);
                intent.putExtra(G.ARG_PROFILE, mProfile);
                intent.putExtra(G.ARG_SESSION, mSession);
                startActivity(intent);
                return true;
            case R.id.menu_settings:
                intent = new Intent(this, SettingsActivity.class);
                if (mSession != null) {
                    ArrayList<String> directories = new ArrayList<String>(mSession.getDownloadDirectories());
                    directories.remove(mSession.getDownloadDir());
                    intent.putExtra(G.ARG_DIRECTORIES, directories);
                }
                if (mProfile != null) {
                    intent.putExtra(G.ARG_PROFILE_ID, mProfile.getId());
                }
                startActivity(intent);
                return true;
            case R.id.menu_about:
                intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                return true;
        }
        if (mTwoPane) {
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
        outState.putBoolean(STATE_INTENT_CONSUMED, mIntentConsumed);
        outState.putInt(STATE_LOCATION_POSITION, mLocationPosition);
        outState.putParcelable(STATE_CURRENT_PROFILE, mProfile);
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (!mDialogShown) {
            mIntentConsumed = false;
            setIntent(intent);
            if (mSession != null) {
                consumeIntent();
            }
        }
    }

    public boolean isDetailPanelShown() {
        return mTwoPane && mDetailPanelShown;
    }

    private boolean toggleRightPane(boolean show) {
        if (!mTwoPane) return false;

        View pager = findViewById(R.id.torrent_detail_pager);
        if (show) {
            if (!mDetailPanelShown) {
                mDetailPanelShown = true;
                mDetailSlideAnimator.start();

                Loader<TransmissionData> loader =
                        getSupportLoaderManager().getLoader(G.TORRENTS_LOADER_ID);
                if (loader != null) {
                    ((TransmissionDataLoader) loader).setDetails(true);
                }

                return true;
            }
        } else {
            if (mDetailPanelShown) {
                mDetailPanelShown = false;
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                        findViewById(R.id.torrent_detail_panel).getLayoutParams();
                params.weight = 0;
                if (pager != null) {
                    pager.setAlpha(0);
                    pager.setVisibility(View.GONE);
                }
                Loader<TransmissionData> loader = getSupportLoaderManager().getLoader(G.TORRENTS_LOADER_ID);
                if (loader != null) {
                    ((TransmissionDataLoader) loader).setDetails(false);
                }

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
        mProfile = profile;
        toggleRightPane(false);
    }

    @Override
    public TransmissionProfile getProfile() {
        return mProfile;
    }

    @Override
    public void setSession(TransmissionSession session) {
        if (session == null) {
            if (mSession != null) {
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
                        findViewById(R.id.sliding_menu_frame));
                getActionBar().setDisplayHomeAsUpEnabled(false);

                invalidateOptionsMenu();
            }

            mSession = null;
            if (menu != null) {
                menu.findItem(R.id.menu_session_settings).setVisible(false);
            }
        } else {
            boolean initial = false;
            if (mSession == null) {
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED,
                        findViewById(R.id.sliding_menu_frame));
                getActionBar().setDisplayHomeAsUpEnabled(true);

                invalidateOptionsMenu();
                initial = true;
            }

            mSession = session;
            if (menu != null) {
                menu.findItem(R.id.menu_session_settings).setVisible(true);
            }

            if (initial && !mIntentConsumed && !mDialogShown) {
                consumeIntent();
            }
        }
    }

    @Override
    public TransmissionSession getSession() {
        return mSession;
    }

    @Override
    public void setRefreshing(boolean refreshing) {
        if (menu == null) {
            return;
        }
        mRefreshing = refreshing;

        MenuItem item = menu.findItem(R.id.menu_refresh);
        if (mRefreshing)
            item.setActionView(R.layout.action_progress_bar);
        else
            item.setActionView(null);
    }

    private void setAltSpeed(boolean alt) {
        if (menu == null) {
            return;
        }
        mAltSpeed = alt;

        MenuItem item = menu.findItem(R.id.menu_alt_speed);
        if (mSession == null) {
            item.setVisible(false);
        } else {
            item.setVisible(true);
            if (mAltSpeed) {
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
            mDialogShown = true;
            final Uri data = intent.getData();

            LayoutInflater inflater = getLayoutInflater();
            View view = inflater.inflate(R.layout.add_torrent_dialog, null);
            final Loader<TransmissionData> loader = getSupportLoaderManager()
                    .getLoader(G.TORRENTS_LOADER_ID);

            final AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setCancelable(false)
                .setView(view)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mIntentConsumed = true;
                        mDialogShown = false;
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

            if (mLocationPosition == AdapterView.INVALID_POSITION) {
                if (mProfile != null && mProfile.getLastDownloadDirectory() != null) {
                    int position = adapter.getPosition(mProfile.getLastDownloadDirectory());

                    if (position > -1) {
                        location.setSelection(position);
                    }
                }
            } else {
                location.setSelection(mLocationPosition);
            }
            location.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    mLocationPosition = i;
                }
                @Override public void onNothingSelected(AdapterView<?> adapterView) {}
            });

            ((CheckBox) view.findViewById(R.id.start_paused)).setChecked(mProfile != null && mProfile.getStartPaused());

            final CheckBox deleteLocal = ((CheckBox) view.findViewById(R.id.delete_local));
            deleteLocal.setChecked(mProfile != null && mProfile.getDeleteLocal());

            if (data.getScheme().equals("magnet")) {
                builder.setTitle(R.string.add_magnet).setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        Spinner location = (Spinner) ((AlertDialog) dialog).findViewById(R.id.location_choice);
                        CheckBox paused = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.start_paused);

                        String dir = (String) location.getSelectedItem();
                        ((TransmissionDataLoader) loader).addTorrent(
                                data.toString(), null, dir,
                                paused.isChecked(), null);

                        setRefreshing(true);
                        mIntentConsumed = true;
                        mDialogShown = false;
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
                            ((TransmissionDataLoader) loader).addTorrent(
                                    null, filedata.toString(),
                                    dir, paused.isChecked(), path);

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
                        mIntentConsumed = true;
                        mDialogShown = false;
                    }
                });

                AlertDialog dialog = builder.create();
                dialog.show();
            }
        } else {
            mIntentConsumed = true;
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
}
