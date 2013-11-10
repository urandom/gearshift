package org.sugr.gearshift;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.Loader;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

import org.sugr.gearshift.TransmissionSessionManager.TransmissionExclusionStrategy;

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

    private ArrayList<Torrent> mTorrents = new ArrayList<Torrent>();

    private TransmissionProfile mProfile;
    private TransmissionSession mSession;

    private boolean mIntentConsumed = false;
    private boolean mDialogShown = false;

    private static final String STATE_INTENT_CONSUMED = "intent_consumed";
    private static final String STATE_LOCATION_POSITION = "location_position";

    private DrawerLayout mDrawerLayout;
    private ActionBarDrawerToggle mDrawerToggle;

    private ValueAnimator mDetailSlideAnimator;

    private boolean mDetailPanelShown;

    private Bundle mDetailArguments;

    private int mLocationPosition = AdapterView.INVALID_POSITION;

    private SharedPreferences mSharedPrefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mSharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        G.DEBUG = mSharedPrefs.getBoolean(G.PREF_DEBUG, false);

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
                            TorrentDetailFragment.TAG);
                    if (fragment == null) {
                        Fragment frag = new TorrentDetailFragment();
                        frag.setArguments(mDetailArguments);
                        manager.beginTransaction()
                                .replace(R.id.torrent_detail_container, frag, TorrentDetailFragment.TAG)
                                .commit();
                    }
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
                    float value = ((Float) animation.getAnimatedValue()).floatValue();
                    LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)
                            detailPanel.getLayoutParams();

                    params.weight = value;
                    slidingLayout.requestLayout();
                }

            });
        }

        if (savedInstanceState != null) {
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
    public void onItemSelected(Torrent torrent) {
        if (mTwoPane) {
            int current = mTorrents.indexOf(torrent);

            FragmentManager manager = getSupportFragmentManager();
            TorrentDetailFragment fragment = (TorrentDetailFragment) manager.findFragmentByTag(
                    TorrentDetailFragment.TAG);
            if (fragment == null) {
                mDetailArguments = new Bundle();
                mDetailArguments.putInt(G.ARG_PAGE_POSITION, current);
            } else {
                fragment.setCurrentTorrent(current);
            }
            toggleRightPane(true);
        } else {
            // In single-pane mode, simply start the detail activity
            // for the selected item ID.
            Intent detailIntent = new Intent(this, TorrentDetailActivity.class);
            detailIntent.putExtra(G.ARG_PAGE_POSITION, mTorrents.indexOf(torrent));
            detailIntent.putExtra(G.ARG_PROFILE, mProfile);
            detailIntent.putParcelableArrayListExtra(TorrentDetailActivity.ARG_TORRENTS, mTorrents);
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

        getMenuInflater().inflate(R.menu.torrent_list_activity, menu);

        if (mSession == null) {
            menu.findItem(R.id.menu_session_settings).setVisible(false);
        } else {
            menu.findItem(R.id.menu_session_settings).setVisible(true);
        }

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
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_INTENT_CONSUMED, mIntentConsumed);
        outState.putInt(STATE_LOCATION_POSITION, mLocationPosition);
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
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED,
                        findViewById(R.id.sliding_menu_frame));
                mDrawerToggle.setDrawerIndicatorEnabled(false);

                Loader<TransmissionData> loader =
                        getSupportLoaderManager().getLoader(G.TORRENTS_LOADER_ID);
                if (loader != null) {
                    ((TransmissionDataLoader) loader).setAllCurrentTorrents(true);
                }

                invalidateOptionsMenu();
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
                mDrawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED,
                        findViewById(R.id.sliding_menu_frame));
                mDrawerToggle.setDrawerIndicatorEnabled(true);
                Loader<TransmissionData> loader = getSupportLoaderManager().getLoader(G.TORRENTS_LOADER_ID);
                if (loader != null) {
                    ((TransmissionDataLoader) loader).setAllCurrentTorrents(false);
                }

                invalidateOptionsMenu();
                return true;
            }
        }
        return false;
    }

    @Override
    public void setTorrents(ArrayList<Torrent> torrents) {
        mTorrents.clear();
        if (torrents != null) {
            mTorrents.addAll(torrents);
        }
        if (mTorrents.size() == 0) {
            toggleRightPane(false);
        }
    }

    @Override
    public ArrayList<Torrent> getTorrents() {
        return mTorrents;
    }

    @Override
    public Torrent[] getCurrentTorrents() {
        if (!isDetailPanelShown()) return new Torrent[] {};

        return mTorrents.toArray(new Torrent[mTorrents.size()]);
    }

    @Override
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

            mSession = session;
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
        FragmentManager manager = getSupportFragmentManager();
        TorrentListFragment list = (TorrentListFragment) manager.findFragmentById(R.id.torrent_list);

        if (list != null) {
            list.setRefreshing(refreshing);
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

            ((CheckBox) view.findViewById(R.id.start_paused)).setChecked(mSharedPrefs.getBoolean(G.PREF_START_PAUSED, false));

            final CheckBox deleteLocal = ((CheckBox) view.findViewById(R.id.delete_local));
            deleteLocal.setChecked(mSharedPrefs.getBoolean(G.PREF_DELETE_LOCAL, false));

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
                                filedata.append(line + "\n");
                            }

                            file.delete();

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
}
