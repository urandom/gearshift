package org.sugr.gearshift;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Html;
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

import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.service.DataServiceManagerInterface;

import java.util.Arrays;
import java.util.List;

public class TransmissionSessionActivity extends FragmentActivity implements DataServiceManagerInterface {
    private TransmissionProfile profile;
    private TransmissionSession session;
    private DataServiceManager manager;

    private boolean refreshing = false;

    private ServiceReceiver serviceReceiver;

    private static final String STATE_EXPANDED = "expanded_states";
    private static final String STATE_SCROLL_POSITION = "scroll_position_state";

    private static class Expanders {
        public static final int TOTAL_EXPANDERS = 4;

        public static final int GENERAL = 0;
        public static final int CONNECTIONS = 1;
        public static final int BANDWIDTH = 2;
        public static final int LIMITS = 3;
    }

    private boolean[] expandedStates = new boolean[Expanders.TOTAL_EXPANDERS];

    private List<String> encryptionValues;

    private View.OnClickListener expanderListener = new View.OnClickListener() {
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
                expandedStates[index] = true;
            } else {
                content.setVisibility(View.GONE);
                image.setBackgroundResource(R.drawable.ic_section_expand);
                expandedStates[index] = false;
            }

        }
    };


    private Runnable loseFocusRunnable = new Runnable() {
        @Override public void run() {
            findViewById(R.id.transmission_session_container).requestFocus();
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Intent in = getIntent();

        profile = in.getParcelableExtra(G.ARG_PROFILE);
        session = in.getParcelableExtra(G.ARG_SESSION);
        manager = new DataServiceManager(this, profile.getId())
            .setSessionOnly(true).onRestoreInstanceState(savedInstanceState).startUpdating();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_transmission_session);

        if (savedInstanceState != null) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (savedInstanceState.containsKey(STATE_EXPANDED)) {
                        expandedStates= savedInstanceState.getBooleanArray(STATE_EXPANDED);
                        findViewById(R.id.transmission_session_general_content).setVisibility(expandedStates[Expanders.GENERAL] ? View.VISIBLE : View.GONE);
                        findViewById(R.id.transmission_session_connections_content).setVisibility(expandedStates[Expanders.CONNECTIONS] ? View.VISIBLE : View.GONE);
                        findViewById(R.id.transmission_session_bandwidth_content).setVisibility(expandedStates[Expanders.BANDWIDTH] ? View.VISIBLE : View.GONE);
                        findViewById(R.id.transmission_session_limits_content).setVisibility(expandedStates[Expanders.LIMITS] ? View.VISIBLE : View.GONE);
                    }
                    if (savedInstanceState.containsKey(STATE_SCROLL_POSITION)) {
                        final int position = savedInstanceState.getInt(STATE_SCROLL_POSITION);
                        final ScrollView scroll = (ScrollView) findViewById(R.id.session_scroll);
                        scroll.scrollTo(0, position);
                    }
                }
            });
        } else {
            expandedStates[Expanders.GENERAL] = findViewById(R.id.transmission_session_general_content).getVisibility() != View.GONE;
            expandedStates[Expanders.CONNECTIONS] = findViewById(R.id.transmission_session_connections_content).getVisibility() != View.GONE;
            expandedStates[Expanders.BANDWIDTH] = findViewById(R.id.transmission_session_bandwidth_content).getVisibility() != View.GONE;
            expandedStates[Expanders.LIMITS] = findViewById(R.id.transmission_session_limits_content).getVisibility() != View.GONE;
        }

        encryptionValues = Arrays.asList(getResources().getStringArray(R.array.session_settings_encryption_values));

        updateFields(null, true);

        initListeners();

        serviceReceiver = new ServiceReceiver();
    }

    @Override protected void onResume() {
        super.onResume();

        if (profile != null && manager == null) {
            manager = new DataServiceManager(this, profile.getId())
                .setDetails(true).startUpdating();
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(serviceReceiver, new IntentFilter(G.INTENT_SERVICE_ACTION_COMPLETE));

        GearShiftApplication.setActivityVisible(true);
    }

    @Override protected void onPause() {
        super.onPause();

        LocalBroadcastManager.getInstance(this).unregisterReceiver(serviceReceiver);
        if (manager != null) {
            manager.reset();
            manager = null;
        }

        GearShiftApplication.setActivityVisible(false);
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBooleanArray(STATE_EXPANDED, expandedStates);
        ScrollView scroll = (ScrollView) findViewById(R.id.session_scroll);
        if (scroll != null) {
            outState.putInt(STATE_SCROLL_POSITION, scroll.getScrollY());
        }
        if (manager != null) {
            manager.onSaveInstanceState(outState);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.transmission_session_activity, menu);

        MenuItem item = menu.findItem(R.id.menu_refresh);
        if (refreshing)
            item.setActionView(R.layout.action_progress_bar);
        else
            item.setActionView(null);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_refresh:
                new SessionTask(this).execute();
                refreshing = !refreshing;
                invalidateOptionsMenu();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public DataServiceManager getDataServiceManager() {
        return manager;
    }

    public void setAltSpeedLimitTimeBegin(int time) {
        session.setAltSpeedTimeBegin(time);
        setSession(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_BEGIN);
    }

    public void setAltSpeedLimitTimeEnd(int time) {
        session.setAltSpeedTimeEnd(time);
        setSession(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_END);
    }

    private void updateFields(TransmissionSession session, boolean initial) {
        if (initial || !this.session.getDownloadDir().equals(session.getDownloadDir())) {
            if (!initial)
                this.session.setDownloadDir(session.getDownloadDir());
            ((EditText) findViewById(R.id.transmission_session_download_directory))
                .setText(this.session.getDownloadDir());
        }

        if (initial || this.session.isIncompleteDirEnabled() != session.isIncompleteDirEnabled()) {
            if (!initial)
                this.session.setIncompleteDirEnabled(session.isIncompleteDirEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_incomplete_download_check))
                .setChecked(this.session.isIncompleteDirEnabled());
            findViewById(R.id.transmission_session_incomplete_download_directory).setEnabled(this.session.isIncompleteDirEnabled());
        }

        if (initial || !this.session.getIncompleteDir().equals(session.getIncompleteDir())) {
            if (!initial)
                this.session.setIncompleteDir(session.getIncompleteDir());
            ((EditText) findViewById(R.id.transmission_session_incomplete_download_directory))
                .setText(this.session.getIncompleteDir());
        }

        if (initial || this.session.isDoneScriptEnabled() != session.isDoneScriptEnabled()) {
            if (!initial)
                this.session.setDoneScriptEnabled(session.isDoneScriptEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_done_script_check))
                .setChecked(this.session.isDoneScriptEnabled());
            findViewById(R.id.transmission_session_done_script).setEnabled(this.session.isDoneScriptEnabled());
        }

        if (initial || !this.session.getDoneScript().equals(session.getDoneScript())) {
            if (!initial)
                this.session.setDoneScript(session.getDoneScript());
            ((EditText) findViewById(R.id.transmission_session_done_script))
                .setText(this.session.getDoneScript());
        }

        if (initial || this.session.getCacheSize() != session.getCacheSize()) {
            if (!initial)
                this.session.setCacheSize(session.getCacheSize());
            ((EditText) findViewById(R.id.transmission_session_cache_size))
                .setText(Long.toString(this.session.getCacheSize()));
        }

        if (initial || this.session.isRenamePartialFilesEnabled() != session.isRenamePartialFilesEnabled()) {
            if (!initial)
                this.session.setRenamePartialFilesEnabled(session.isRenamePartialFilesEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_rename_partial_check))
                .setChecked(this.session.isRenamePartialFilesEnabled());
        }

        if (initial || this.session.isTrashOriginalTorrentFilesEnabled() != session.isTrashOriginalTorrentFilesEnabled()) {
            if (!initial)
                this.session.setTrashOriginalTorrentFilesEnabled(session.isTrashOriginalTorrentFilesEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_trash_original_check))
                .setChecked(this.session.isTrashOriginalTorrentFilesEnabled());
        }

        if (initial || this.session.isStartAddedTorrentsEnabled() != session.isStartAddedTorrentsEnabled()) {
            if (!initial)
                this.session.setStartAddedTorrentsEnabled(session.isStartAddedTorrentsEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_start_added_check))
                .setChecked(this.session.isStartAddedTorrentsEnabled());
        }

        if (initial || this.session.getPeerPort() != session.getPeerPort()) {
            if (!initial)
                this.session.setPeerPort(session.getPeerPort());
            ((EditText) findViewById(R.id.transmission_session_peer_port))
                .setText(Long.toString(this.session.getPeerPort()));
            ((Button) findViewById(R.id.transmission_session_port_test))
                .setText(R.string.session_settings_port_test);
        }

        if (initial || this.session.getEncryption().equals(session.getEncryption())) {
            if (!initial)
                this.session.setEncryption(session.getEncryption());
            ((Spinner) findViewById(R.id.transmission_session_encryption))
                .setSelection(encryptionValues.indexOf(this.session.getEncryption()));
        }

        if (initial || this.session.isPeerPortRandomOnStart() != session.isPeerPortRandomOnStart()) {
            if (!initial)
                this.session.setPeerPortRandomOnStart(session.isPeerPortRandomOnStart());
            ((CheckBox) findViewById(R.id.transmission_session_random_port))
                .setChecked(this.session.isPeerPortRandomOnStart());
        }

        if (initial || this.session.isPortForwardingEnabled() != session.isPortForwardingEnabled()) {
            if (!initial)
                this.session.setPortForwardingEnabled(session.isPortForwardingEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_port_forwarding))
                .setChecked(this.session.isPortForwardingEnabled());
        }

        if (initial || this.session.isPeerExchangeEnabled() != session.isPeerExchangeEnabled()) {
            if (!initial)
                this.session.setPeerExchangeEnabled(session.isPeerExchangeEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_peer_exchange))
                .setChecked(this.session.isPeerExchangeEnabled());
        }

        if (initial || this.session.isDHTEnabled() != session.isDHTEnabled()) {
            if (!initial)
                this.session.setDHTEnabled(session.isDHTEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_hash_table))
                .setChecked(this.session.isDHTEnabled());
        }

        if (initial || this.session.isLocalDiscoveryEnabled() != session.isLocalDiscoveryEnabled()) {
            if (!initial)
                this.session.setLocalDiscoveryEnabled(session.isLocalDiscoveryEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_local_discovery))
                .setChecked(this.session.isLocalDiscoveryEnabled());
        }

        if (initial || this.session.isUTPEnabled() != session.isUTPEnabled()) {
            if (!initial)
                this.session.setUTPEnabled(session.isUTPEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_utp))
                    .setChecked(this.session.isUTPEnabled());
        }

        if (initial || this.session.isBlocklistEnabled() != session.isBlocklistEnabled()) {
            if (!initial)
                this.session.setBlocklistEnabled(session.isBlocklistEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_blocklist_check))
                .setChecked(this.session.isBlocklistEnabled());
            findViewById(R.id.transmission_session_blocklist_url).setEnabled(this.session.isBlocklistEnabled());
            findViewById(R.id.transmission_session_blocklist_update).setEnabled(this.session.isBlocklistEnabled());
        }

        if (initial || this.session.getBlocklistURL().equals(session.getBlocklistURL())) {
            if (!initial)
                this.session.setBlocklistURL(session.getBlocklistURL());
            ((EditText) findViewById(R.id.transmission_session_blocklist_url))
                .setText(this.session.getBlocklistURL());
        }

        if (initial || this.session.getBlocklistSize() != session.getBlocklistSize()) {
            if (!initial)
                this.session.setBlocklistSize(session.getBlocklistSize());
            ((TextView) findViewById(R.id.transmission_session_blocklist_size)).setText(String.format(
                getString(R.string.session_settings_blocklist_count_format),
                this.session.getBlocklistSize()
            ));
        }

        if (initial || this.session.isDownloadSpeedLimitEnabled() != session.isDownloadSpeedLimitEnabled()) {
            if (!initial)
                this.session.setDownloadSpeedLimitEnabled(session.isDownloadSpeedLimitEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_down_limit_check))
                .setChecked(this.session.isDownloadSpeedLimitEnabled());
            findViewById(R.id.transmission_session_down_limit).setEnabled(this.session.isDownloadSpeedLimitEnabled());
        }

        if (initial || this.session.getDownloadSpeedLimit() != session.getDownloadSpeedLimit()) {
            if (!initial)
                this.session.setDownloadSpeedLimit(session.getDownloadSpeedLimit());
            ((EditText) findViewById(R.id.transmission_session_down_limit))
                .setText(Long.toString(this.session.getDownloadSpeedLimit()));
        }

        if (initial || this.session.isUploadSpeedLimitEnabled() != session.isUploadSpeedLimitEnabled()) {
            if (!initial)
                this.session.setUploadSpeedLimitEnabled(session.isUploadSpeedLimitEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_up_limit_check))
                .setChecked(this.session.isUploadSpeedLimitEnabled());
            findViewById(R.id.transmission_session_up_limit).setEnabled(this.session.isUploadSpeedLimitEnabled());
        }

        if (initial || this.session.getUploadSpeedLimit() != session.getUploadSpeedLimit()) {
            if (!initial)
                this.session.setUploadSpeedLimit(session.getUploadSpeedLimit());
            ((EditText) findViewById(R.id.transmission_session_up_limit))
                .setText(Long.toString(this.session.getUploadSpeedLimit()));
        }

        if (initial || this.session.isAltSpeedLimitEnabled() != session.isAltSpeedLimitEnabled()) {
            if (!initial)
                this.session.setAltSpeedLimitEnabled(session.isAltSpeedLimitEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_alt_limits_check))
                .setChecked(this.session.isAltSpeedLimitEnabled());
            findViewById(R.id.transmission_session_alt_down_limit).setEnabled(this.session.isAltSpeedLimitEnabled());
            findViewById(R.id.transmission_session_alt_up_limit).setEnabled(this.session.isAltSpeedLimitEnabled());
        }

        if (initial || this.session.getAltDownloadSpeedLimit() != session.getAltDownloadSpeedLimit()) {
            if (!initial)
                this.session.setAltDownloadSpeedLimit(session.getAltDownloadSpeedLimit());
            ((EditText) findViewById(R.id.transmission_session_alt_down_limit))
                .setText(Long.toString(this.session.getAltDownloadSpeedLimit()));
        }

        if (initial || this.session.getAltUploadSpeedLimit() != session.getAltUploadSpeedLimit()) {
            if (!initial)
                this.session.setAltUploadSpeedLimit(session.getAltUploadSpeedLimit());
            ((EditText) findViewById(R.id.transmission_session_alt_up_limit))
                .setText(Long.toString(this.session.getAltUploadSpeedLimit()));
        }

        if (initial || this.session.isAltSpeedLimitTimeEnabled() != session.isAltSpeedLimitTimeEnabled()) {
            if (!initial)
                this.session.setAltSpeedLimitTimeEnabled(session.isAltSpeedLimitTimeEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_alt_limits_time_check))
                .setChecked(this.session.isAltSpeedLimitTimeEnabled());
            findViewById(R.id.transmission_session_alt_limit_time_from).setEnabled(this.session.isAltSpeedLimitTimeEnabled());
            findViewById(R.id.transmission_session_alt_limit_time_to).setEnabled(this.session.isAltSpeedLimitTimeEnabled());
        }

        if (initial || this.session.getAltSpeedTimeBegin() != session.getAltSpeedTimeBegin()) {
            if (!initial)
                this.session.setAltSpeedTimeBegin(session.getAltSpeedTimeBegin());
            ((Button) findViewById(R.id.transmission_session_alt_limit_time_from))
                .setText(String.format(
                    getString(R.string.session_settings_alt_limit_time_format),
                    this.session.getAltSpeedTimeBegin() / 60,
                    this.session.getAltSpeedTimeBegin() % 60));
        }

        if (initial || this.session.getAltSpeedTimeEnd() != session.getAltSpeedTimeEnd()) {
            if (!initial)
                this.session.setAltSpeedTimeEnd(session.getAltSpeedTimeEnd());
            ((Button) findViewById(R.id.transmission_session_alt_limit_time_to))
                .setText(String.format(
                    getString(R.string.session_settings_alt_limit_time_format),
                    this.session.getAltSpeedTimeEnd() / 60,
                    this.session.getAltSpeedTimeEnd() % 60));
        }

        if (initial || this.session.isUploadSpeedLimitEnabled() != session.isUploadSpeedLimitEnabled()) {
            if (!initial)
                this.session.setUploadSpeedLimitEnabled(session.isUploadSpeedLimitEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_up_limit_check))
                .setChecked(this.session.isUploadSpeedLimitEnabled());
            findViewById(R.id.transmission_session_up_limit).setEnabled(this.session.isUploadSpeedLimitEnabled());
        }

        if (initial || this.session.getUploadSpeedLimit() != session.getUploadSpeedLimit()) {
            if (!initial)
                this.session.setUploadSpeedLimit(session.getUploadSpeedLimit());
            ((EditText) findViewById(R.id.transmission_session_up_limit))
                .setText(Long.toString(this.session.getUploadSpeedLimit()));
        }

        if (initial || this.session.isSeedRatioLimitEnabled() != session.isSeedRatioLimitEnabled()) {
            if (!initial)
                this.session.setSeedRatioLimitEnabled(session.isSeedRatioLimitEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_seed_ratio_limit_check))
                .setChecked(this.session.isSeedRatioLimitEnabled());
            findViewById(R.id.transmission_session_seed_ratio_limit).setEnabled(this.session.isSeedRatioLimitEnabled());
        }

        if (initial || this.session.getSeedRatioLimit() != session.getSeedRatioLimit()) {
            if (!initial)
                this.session.setSeedRatioLimit(session.getSeedRatioLimit());
            ((EditText) findViewById(R.id.transmission_session_seed_ratio_limit))
                .setText(Float.toString(this.session.getSeedRatioLimit()));
        }

        if (initial || this.session.isDownloadQueueEnabled() != session.isDownloadQueueEnabled()) {
            if (!initial)
                this.session.setDownloadQueueEnabled(session.isDownloadQueueEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_download_queue_size_check))
                .setChecked(this.session.isDownloadQueueEnabled());
            findViewById(R.id.transmission_session_download_queue_size).setEnabled(this.session.isDownloadQueueEnabled());
        }

        if (initial || this.session.getDownloadQueueSize() != session.getDownloadQueueSize()) {
            if (!initial)
                this.session.setDownloadQueueSize(session.getDownloadQueueSize());
            ((EditText) findViewById(R.id.transmission_session_download_queue_size))
                .setText(Integer.toString(this.session.getDownloadQueueSize()));
        }

        if (initial || this.session.isSeedQueueEnabled() != session.isSeedQueueEnabled()) {
            if (!initial)
                this.session.setSeedQueueEnabled(session.isSeedQueueEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_seed_queue_size_check))
                .setChecked(this.session.isSeedQueueEnabled());
            findViewById(R.id.transmission_session_seed_queue_size).setEnabled(this.session.isSeedQueueEnabled());
        }

        if (initial || this.session.getSeedQueueSize() != session.getSeedQueueSize()) {
            if (!initial)
                this.session.setSeedQueueSize(session.getSeedQueueSize());
            ((EditText) findViewById(R.id.transmission_session_seed_queue_size))
                .setText(Integer.toString(this.session.getSeedQueueSize()));
        }

        if (initial || this.session.isStalledQueueEnabled() != session.isStalledQueueEnabled()) {
            if (!initial)
                this.session.setStalledQueueEnabled(session.isStalledQueueEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_stalled_queue_size_check))
                .setChecked(this.session.isStalledQueueEnabled());
            findViewById(R.id.transmission_session_stalled_queue_size).setEnabled(this.session.isStalledQueueEnabled());
        }

        if (initial || this.session.getStalledQueueSize() != session.getStalledQueueSize()) {
            if (!initial)
                this.session.setStalledQueueSize(session.getStalledQueueSize());
            ((EditText) findViewById(R.id.transmission_session_stalled_queue_size))
                .setText(Integer.toString(this.session.getStalledQueueSize()));
        }

        if (initial || this.session.getGlobalPeerLimit() != session.getGlobalPeerLimit()) {
            if (!initial)
                this.session.setGlobalPeerLimit(session.getGlobalPeerLimit());
            ((EditText) findViewById(R.id.transmission_session_global_peer_limit))
                .setText(Integer.toString(this.session.getGlobalPeerLimit()));
        }

        if (initial || this.session.getTorrentPeerLimit() != session.getTorrentPeerLimit()) {
            if (!initial)
                this.session.setTorrentPeerLimit(session.getTorrentPeerLimit());
            ((EditText) findViewById(R.id.transmission_session_torrent_peer_limit))
                .setText(Integer.toString(this.session.getTorrentPeerLimit()));
        }

    }

    private void initListeners() {
        findViewById(R.id.transmission_session_general_expander).setOnClickListener(expanderListener);
        findViewById(R.id.transmission_session_connections_expander).setOnClickListener(expanderListener);
        findViewById(R.id.transmission_session_bandwidth_expander).setOnClickListener(expanderListener);
        findViewById(R.id.transmission_session_limits_expander).setOnClickListener(expanderListener);

        CheckBox check;
        EditText edit;

        edit = (EditText) findViewById(R.id.transmission_session_download_directory);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String value = v.getText().toString().trim();
                    if (!session.getDownloadDir().equals(value)) {
                        session.setDownloadDir(value);
                        setSession(TransmissionSession.SetterFields.DOWNLOAD_DIR);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_incomplete_download_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                findViewById(R.id.transmission_session_incomplete_download_directory).setEnabled(isChecked);
                if (session.isIncompleteDirEnabled() != isChecked) {
                    session.setIncompleteDirEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.INCOMPLETE_DIR_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_incomplete_download_directory);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String value = v.getText().toString().trim();
                    if (!session.getIncompleteDir().equals(value)) {
                        session.setIncompleteDir(value);
                        setSession(TransmissionSession.SetterFields.INCOMPLETE_DIR);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_done_script_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                findViewById(R.id.transmission_session_done_script).setEnabled(isChecked);
                if (session.isDoneScriptEnabled() != isChecked) {
                    session.setDoneScriptEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.DONE_SCRIPT_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_done_script);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String value = v.getText().toString().trim();
                    if (!session.getDoneScript().equals(value)) {
                        session.setDoneScript(value);
                        setSession(TransmissionSession.SetterFields.DONE_SCRIPT);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_cache_size);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    long value;
                    try {
                        value = Long.parseLong(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (session.getCacheSize() != value) {
                        session.setCacheSize(value);
                        setSession(TransmissionSession.SetterFields.CACHE_SIZE);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_rename_partial_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                if (session.isRenamePartialFilesEnabled() != isChecked) {
                    session.setRenamePartialFilesEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.RENAME_PARTIAL);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_trash_original_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                if (session.isTrashOriginalTorrentFilesEnabled() != isChecked) {
                    session.setTrashOriginalTorrentFilesEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.TRASH_ORIGINAL);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_start_added_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                if (session.isStartAddedTorrentsEnabled() != isChecked) {
                    session.setStartAddedTorrentsEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.START_ADDED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_peer_port);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int value;
                    try {
                        value = Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (value > 65535) {
                        value = 65535;
                        v.setText(value);
                    }
                    if (session.getPeerPort() != value) {
                        session.setPeerPort(value);
                        setSession(TransmissionSession.SetterFields.PEER_PORT);
                        ((Button) findViewById(R.id.transmission_session_port_test))
                            .setText(R.string.session_settings_port_test);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        Button button = (Button) findViewById(R.id.transmission_session_port_test);
        button.setOnClickListener(new View.OnClickListener() {
            @Override  public void onClick(View v) {
                if (session == null) {
                    return;
                }
                Button test = (Button) TransmissionSessionActivity.this.findViewById(
                        R.id.transmission_session_port_test);

                test.setText(R.string.port_test_testing);
                test.setEnabled(false);

                manager.testPort();
            }
        });

        Spinner spinner = (Spinner) findViewById(R.id.transmission_session_encryption);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (session == null) {
                    return;
                }
                String value = encryptionValues.get(pos);
                if (!session.getEncryption().equals(value)) {
                    session.setEncryption(value);
                    setSession(TransmissionSession.SetterFields.ENCRYPTION);
                }
            }

            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_random_port);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                if (session.isPeerPortRandomOnStart() != isChecked) {
                    session.setPeerPortRandomOnStart(isChecked);
                    setSession(TransmissionSession.SetterFields.RANDOM_PORT);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_port_forwarding);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                if (session.isPortForwardingEnabled() != isChecked) {
                    session.setPortForwardingEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.PORT_FORWARDING);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_peer_exchange);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                if (session.isPeerExchangeEnabled() != isChecked) {
                    session.setPeerExchangeEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.PEER_EXCHANGE);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_hash_table);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                if (session.isDHTEnabled() != isChecked) {
                    session.setDHTEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.DHT);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_local_discovery);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                if (session.isLocalDiscoveryEnabled() != isChecked) {
                    session.setLocalDiscoveryEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.LOCAL_DISCOVERY);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_utp);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                if (session.isUTPEnabled() != isChecked) {
                    session.setUTPEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.UTP);
                }
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_blocklist_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                findViewById(R.id.transmission_session_blocklist_update).setEnabled(isChecked);
                findViewById(R.id.transmission_session_blocklist_url).setEnabled(isChecked);
                if (session.isBlocklistEnabled() != isChecked) {
                    session.setBlocklistEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.BLOCKLIST_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_blocklist_url);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    String value = v.getText().toString().trim();
                    if (!session.getBlocklistURL().equals(value)) {
                        session.setBlocklistURL(value);
                        setSession(TransmissionSession.SetterFields.BLOCKLIST_URL);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        button = (Button) findViewById(R.id.transmission_session_blocklist_update);
        button.setOnClickListener(new View.OnClickListener() {
            @Override  public void onClick(View v) {
                if (session == null) {
                    return;
                }
                Button update = (Button) TransmissionSessionActivity.this.findViewById(
                    R.id.transmission_session_blocklist_update);

                update.setText(R.string.blocklist_updating);
                update.setEnabled(false);

                manager.updateBlocklist();
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_down_limit_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                findViewById(R.id.transmission_session_down_limit).setEnabled(isChecked);
                if (session.isDownloadSpeedLimitEnabled() != isChecked) {
                    session.setDownloadSpeedLimitEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.DOWNLOAD_SPEED_LIMIT_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_down_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    long value;
                    try {
                        value = Long.parseLong(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (session.getDownloadSpeedLimit() != value) {
                        session.setDownloadSpeedLimit(value);
                        setSession(TransmissionSession.SetterFields.DOWNLOAD_SPEED_LIMIT);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_up_limit_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                findViewById(R.id.transmission_session_up_limit).setEnabled(isChecked);
                if (session.isUploadSpeedLimitEnabled() != isChecked) {
                    session.setUploadSpeedLimitEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.UPLOAD_SPEED_LIMIT_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_up_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    long value;
                    try {
                        value = Long.parseLong(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (session.getUploadSpeedLimit() != value) {
                        session.setUploadSpeedLimit(value);
                        setSession(TransmissionSession.SetterFields.UPLOAD_SPEED_LIMIT);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_alt_limits_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                findViewById(R.id.transmission_session_alt_down_limit).setEnabled(isChecked);
                findViewById(R.id.transmission_session_alt_up_limit).setEnabled(isChecked);
                if (session.isAltSpeedLimitEnabled() != isChecked) {
                    session.setAltSpeedLimitEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_alt_down_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    long value;
                    try {
                        value = Long.parseLong(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (session.getAltDownloadSpeedLimit() != value) {
                        session.setAltDownloadSpeedLimit(value);
                        setSession(TransmissionSession.SetterFields.ALT_DOWNLOAD_SPEED_LIMIT);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_alt_up_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    long value;
                    try {
                        value = Long.parseLong(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (session.getAltUploadSpeedLimit() != value) {
                        session.setAltUploadSpeedLimit(value);
                        setSession(TransmissionSession.SetterFields.ALT_UPLOAD_SPEED_LIMIT);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_alt_limits_time_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                findViewById(R.id.transmission_session_alt_limit_time_from).setEnabled(isChecked);
                findViewById(R.id.transmission_session_alt_limit_time_to).setEnabled(isChecked);
                if (session.isAltSpeedLimitTimeEnabled() != isChecked) {
                    session.setAltSpeedLimitTimeEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.ALT_SPEED_LIMIT_TIME_ENABLED);
                }
            }
        });

        button = (Button) findViewById(R.id.transmission_session_alt_limit_time_from);
        button.setOnClickListener(new View.OnClickListener() {
            @Override  public void onClick(View v) {
                if (session == null) {
                    return;
                }
                showTimePickerDialog(true,
                    session.getAltSpeedTimeBegin() / 60,
                    session.getAltSpeedTimeBegin() % 60);
            }
        });

        button = (Button) findViewById(R.id.transmission_session_alt_limit_time_to);
        button.setOnClickListener(new View.OnClickListener() {
            @Override  public void onClick(View v) {
                if (session == null) {
                    return;
                }
                showTimePickerDialog(false,
                    session.getAltSpeedTimeEnd() / 60,
                    session.getAltSpeedTimeEnd() % 60);
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_seed_ratio_limit_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                findViewById(R.id.transmission_session_seed_ratio_limit).setEnabled(isChecked);
                if (session.isSeedRatioLimitEnabled() != isChecked) {
                    session.setSeedRatioLimitEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.SEED_RATIO_LIMIT_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_seed_ratio_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    float value;
                    try {
                        value = Float.parseFloat(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (session.getSeedRatioLimit() != value) {
                        session.setSeedRatioLimit(value);
                        setSession(TransmissionSession.SetterFields.SEED_RATIO_LIMIT);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_download_queue_size_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                findViewById(R.id.transmission_session_download_queue_size).setEnabled(isChecked);
                if (session.isDownloadQueueEnabled() != isChecked) {
                    session.setDownloadQueueEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.DOWNLOAD_QUEUE_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_download_queue_size);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int value;
                    try {
                        value = Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (session.getDownloadQueueSize() != value) {
                        session.setDownloadQueueSize(value);
                        setSession(TransmissionSession.SetterFields.DOWNLOAD_QUEUE_SIZE);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_seed_queue_size_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                findViewById(R.id.transmission_session_seed_queue_size).setEnabled(isChecked);
                if (session.isSeedQueueEnabled() != isChecked) {
                    session.setSeedQueueEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.SEED_QUEUE_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_seed_queue_size);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int value;
                    try {
                        value = Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (session.getSeedQueueSize() != value) {
                        session.setSeedQueueSize(value);
                        setSession(TransmissionSession.SetterFields.SEED_QUEUE_SIZE);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        check = (CheckBox) findViewById(R.id.transmission_session_stalled_queue_size_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (session == null) {
                    return;
                }
                findViewById(R.id.transmission_session_stalled_queue_size).setEnabled(isChecked);
                if (session.isStalledQueueEnabled() != isChecked) {
                    session.setStalledQueueEnabled(isChecked);
                    setSession(TransmissionSession.SetterFields.STALLED_QUEUE_ENABLED);
                }
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_stalled_queue_size);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int value;
                    try {
                        value = Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (session.getStalledQueueSize() != value) {
                        session.setStalledQueueSize(value);
                        setSession(TransmissionSession.SetterFields.STALLED_QUEUE_SIZE);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_global_peer_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int value;
                    try {
                        value = Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (session.getGlobalPeerLimit() != value) {
                        session.setGlobalPeerLimit(value);
                        setSession(TransmissionSession.SetterFields.GLOBAL_PEER_LIMIT);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        edit = (EditText) findViewById(R.id.transmission_session_torrent_peer_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (session == null) {
                    return false;
                }
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int value;
                    try {
                        value = Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (session.getTorrentPeerLimit() != value) {
                        session.setTorrentPeerLimit(value);
                        setSession(TransmissionSession.SetterFields.TORRENT_PEER_LIMIT);
                    }
                }
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

    }

    private void setSession(String... keys) {
        manager.setSession(session, keys);
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

    private class ServiceReceiver extends BroadcastReceiver {
        @Override public void onReceive(Context context, Intent intent) {
            int error = intent.getIntExtra(G.ARG_ERROR, 0);

            String type = intent.getStringExtra(G.ARG_REQUEST_TYPE);
            switch (type) {
                case DataService.Requests.GET_SESSION:
                case DataService.Requests.TEST_PORT:
                case DataService.Requests.UPDATE_BLOCKLIST:
                    if (error == 0 || error == TransmissionData.Errors.DUPLICATE_TORRENT
                        || error == TransmissionData.Errors.INVALID_TORRENT) {

                        switch (type) {
                            case DataService.Requests.GET_SESSION:
                                findViewById(R.id.fatal_error_layer).setVisibility(View.GONE);
                                new SessionTask(TransmissionSessionActivity.this).execute();
                                break;
                            case DataService.Requests.TEST_PORT:
                                boolean open = intent.getBooleanExtra(G.ARG_PORT_IS_OPEN, false);
                                Button test = (Button) TransmissionSessionActivity.this.findViewById(
                                    R.id.transmission_session_port_test);
                                test.setEnabled(true);
                                test.setText(Html.fromHtml(getString(open
                                    ? R.string.port_test_open : R.string.port_test_closed)));
                                break;
                            case DataService.Requests.UPDATE_BLOCKLIST:
                                long size = intent.getLongExtra(G.ARG_BLOCKLIST_SIZE, -1);
                                Button update = (Button) TransmissionSessionActivity.this.findViewById(
                                    R.id.transmission_session_blocklist_update);
                                update.setEnabled(true);
                                if (size == -1) {
                                    update.setText(Html.fromHtml(getString(R.string.blocklist_update_error)));
                                } else {
                                    update.setText(R.string.session_settings_blocklist_update);
                                    TextView text = (TextView) TransmissionSessionActivity.this.findViewById(
                                        R.id.transmission_session_blocklist_size);
                                    text.setText(String.format(
                                        getString(R.string.session_settings_blocklist_count_format),
                                        size
                                    ));

                                }
                                break;
                        }
                    } else {
                        findViewById(R.id.fatal_error_layer).setVisibility(View.VISIBLE);
                        TextView text = (TextView) findViewById(R.id.transmission_error);

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
                    break;
            }
        }
    }

    private class SessionTask extends AsyncTask<Void, Void, TransmissionSession> {
        DataSource readSource;
        public SessionTask(Context context) {
            super();

            readSource = new DataSource(context);
        }

        @Override protected TransmissionSession doInBackground(Void... params) {
            try {
                readSource.open();

                return readSource.getSession();
            } finally {
                if (readSource.isOpen()) {
                    readSource.close();
                }
            }
        }

        @Override protected void onPostExecute(TransmissionSession session) {
            updateFields(session, false);
            TransmissionSessionActivity.this.session = session;

            if (refreshing) {
                refreshing = false;
                invalidateOptionsMenu();
            }
        }
    }
}

