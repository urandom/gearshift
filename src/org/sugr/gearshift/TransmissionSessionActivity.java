package org.sugr.gearshift;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.sugr.gearshift.TransmissionSessionManager.ManagerException;
import org.sugr.gearshift.TransmissionSessionManager.TransmissionExclusionStrategy;

import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.Html;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TransmissionSessionActivity extends FragmentActivity {
    private TransmissionProfile mProfile;
    private TransmissionSession mSession;

    private boolean mRefreshing = false;

    private LoaderCallbacks<TransmissionData> mSessionLoaderCallbacks = new LoaderCallbacks<TransmissionData>() {

        @Override public Loader<TransmissionData> onCreateLoader(int arg0, Bundle arg1) {
            if (mProfile == null) return null;

            return new TransmissionSessionLoader(
                    TransmissionSessionActivity.this, mProfile);
        }

        @Override public void onLoadFinished(Loader<TransmissionData> loader,
                TransmissionData data) {

            if (data.session == null) {
                mSession = null;
                if (data.error > 0) {
                    findViewById(R.id.fatal_error_layer).setVisibility(View.VISIBLE);
                    TextView text = (TextView) findViewById(R.id.transmission_error);

                    if (data.error == TransmissionData.Errors.NO_CONNECTIVITY) {
                        text.setText(Html.fromHtml(getString(R.string.no_connectivity_empty_list)));
                    } else if (data.error == TransmissionData.Errors.ACCESS_DENIED) {
                        text.setText(Html.fromHtml(getString(R.string.access_denied_empty_list)));
                    } else if (data.error == TransmissionData.Errors.NO_JSON) {
                        text.setText(Html.fromHtml(getString(R.string.no_json_empty_list)));
                    } else if (data.error == TransmissionData.Errors.NO_CONNECTION) {
                        text.setText(Html.fromHtml(getString(R.string.no_connection_empty_list)));
                    } else if (data.error == TransmissionData.Errors.THREAD_ERROR) {
                        text.setText(Html.fromHtml(getString(R.string.thread_error_empty_list)));
                    } else if (data.error == TransmissionData.Errors.RESPONSE_ERROR) {
                        text.setText(Html.fromHtml(getString(R.string.response_error_empty_list)));
                    } else if (data.error == TransmissionData.Errors.TIMEOUT) {
                        text.setText(Html.fromHtml(getString(R.string.timeout_empty_list)));
                    }
                }
            } else {
                findViewById(R.id.fatal_error_layer).setVisibility(View.GONE);
                if (mSession == null) {
                    mSession = data.session;
                    updateFields(null, true);
                } else {
                    updateFields(data.session, false);
                }
            }

            if (mRefreshing) {
                mRefreshing = false;
                invalidateOptionsMenu();
            }
        }

        @Override public void onLoaderReset(Loader<TransmissionData> loader) {
        }
    };

    private static final String STATE_EXPANDED = "expanded_states";
    private static final String STATE_SCROLL_POSITION = "scroll_position_state";

    private static class Expanders {
        public static final int TOTAL_EXPANDERS = 4;

        public static final int GENERAL = 0;
        public static final int CONNECTIONS = 1;
        public static final int BANDWIDTH = 2;
        public static final int LIMITS = 3;
    }

    private boolean[] mExpandedStates = new boolean[Expanders.TOTAL_EXPANDERS];

    private List<String> mEncryptionValues;

    private View.OnClickListener mExpanderListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            View image;
            View content;
            int index;
            switch(v.getId()) {
                case R.id.transmission_session_general_expander:
                    image = v.findViewById(R.id.transmission_session_general_expander_image);
                    content = findViewById(R.id.transmission_session_general_content);
                    index = Expanders.GENERAL;
                    break;
                case R.id.transmission_session_connections_expander:
                    image = v.findViewById(R.id.transmission_session_connections_expander_image);
                    content = findViewById(R.id.transmission_session_connections_content);
                    index = Expanders.CONNECTIONS;
                    break;
                case R.id.transmission_session_bandwidth_expander:
                    image = v.findViewById(R.id.transmission_session_bandwidth_expander_image);
                    content = findViewById(R.id.transmission_session_bandwidth_content);
                    index = Expanders.BANDWIDTH;
                    break;
                case R.id.transmission_session_limits_expander:
                    image = v.findViewById(R.id.transmission_session_limits_expander_image);
                    content = findViewById(R.id.transmission_session_limits_content);
                    index = Expanders.LIMITS;
                    break;
                default:
                    return;
            }

            if (content.getVisibility() == View.GONE) {
                content.setVisibility(View.VISIBLE);
                content.setAlpha(0);
                content.animate().alpha(1);
                image.setBackgroundResource(R.drawable.ic_section_collapse);
                mExpandedStates[index] = true;
            } else {
                content.setVisibility(View.GONE);
                image.setBackgroundResource(R.drawable.ic_section_expand);
                mExpandedStates[index] = false;
            }

        }
    };


    private Runnable mLoseFocus = new Runnable() {
        @Override public void run() {
            findViewById(R.id.transmission_session_container).requestFocus();
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Intent in = getIntent();
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();

        mProfile = in.getParcelableExtra(G.ARG_PROFILE);
        mSession = gson.fromJson(in.getStringExtra(G.ARG_JSON_SESSION), TransmissionSession.class);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_transmission_session);

        if (savedInstanceState != null) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (savedInstanceState.containsKey(STATE_EXPANDED)) {
                        mExpandedStates = savedInstanceState.getBooleanArray(STATE_EXPANDED);
                        findViewById(R.id.transmission_session_general_content).setVisibility(mExpandedStates[Expanders.GENERAL] ? View.VISIBLE : View.GONE);
                        findViewById(R.id.transmission_session_connections_content).setVisibility(mExpandedStates[Expanders.CONNECTIONS] ? View.VISIBLE : View.GONE);
                        findViewById(R.id.transmission_session_bandwidth_content).setVisibility(mExpandedStates[Expanders.BANDWIDTH] ? View.VISIBLE : View.GONE);
                        findViewById(R.id.transmission_session_limits_content).setVisibility(mExpandedStates[Expanders.LIMITS] ? View.VISIBLE : View.GONE);
                    }
                    if (savedInstanceState.containsKey(STATE_SCROLL_POSITION)) {
                        final int position = savedInstanceState.getInt(STATE_SCROLL_POSITION);
                        final ScrollView scroll = (ScrollView) findViewById(R.id.session_scroll);
                        scroll.scrollTo(0, position);
                    }
                }
            });
        } else {
            mExpandedStates[Expanders.GENERAL] = findViewById(R.id.transmission_session_general_content).getVisibility() != View.GONE;
            mExpandedStates[Expanders.CONNECTIONS] = findViewById(R.id.transmission_session_connections_content).getVisibility() != View.GONE;
            mExpandedStates[Expanders.BANDWIDTH] = findViewById(R.id.transmission_session_bandwidth_content).getVisibility() != View.GONE;
            mExpandedStates[Expanders.LIMITS] = findViewById(R.id.transmission_session_limits_content).getVisibility() != View.GONE;
        }

        mEncryptionValues = Arrays.asList(getResources().getStringArray(R.array.session_settings_encryption_values));

        updateFields(null, true);

        initListeners();

        getSupportLoaderManager().restartLoader(
                G.SESSION_LOADER_ID, null, mSessionLoaderCallbacks);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Loader<TransmissionData> loader = getSupportLoaderManager()
            .getLoader(G.SESSION_LOADER_ID);

        loader.onContentChanged();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBooleanArray(STATE_EXPANDED, mExpandedStates);
        ScrollView scroll = (ScrollView) findViewById(R.id.session_scroll);
        if (scroll != null) {
            outState.putInt(STATE_SCROLL_POSITION, scroll.getScrollY());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.transmission_session_activity, menu);

        MenuItem item = menu.findItem(R.id.menu_refresh);
        if (mRefreshing)
            item.setActionView(R.layout.action_progress_bar);
        else
            item.setActionView(null);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Loader<TransmissionData> loader;

        switch (item.getItemId()) {
            case R.id.menu_refresh:
                loader = getSupportLoaderManager()
                    .getLoader(G.SESSION_LOADER_ID);
                if (loader != null) {
                    loader.onContentChanged();
                    mRefreshing = !mRefreshing;
                    invalidateOptionsMenu();
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void setAltSpeedLimitTimeBegin(int time) {
        mSession.setAltSpeedTimeBegin(time);
        setSession(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_BEGIN);
    }

    public void setAltSpeedLimitTimeEnd(int time) {
        mSession.setAltSpeedTimeEnd(time);
        setSession(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_END);
    }

    private void updateFields(TransmissionSession session, boolean initial) {
        if (initial || !mSession.getDownloadDir().equals(session.getDownloadDir())) {
            if (!initial)
                mSession.setDownloadDir(session.getDownloadDir());
            ((EditText) findViewById(R.id.transmission_session_download_directory))
                .setText(mSession.getDownloadDir());
        }

        if (initial || mSession.isIncompleteDirEnabled() != session.isIncompleteDirEnabled()) {
            if (!initial)
                mSession.setIncompleteDirEnabled(session.isIncompleteDirEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_incomplete_download_check))
                .setChecked(mSession.isIncompleteDirEnabled());
            findViewById(R.id.transmission_session_incomplete_download_directory).setEnabled(mSession.isIncompleteDirEnabled());
        }

        if (initial || !mSession.getIncompleteDir().equals(session.getIncompleteDir())) {
            if (!initial)
                mSession.setIncompleteDir(session.getIncompleteDir());
            ((EditText) findViewById(R.id.transmission_session_incomplete_download_directory))
                .setText(mSession.getIncompleteDir());
        }

        if (initial || mSession.isDoneScriptEnabled() != session.isDoneScriptEnabled()) {
            if (!initial)
                mSession.setDoneScriptEnabled(session.isDoneScriptEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_done_script_check))
                .setChecked(mSession.isDoneScriptEnabled());
            findViewById(R.id.transmission_session_done_script).setEnabled(mSession.isDoneScriptEnabled());
        }

        if (initial || !mSession.getDoneScript().equals(session.getDoneScript())) {
            if (!initial)
                mSession.setDoneScript(session.getDoneScript());
            ((EditText) findViewById(R.id.transmission_session_done_script))
                .setText(mSession.getDoneScript());
        }

        if (initial || mSession.getCacheSize() != session.getCacheSize()) {
            if (!initial)
                mSession.setCacheSize(session.getCacheSize());
            ((EditText) findViewById(R.id.transmission_session_cache_size))
                .setText(Long.toString(mSession.getCacheSize()));
        }

        if (initial || mSession.isRenamePartialFilesEnabled() != session.isRenamePartialFilesEnabled()) {
            if (!initial)
                mSession.setRenamePartialFilesEnabled(session.isRenamePartialFilesEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_rename_partial_check))
                .setChecked(mSession.isRenamePartialFilesEnabled());
        }

        if (initial || mSession.isTrashOriginalTorrentFilesEnabled() != session.isTrashOriginalTorrentFilesEnabled()) {
            if (!initial)
                mSession.setTrashOriginalTorrentFilesEnabled(session.isTrashOriginalTorrentFilesEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_trash_original_check))
                .setChecked(mSession.isTrashOriginalTorrentFilesEnabled());
        }

        if (initial || mSession.isStartAddedTorrentsEnabled() != session.isStartAddedTorrentsEnabled()) {
            if (!initial)
                mSession.setStartAddedTorrentsEnabled(session.isStartAddedTorrentsEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_start_added_check))
                .setChecked(mSession.isStartAddedTorrentsEnabled());
        }

        if (initial || mSession.getPeerPort() != session.getPeerPort()) {
            if (!initial)
                mSession.setPeerPort(session.getPeerPort());
            ((EditText) findViewById(R.id.transmission_session_peer_port))
                .setText(Long.toString(mSession.getPeerPort()));
            ((Button) findViewById(R.id.transmission_session_port_test))
                .setText(R.string.session_settings_port_test);
        }

        if (initial || mSession.getEncryption() != session.getEncryption()) {
            if (!initial)
                mSession.setEncryption(session.getEncryption());
            ((Spinner) findViewById(R.id.transmission_session_encryption))
                .setSelection(mEncryptionValues.indexOf(mSession.getEncryption()));
        }

        if (initial || mSession.isPeerPortRandomOnStart() != session.isPeerPortRandomOnStart()) {
            if (!initial)
                mSession.setPeerPortRandomOnStart(session.isPeerPortRandomOnStart());
            ((CheckBox) findViewById(R.id.transmission_session_random_port))
                .setChecked(mSession.isPeerPortRandomOnStart());
        }

        if (initial || mSession.isPortForwardingEnabled() != session.isPortForwardingEnabled()) {
            if (!initial)
                mSession.setPortForwardingEnabled(session.isPortForwardingEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_port_forwarding))
                .setChecked(mSession.isPortForwardingEnabled());
        }

        if (initial || mSession.isPeerExchangeEnabled() != session.isPeerExchangeEnabled()) {
            if (!initial)
                mSession.setPeerExchangeEnabled(session.isPeerExchangeEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_peer_exchange))
                .setChecked(mSession.isPeerExchangeEnabled());
        }

        if (initial || mSession.isDHTEnabled() != session.isDHTEnabled()) {
            if (!initial)
                mSession.setDHTEnabled(session.isDHTEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_hash_table))
                .setChecked(mSession.isDHTEnabled());
        }

        if (initial || mSession.isLocalDiscoveryEnabled() != session.isLocalDiscoveryEnabled()) {
            if (!initial)
                mSession.setLocalDiscoveryEnabled(session.isLocalDiscoveryEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_local_discovery))
                .setChecked(mSession.isLocalDiscoveryEnabled());
        }

        if (initial || mSession.isBlocklistEnabled() != session.isBlocklistEnabled()) {
            if (!initial)
                mSession.setBlocklistEnabled(session.isBlocklistEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_blocklist_check))
                .setChecked(mSession.isBlocklistEnabled());
            findViewById(R.id.transmission_session_blocklist_url).setEnabled(mSession.isBlocklistEnabled());
            findViewById(R.id.transmission_session_blocklist_update).setEnabled(mSession.isBlocklistEnabled());
        }

        if (initial || mSession.getBlocklistURL() != session.getBlocklistURL()) {
            if (!initial)
                mSession.setBlocklistURL(session.getBlocklistURL());
            ((EditText) findViewById(R.id.transmission_session_blocklist_url))
                .setText(mSession.getBlocklistURL());
        }

        if (initial || mSession.getBlocklistSize() != session.getBlocklistSize()) {
            if (!initial)
                mSession.setBlocklistSize(session.getBlocklistSize());
            ((TextView) findViewById(R.id.transmission_session_blocklist_size)).setText(String.format(
                getString(R.string.session_settings_blocklist_count_format),
                mSession.getBlocklistSize()
            ));
        }

        if (initial || mSession.isDownloadSpeedLimitEnabled() != session.isDownloadSpeedLimitEnabled()) {
            if (!initial)
                mSession.setDownloadSpeedLimitEnabled(session.isDownloadSpeedLimitEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_down_limit_check))
                .setChecked(mSession.isDownloadSpeedLimitEnabled());
            findViewById(R.id.transmission_session_down_limit).setEnabled(mSession.isDownloadSpeedLimitEnabled());
        }

        if (initial || mSession.getDownloadSpeedLimit() != session.getDownloadSpeedLimit()) {
            if (!initial)
                mSession.setDownloadSpeedLimit(session.getDownloadSpeedLimit());
            ((EditText) findViewById(R.id.transmission_session_down_limit))
                .setText(Long.toString(mSession.getDownloadSpeedLimit()));
        }

        if (initial || mSession.isUploadSpeedLimitEnabled() != session.isUploadSpeedLimitEnabled()) {
            if (!initial)
                mSession.setUploadSpeedLimitEnabled(session.isUploadSpeedLimitEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_up_limit_check))
                .setChecked(mSession.isUploadSpeedLimitEnabled());
            findViewById(R.id.transmission_session_up_limit).setEnabled(mSession.isUploadSpeedLimitEnabled());
        }

        if (initial || mSession.getUploadSpeedLimit() != session.getUploadSpeedLimit()) {
            if (!initial)
                mSession.setUploadSpeedLimit(session.getUploadSpeedLimit());
            ((EditText) findViewById(R.id.transmission_session_up_limit))
                .setText(Long.toString(mSession.getUploadSpeedLimit()));
        }

        if (initial || mSession.isAltSpeedLimitEnabled() != session.isAltSpeedLimitEnabled()) {
            if (!initial)
                mSession.setAltSpeedLimitEnabled(session.isAltSpeedLimitEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_alt_limits_check))
                .setChecked(mSession.isAltSpeedLimitEnabled());
            findViewById(R.id.transmission_session_alt_down_limit).setEnabled(mSession.isAltSpeedLimitEnabled());
            findViewById(R.id.transmission_session_alt_up_limit).setEnabled(mSession.isAltSpeedLimitEnabled());
        }

        if (initial || mSession.getAltDownloadSpeedLimit() != session.getAltDownloadSpeedLimit()) {
            if (!initial)
                mSession.setAltDownloadSpeedLimit(session.getAltDownloadSpeedLimit());
            ((EditText) findViewById(R.id.transmission_session_alt_down_limit))
                .setText(Long.toString(mSession.getAltDownloadSpeedLimit()));
        }

        if (initial || mSession.getAltUploadSpeedLimit() != session.getAltUploadSpeedLimit()) {
            if (!initial)
                mSession.setAltUploadSpeedLimit(session.getAltUploadSpeedLimit());
            ((EditText) findViewById(R.id.transmission_session_alt_up_limit))
                .setText(Long.toString(mSession.getAltUploadSpeedLimit()));
        }

        if (initial || mSession.isAltSpeedLimitTimeEnabled() != session.isAltSpeedLimitTimeEnabled()) {
            if (!initial)
                mSession.setAltSpeedLimitTimeEnabled(session.isAltSpeedLimitTimeEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_alt_limits_time_check))
                .setChecked(mSession.isAltSpeedLimitTimeEnabled());
            findViewById(R.id.transmission_session_alt_limit_time_from).setEnabled(mSession.isAltSpeedLimitTimeEnabled());
            findViewById(R.id.transmission_session_alt_limit_time_to).setEnabled(mSession.isAltSpeedLimitTimeEnabled());
        }

        if (initial || mSession.getAltSpeedTimeBegin() != session.getAltSpeedTimeBegin()) {
            if (!initial)
                mSession.setAltSpeedTimeBegin(session.getAltSpeedTimeBegin());
            ((Button) findViewById(R.id.transmission_session_alt_limit_time_from))
                .setText(String.format(
                    getString(R.string.session_settings_alt_limit_time_format),
                    mSession.getAltSpeedTimeBegin() / 60,
                    mSession.getAltSpeedTimeBegin() % 60));
        }

        if (initial || mSession.getAltSpeedTimeEnd() != session.getAltSpeedTimeEnd()) {
            if (!initial)
                mSession.setAltSpeedTimeEnd(session.getAltSpeedTimeEnd());
            ((Button) findViewById(R.id.transmission_session_alt_limit_time_to))
                .setText(String.format(
                    getString(R.string.session_settings_alt_limit_time_format),
                    mSession.getAltSpeedTimeEnd() / 60,
                    mSession.getAltSpeedTimeEnd() % 60));
        }

        if (initial || mSession.isUploadSpeedLimitEnabled() != session.isUploadSpeedLimitEnabled()) {
            if (!initial)
                mSession.setUploadSpeedLimitEnabled(session.isUploadSpeedLimitEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_up_limit_check))
                .setChecked(mSession.isUploadSpeedLimitEnabled());
            findViewById(R.id.transmission_session_up_limit).setEnabled(mSession.isUploadSpeedLimitEnabled());
        }

        if (initial || mSession.getUploadSpeedLimit() != session.getUploadSpeedLimit()) {
            if (!initial)
                mSession.setUploadSpeedLimit(session.getUploadSpeedLimit());
            ((EditText) findViewById(R.id.transmission_session_up_limit))
                .setText(Long.toString(mSession.getUploadSpeedLimit()));
        }

        if (initial || mSession.isSeedRatioLimitEnabled() != session.isSeedRatioLimitEnabled()) {
            if (!initial)
                mSession.setSeedRatioLimitEnabled(session.isSeedRatioLimitEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_seed_ratio_limit_check))
                .setChecked(mSession.isSeedRatioLimitEnabled());
            findViewById(R.id.transmission_session_seed_ratio_limit).setEnabled(mSession.isSeedRatioLimitEnabled());
        }

        if (initial || mSession.getSeedRatioLimit() != session.getSeedRatioLimit()) {
            if (!initial)
                mSession.setSeedRatioLimit(session.getSeedRatioLimit());
            ((EditText) findViewById(R.id.transmission_session_seed_ratio_limit))
                .setText(Float.toString(mSession.getSeedRatioLimit()));
        }

        if (initial || mSession.isDownloadQueueEnabled() != session.isDownloadQueueEnabled()) {
            if (!initial)
                mSession.setDownloadQueueEnabled(session.isDownloadQueueEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_download_queue_size_check))
                .setChecked(mSession.isDownloadQueueEnabled());
            findViewById(R.id.transmission_session_download_queue_size).setEnabled(mSession.isDownloadQueueEnabled());
        }

        if (initial || mSession.getDownloadQueueSize() != session.getDownloadQueueSize()) {
            if (!initial)
                mSession.setDownloadQueueSize(session.getDownloadQueueSize());
            ((EditText) findViewById(R.id.transmission_session_download_queue_size))
                .setText(Integer.toString(mSession.getDownloadQueueSize()));
        }

        if (initial || mSession.isSeedQueueEnabled() != session.isSeedQueueEnabled()) {
            if (!initial)
                mSession.setSeedQueueEnabled(session.isSeedQueueEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_seed_queue_size_check))
                .setChecked(mSession.isSeedQueueEnabled());
            findViewById(R.id.transmission_session_seed_queue_size).setEnabled(mSession.isSeedQueueEnabled());
        }

        if (initial || mSession.getSeedQueueSize() != session.getSeedQueueSize()) {
            if (!initial)
                mSession.setSeedQueueSize(session.getSeedQueueSize());
            ((EditText) findViewById(R.id.transmission_session_seed_queue_size))
                .setText(Integer.toString(mSession.getSeedQueueSize()));
        }

        if (initial || mSession.isStalledQueueEnabled() != session.isStalledQueueEnabled()) {
            if (!initial)
                mSession.setStalledQueueEnabled(session.isStalledQueueEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_stalled_queue_size_check))
                .setChecked(mSession.isStalledQueueEnabled());
            findViewById(R.id.transmission_session_stalled_queue_size).setEnabled(mSession.isStalledQueueEnabled());
        }

        if (initial || mSession.getStalledQueueSize() != session.getStalledQueueSize()) {
            if (!initial)
                mSession.setStalledQueueSize(session.getStalledQueueSize());
            ((EditText) findViewById(R.id.transmission_session_stalled_queue_size))
                .setText(Integer.toString(mSession.getStalledQueueSize()));
        }

        if (initial || mSession.getGlobalPeerLimit() != session.getGlobalPeerLimit()) {
            if (!initial)
                mSession.setGlobalPeerLimit(session.getGlobalPeerLimit());
            ((EditText) findViewById(R.id.transmission_session_global_peer_limit))
                .setText(Integer.toString(mSession.getGlobalPeerLimit()));
        }

        if (initial || mSession.getTorrentPeerLimit() != session.getTorrentPeerLimit()) {
            if (!initial)
                mSession.setTorrentPeerLimit(session.getTorrentPeerLimit());
            ((EditText) findViewById(R.id.transmission_session_torrent_peer_limit))
                .setText(Integer.toString(mSession.getTorrentPeerLimit()));
        }

    }

    private void initListeners() {
        findViewById(R.id.transmission_session_general_expander).setOnClickListener(mExpanderListener);
        findViewById(R.id.transmission_session_connections_expander).setOnClickListener(mExpanderListener);
        findViewById(R.id.transmission_session_bandwidth_expander).setOnClickListener(mExpanderListener);
        findViewById(R.id.transmission_session_limits_expander).setOnClickListener(mExpanderListener);

        CheckBox check = null;
        EditText edit = null;

        edit = (EditText) findViewById(R.id.transmission_session_download_directory);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String value = v.getText().toString().trim();
                    if (!mSession.getDownloadDir().equals(value)) {
                        mSession.setDownloadDir(value);
                        setSession(TransmissionSession.SetterFields.DOWNLOAD_DIR);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_incomplete_download_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                findViewById(R.id.transmission_session_incomplete_download_directory).setEnabled(isChecked);
                if (mSession.isIncompleteDirEnabled() != isChecked) {
                    mSession.setIncompleteDirEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.INCOMPLETE_DIR_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_incomplete_download_directory);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String value = v.getText().toString().trim();
                    if (!mSession.getIncompleteDir().equals(value)) {
                        mSession.setIncompleteDir(value);
                        setSession(TransmissionSession.SetterFields.INCOMPLETE_DIR);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_done_script_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                findViewById(R.id.transmission_session_done_script).setEnabled(isChecked);
                if (mSession.isDoneScriptEnabled() != isChecked) {
                    mSession.setDoneScriptEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.DONE_SCRIPT_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_done_script);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String value = v.getText().toString().trim();
                    if (!mSession.getDoneScript().equals(value)) {
                        mSession.setDoneScript(value);
                        setSession(TransmissionSession.SetterFields.DONE_SCRIPT);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_cache_size);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    long value;
                    try {
                        value = Long.parseLong(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mSession.getCacheSize() != value) {
                        mSession.setCacheSize(value);
                        setSession(TransmissionSession.SetterFields.CACHE_SIZE);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_rename_partial_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mSession.isRenamePartialFilesEnabled() != isChecked) {
                    mSession.setRenamePartialFilesEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.RENAME_PARTIAL);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_trash_original_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mSession.isTrashOriginalTorrentFilesEnabled() != isChecked) {
                    mSession.setTrashOriginalTorrentFilesEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.TRASH_ORIGINAL);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_start_added_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mSession.isStartAddedTorrentsEnabled() != isChecked) {
                    mSession.setStartAddedTorrentsEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.START_ADDED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_peer_port);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int value;
                    try {
                        value = Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (value > 65535) {
                        value = 65535;
                        v.setText(Integer.valueOf(value));
                    }
                    if (mSession.getPeerPort() != value) {
                        mSession.setPeerPort(value);
                        setSession(TransmissionSession.SetterFields.PEER_PORT);
                        ((Button) findViewById(R.id.transmission_session_port_test))
                            .setText(R.string.session_settings_port_test);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        Button button = (Button) findViewById(R.id.transmission_session_port_test);
        button.setOnClickListener(new View.OnClickListener() {
            @Override  public void onClick(View v) {
                new PortTestAsyncTask().execute();
            }
        });

        Spinner spinner = (Spinner) findViewById(R.id.transmission_session_encryption);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String value = mEncryptionValues.get(pos);
                if (!mSession.getEncryption().equals(value)) {
                    mSession.setEncryption(value);
                    setSession(TransmissionSession.SetterFields.ENCRYPTION);
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_random_port);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mSession.isPeerPortRandomOnStart() != isChecked) {
                    mSession.setPeerPortRandomOnStart(isChecked);
                    setSession(TransmissionSession.SetterFields.RANDOM_PORT);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_port_forwarding);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mSession.isPortForwardingEnabled() != isChecked) {
                    mSession.setPortForwardingEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.PORT_FORWARDING);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_peer_exchange);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mSession.isPeerExchangeEnabled() != isChecked) {
                    mSession.setPeerExchangeEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.PEER_EXCHANGE);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_hash_table);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mSession.isDHTEnabled() != isChecked) {
                    mSession.setDHTEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.DHT);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_local_discovery);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mSession.isLocalDiscoveryEnabled() != isChecked) {
                    mSession.setLocalDiscoveryEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.LOCAL_DISCOVERY);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_blocklist_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                findViewById(R.id.transmission_session_blocklist_update).setEnabled(isChecked);
                findViewById(R.id.transmission_session_blocklist_url).setEnabled(isChecked);
                if (mSession.isBlocklistEnabled() != isChecked) {
                    mSession.setBlocklistEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.BLOCKLIST_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_blocklist_url);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String value = v.getText().toString().trim();
                    if (!mSession.getBlocklistURL().equals(value)) {
                        mSession.setBlocklistURL(value);
                        setSession(TransmissionSession.SetterFields.BLOCKLIST_URL);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        button = (Button) findViewById(R.id.transmission_session_blocklist_update);
        button.setOnClickListener(new View.OnClickListener() {
            @Override  public void onClick(View v) {
                new BlocklistUpdateAsyncTask().execute();
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_down_limit_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                findViewById(R.id.transmission_session_down_limit).setEnabled(isChecked);
                if (mSession.isDownloadSpeedLimitEnabled() != isChecked) {
                    mSession.setDownloadSpeedLimitEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.DOWNLOAD_SPEED_LIMIT_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_down_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    long value;
                    try {
                        value = Long.parseLong(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mSession.getDownloadSpeedLimit() != value) {
                        mSession.setDownloadSpeedLimit(value);
                        setSession(TransmissionSession.SetterFields.DOWNLOAD_SPEED_LIMIT);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_up_limit_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                findViewById(R.id.transmission_session_up_limit).setEnabled(isChecked);
                if (mSession.isUploadSpeedLimitEnabled() != isChecked) {
                    mSession.setUploadSpeedLimitEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.UPLOAD_SPEED_LIMIT_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_up_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    long value;
                    try {
                        value = Long.parseLong(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mSession.getUploadSpeedLimit() != value) {
                        mSession.setUploadSpeedLimit(value);
                        setSession(TransmissionSession.SetterFields.UPLOAD_SPEED_LIMIT);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_alt_limits_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                findViewById(R.id.transmission_session_alt_down_limit).setEnabled(isChecked);
                findViewById(R.id.transmission_session_alt_up_limit).setEnabled(isChecked);
                if (mSession.isAltSpeedLimitEnabled() != isChecked) {
                    mSession.setAltSpeedLimitEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_alt_down_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    long value;
                    try {
                        value = Long.parseLong(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mSession.getAltDownloadSpeedLimit() != value) {
                        mSession.setAltDownloadSpeedLimit(value);
                        setSession(TransmissionSession.SetterFields.ALT_DOWNLOAD_SPEED_LIMIT);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_alt_up_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    long value;
                    try {
                        value = Long.parseLong(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mSession.getAltUploadSpeedLimit() != value) {
                        mSession.setAltUploadSpeedLimit(value);
                        setSession(TransmissionSession.SetterFields.ALT_UPLOAD_SPEED_LIMIT);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_alt_limits_time_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                findViewById(R.id.transmission_session_alt_limit_time_from).setEnabled(isChecked);
                findViewById(R.id.transmission_session_alt_limit_time_to).setEnabled(isChecked);
                if (mSession.isAltSpeedLimitTimeEnabled() != isChecked) {
                    mSession.setAltSpeedLimitTimeEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_ENABLED);
                }
            }
        });

        button = (Button) findViewById(R.id.transmission_session_alt_limit_time_from);
        button.setOnClickListener(new View.OnClickListener() {
            @Override  public void onClick(View v) {
                showTimePickerDialog(true,
                    mSession.getAltSpeedTimeBegin() / 60,
                    mSession.getAltSpeedTimeBegin() % 60);
            }
        });

        button = (Button) findViewById(R.id.transmission_session_alt_limit_time_to);
        button.setOnClickListener(new View.OnClickListener() {
            @Override  public void onClick(View v) {
                showTimePickerDialog(false,
                    mSession.getAltSpeedTimeEnd() / 60,
                    mSession.getAltSpeedTimeEnd() % 60);
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_seed_ratio_limit_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                findViewById(R.id.transmission_session_seed_ratio_limit).setEnabled(isChecked);
                if (mSession.isSeedRatioLimitEnabled() != isChecked) {
                    mSession.setSeedRatioLimitEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.SEED_RATIO_LIMIT_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_seed_ratio_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    float value;
                    try {
                        value = Float.parseFloat(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mSession.getSeedRatioLimit() != value) {
                        mSession.setSeedRatioLimit(value);
                        setSession(TransmissionSession.SetterFields.SEED_RATIO_LIMIT);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_download_queue_size_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                findViewById(R.id.transmission_session_download_queue_size).setEnabled(isChecked);
                if (mSession.isDownloadQueueEnabled() != isChecked) {
                    mSession.setDownloadQueueEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.DOWNLOAD_QUEUE_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_download_queue_size);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int value;
                    try {
                        value = Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mSession.getDownloadQueueSize() != value) {
                        mSession.setDownloadQueueSize(value);
                        setSession(TransmissionSession.SetterFields.DOWNLOAD_QUEUE_SIZE);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_seed_queue_size_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                findViewById(R.id.transmission_session_seed_queue_size).setEnabled(isChecked);
                if (mSession.isSeedQueueEnabled() != isChecked) {
                    mSession.setSeedQueueEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.SEED_QUEUE_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_seed_queue_size);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int value;
                    try {
                        value = Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mSession.getSeedQueueSize() != value) {
                        mSession.setSeedQueueSize(value);
                        setSession(TransmissionSession.SetterFields.SEED_QUEUE_SIZE);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_stalled_queue_size_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                findViewById(R.id.transmission_session_stalled_queue_size).setEnabled(isChecked);
                if (mSession.isStalledQueueEnabled() != isChecked) {
                    mSession.setStalledQueueEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.STALLED_QUEUE_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_stalled_queue_size);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int value;
                    try {
                        value = Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mSession.getStalledQueueSize() != value) {
                        mSession.setStalledQueueSize(value);
                        setSession(TransmissionSession.SetterFields.STALLED_QUEUE_SIZE);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_global_peer_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int value;
                    try {
                        value = Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mSession.getGlobalPeerLimit() != value) {
                        mSession.setGlobalPeerLimit(value);
                        setSession(TransmissionSession.SetterFields.GLOBAL_PEER_LIMIT);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_torrent_peer_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int value;
                    try {
                        value = Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mSession.getTorrentPeerLimit() != value) {
                        mSession.setTorrentPeerLimit(value);
                        setSession(TransmissionSession.SetterFields.TORRENT_PEER_LIMIT);
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

    }

    private void setSession(String... keys) {
        Loader<TransmissionData> l = getSupportLoaderManager()
            .getLoader(G.SESSION_LOADER_ID);

        TransmissionSessionLoader loader = (TransmissionSessionLoader) l;

        loader.setSession(mSession, keys);
    }

    private void showTimePickerDialog(boolean begin, int hour, int minute) {
        DialogFragment newFragment = new TimePickerFragment();
        Bundle bundle = new Bundle();
        bundle.putBoolean(TimePickerFragment.ARG_BEGIN, begin);
        bundle.putInt(TimePickerFragment.ARG_HOUR, hour);
        bundle.putInt(TimePickerFragment.ARG_MINUTE, minute);
        newFragment.setArguments(bundle);
        newFragment.show(getSupportFragmentManager(), "timePicker");
    }

    private class PortTestAsyncTask extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            TransmissionSessionManager manager = new TransmissionSessionManager(
                    TransmissionSessionActivity.this, mProfile);

            if (!manager.hasConnectivity()) {
                return null;
            }

            try {
                return manager.testPort();
            } catch (ManagerException e) {
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            Button test = (Button) TransmissionSessionActivity.this.findViewById(
                    R.id.transmission_session_port_test);

            test.setText(R.string.port_test_testing);
            test.setEnabled(false);
        }

        @Override
        protected void onPostExecute(Boolean result) {
            Button test = (Button) TransmissionSessionActivity.this.findViewById(
                    R.id.transmission_session_port_test);
            test.setEnabled(true);
            if (result == null) {
                test.setText(Html.fromHtml(getString(R.string.port_test_error)));
            } else if (result == true) {
                test.setText(Html.fromHtml(getString(R.string.port_test_open)));
            } else if (result == false) {
                test.setText(Html.fromHtml(getString(R.string.port_test_closed)));
            }

        }
    }

    private class BlocklistUpdateAsyncTask extends AsyncTask<Void, Void, Long> {
        @Override
        protected Long doInBackground(Void... params) {
            TransmissionSessionManager manager = new TransmissionSessionManager(
                    TransmissionSessionActivity.this, mProfile);

            if (!manager.hasConnectivity()) {
                return null;
            }

            try {
                return manager.blocklistUpdate();
            } catch (ManagerException e) {
                return null;
            }
        }

        @Override
        protected void onPreExecute() {
            Button update = (Button) TransmissionSessionActivity.this.findViewById(
                    R.id.transmission_session_blocklist_update);

            update.setText(R.string.blocklist_updating);
            update.setEnabled(false);
        }

        @Override
        protected void onPostExecute(Long result) {
            Button update = (Button) TransmissionSessionActivity.this.findViewById(
                    R.id.transmission_session_blocklist_update);
            update.setEnabled(true);
            if (result == null) {
                update.setText(Html.fromHtml(getString(R.string.blocklist_update_error)));
            } else {
                update.setText(R.string.session_settings_blocklist_update);
                TextView text = (TextView) TransmissionSessionActivity.this.findViewById(
                        R.id.transmission_session_blocklist_size);
                text.setText(String.format(
                        getString(R.string.session_settings_blocklist_count_format),
                        result
                ));

            }
        }
    }
}

class TransmissionSessionLoader extends AsyncTaskLoader<TransmissionData> {

    private TransmissionSessionManager mSessManager;

    private TransmissionProfile mProfile;
    private TransmissionSession mSessionSet;
    private Set<String> mSessionSetKeys = new HashSet<String>();
    private final Object mLock = new Object();
    private boolean mStopUpdates = false;

    private Handler mIntervalHandler = new Handler();
    private Runnable mIntervalRunner = new Runnable() {
        @Override
        public void run() {
            if (!mStopUpdates)
                onContentChanged();
        }
    };


    private int mLastError;

    public TransmissionSessionLoader(Context context, TransmissionProfile profile) {
        super(context);

        mProfile = profile;
        mSessManager = new TransmissionSessionManager(getContext(), mProfile);
    }

    public void setSession(TransmissionSession session, String... keys) {
        mSessionSet = session;
        synchronized(mLock) {
            for (String key : keys) {
                mSessionSetKeys.add(key);
            }
        }
        onContentChanged();
    }


    @Override
    public TransmissionData loadInBackground() {
        mIntervalHandler.removeCallbacks(mIntervalRunner);
        mStopUpdates = false;

        if (mLastError > 0) {
            mLastError = 0;
        }
        if (!mSessManager.hasConnectivity()) {
            mLastError = TransmissionData.Errors.NO_CONNECTIVITY;
            return new TransmissionData(null, null, mLastError);
        }

        G.logD("Fetching data");

        if (mSessionSet != null) {
            try {
                synchronized(mLock) {
                    mSessManager.setSession(mSessionSet,
                        mSessionSetKeys.toArray(new String[mSessionSetKeys.size()]));
                    mSessionSetKeys.clear();
                }
            } catch (ManagerException e) {
                synchronized(mLock) {
                    mSessionSetKeys.clear();
                }
                return handleError(e);
            } finally {
                mSessionSet = null;
            }
        }

        TransmissionSession session;
        try {
            session = mSessManager.getSession();
        } catch (ManagerException e) {
            return handleError(e);
        }

        return new TransmissionData(session, null, mLastError);
    }

    @Override
    public void deliverResult(TransmissionData data) {
        super.deliverResult(data);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        int update = Integer.parseInt(prefs.getString(G.PREF_UPDATE_INTERVAL, "-1"));
        if (update >= 0 && !isReset()) {
            if (update < 10) {
                update = 10;
            }
            mIntervalHandler.postDelayed(mIntervalRunner, update * 1000);
        }
    }

    private TransmissionData handleError(ManagerException e) {
        mStopUpdates = true;

        G.logD("Got an error while fetching data: " + e.getMessage() + " and this code: " + e.getCode());

        switch(e.getCode()) {
            case 401:
            case 403:
                mLastError = TransmissionData.Errors.ACCESS_DENIED;
                break;
            case 200:
                if (e.getMessage().equals("no-json")) {
                    mLastError = TransmissionData.Errors.NO_JSON;
                }
                break;
            case -1:
                if (e.getMessage().equals("timeout")) {
                    mLastError = TransmissionData.Errors.TIMEOUT;
                } else {
                    mLastError = TransmissionData.Errors.NO_CONNECTION;
                }
                break;
            case -2:
                mLastError = TransmissionData.Errors.RESPONSE_ERROR;
                G.logE("Transmission Daemon Error!", e);
                break;
            default:
                mLastError = TransmissionData.Errors.GENERIC_HTTP;
                break;
        }

        return new TransmissionData(null, null, mLastError);
    }
}

class TimePickerFragment extends DialogFragment
    implements TimePickerDialog.OnTimeSetListener {

    public static final String ARG_HOUR = "hour";
    public static final String ARG_MINUTE = "minute";
    public static final String ARG_BEGIN = "begin";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the current time as the default values for the picker
        final Calendar c = Calendar.getInstance();

        int hour, minute;

        if (getArguments().containsKey(ARG_HOUR)) {
            hour = getArguments().getInt(ARG_HOUR);
            c.set(Calendar.HOUR_OF_DAY, hour);
        } else {
            hour = c.get(Calendar.HOUR_OF_DAY);
        }

        if (getArguments().containsKey(ARG_MINUTE)) {
            minute = getArguments().getInt(ARG_MINUTE);
            c.set(Calendar.MINUTE, minute);
        } else {
            minute = c.get(Calendar.MINUTE);
        }

        // Create a new instance of TimePickerDialog and return it
        return new TimePickerDialog(getActivity(), this, hour, minute,
                DateFormat.is24HourFormat(getActivity()));
    }

    @Override
    public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
        Button button;
        if (getArguments().containsKey(ARG_BEGIN) && getArguments().getBoolean(ARG_BEGIN)) {
            button = (Button) getActivity().findViewById(R.id.transmission_session_alt_limit_time_from);
            ((TransmissionSessionActivity) getActivity()).setAltSpeedLimitTimeBegin(hourOfDay * 60 + minute);
        } else {
            button = (Button) getActivity().findViewById(R.id.transmission_session_alt_limit_time_to);
            ((TransmissionSessionActivity) getActivity()).setAltSpeedLimitTimeEnd(hourOfDay * 60 + minute);
        }

        button.setText(String.format(
                getActivity().getString(R.string.session_settings_alt_limit_time_format),
                hourOfDay,
                minute));
    }
}

