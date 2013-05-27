package org.sugr.gearshift;

import java.util.Arrays;
import java.util.List;

import org.sugr.gearshift.TransmissionSessionManager.ManagerException;
import org.sugr.gearshift.TransmissionSessionManager.TransmissionExclusionStrategy;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TransmissionSessionActivity extends FragmentActivity {
    public static final String ARG_PROFILE = "profile";
    public static final String ARG_JSON_SESSION = "json_session";

    private TransmissionProfile mProfile;
    private TransmissionSession mSession;

    private static final int SESSION_LOADER_ID = 10;

    private LoaderCallbacks<TransmissionData> mSessionLoaderCallbacks = new LoaderCallbacks<TransmissionData>() {

        @Override public Loader<TransmissionData> onCreateLoader(int arg0, Bundle arg1) {
            if (mProfile == null) return null;

            return new TransmissionSessionLoader(
                    TransmissionSessionActivity.this, mProfile);
        }

        @Override public void onLoadFinished(Loader<TransmissionData> loader,
                TransmissionData data) {
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


    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        Intent in = getIntent();
        Gson gson = new GsonBuilder().setExclusionStrategies(new TransmissionExclusionStrategy()).create();

        mProfile = in.getParcelableExtra(ARG_PROFILE);
        mSession = gson.fromJson(in.getStringExtra(ARG_JSON_SESSION), TransmissionSession.class);

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
        }

        mEncryptionValues = Arrays.asList(getResources().getStringArray(R.array.session_settings_encryption_values));

        initListeners();

        updateFields(null, true);

        getSupportLoaderManager().restartLoader(
                SESSION_LOADER_ID, null, mSessionLoaderCallbacks);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Loader<TransmissionData> loader = getSupportLoaderManager()
            .getLoader(SESSION_LOADER_ID);

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
            ((CheckBox) findViewById(R.id.transmission_session_rename_partial_check))
                .setChecked(mSession.isStartAddedTorrentsEnabled());
        }

        if (initial || mSession.getPeerPort() != session.getPeerPort()) {
            if (!initial)
                mSession.setPeerPort(session.getPeerPort());
            ((EditText) findViewById(R.id.transmission_session_peer_port))
                .setText(Long.toString(mSession.getPeerPort()));
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

        if (initial || mSession.isPEXEnabled() != session.isPEXEnabled()) {
            if (!initial)
                mSession.setPEXEnabled(session.isPEXEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_peer_exchange))
                .setChecked(mSession.isPEXEnabled());
        }

        if (initial || mSession.isDHTEnabled() != session.isDHTEnabled()) {
            if (!initial)
                mSession.setDHTEnabled(session.isDHTEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_hash_table))
                .setChecked(mSession.isDHTEnabled());
        }

        if (initial || mSession.isLPDEnabled() != session.isLPDEnabled()) {
            if (!initial)
                mSession.setLPDEnabled(session.isLPDEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_random_port))
                .setChecked(mSession.isLPDEnabled());
        }

        if (initial || mSession.isBlocklistEnabled() != session.isBlocklistEnabled()) {
            if (!initial)
                mSession.setBlocklistEnabled(session.isBlocklistEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_blocklist_check))
                .setChecked(mSession.isBlocklistEnabled());
            findViewById(R.id.transmission_session_blocklist_url).setEnabled(mSession.isBlocklistEnabled());
            findViewById(R.id.transmission_session_blocklist_update).setEnabled(mSession.isBlocklistEnabled());
        }

        if (initial || mSession.getBlocklistSize() != session.getBlocklistSize()) {
            if (!initial)
                mSession.setBlocklistSize(session.getBlocklistSize());
            ((TextView) findViewById(R.id.transmission_session_blocklist_size)).setText(String.format(
                getString(R.string.session_settings_blocklist_count_format),
                mSession.getBlocklistSize()
            ));
        }

        if (initial || mSession.getBlocklistURL() != session.getBlocklistURL()) {
            if (!initial)
                mSession.setBlocklistURL(session.getBlocklistURL());
            ((EditText) findViewById(R.id.transmission_session_blocklist_url))
                .setText(mSession.getBlocklistURL());
        }

        if (initial || mSession.isDownloadSpeedLimited() != session.isDownloadSpeedLimited()) {
            if (!initial)
                mSession.setSpeedLimitDownEnabled(session.isDownloadSpeedLimited());
            ((CheckBox) findViewById(R.id.transmission_session_down_limit_check))
                .setChecked(mSession.isDownloadSpeedLimited());
            findViewById(R.id.transmission_session_down_limit).setEnabled(mSession.isDownloadSpeedLimited());
        }

        if (initial || mSession.getDownloadSpeedLimit() != session.getDownloadSpeedLimit()) {
            if (!initial)
                mSession.setSpeedLimitDown(session.getDownloadSpeedLimit());
            ((EditText) findViewById(R.id.transmission_session_down_limit))
                .setText(Long.toString(mSession.getDownloadSpeedLimit()));
        }

        if (initial || mSession.isUploadSpeedLimited() != session.isUploadSpeedLimited()) {
            if (!initial)
                mSession.setSpeedLimitUpEnabled(session.isUploadSpeedLimited());
            ((CheckBox) findViewById(R.id.transmission_session_up_limit_check))
                .setChecked(mSession.isUploadSpeedLimited());
            findViewById(R.id.transmission_session_up_limit).setEnabled(mSession.isUploadSpeedLimited());
        }

        if (initial || mSession.getUploadSpeedLimit() != session.getUploadSpeedLimit()) {
            if (!initial)
                mSession.setSpeedLimitUp(session.getUploadSpeedLimit());
            ((EditText) findViewById(R.id.transmission_session_up_limit))
                .setText(Long.toString(mSession.getUploadSpeedLimit()));
        }

        if (initial || mSession.isAltSpeedEnabled() != session.isAltSpeedEnabled()) {
            if (!initial)
                mSession.setAltSpeedEnabled(session.isAltSpeedEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_alt_limits_check))
                .setChecked(mSession.isAltSpeedEnabled());
            findViewById(R.id.transmission_session_alt_down_limit).setEnabled(mSession.isAltSpeedEnabled());
            findViewById(R.id.transmission_session_alt_up_limit).setEnabled(mSession.isAltSpeedEnabled());
        }

        if (initial || mSession.getAltSpeedDown() != session.getAltSpeedDown()) {
            if (!initial)
                mSession.setAltSpeedDown(session.getAltSpeedDown());
            ((EditText) findViewById(R.id.transmission_session_alt_down_limit))
                .setText(Long.toString(mSession.getAltSpeedDown()));
        }

        if (initial || mSession.getAltSpeedUp() != session.getAltSpeedUp()) {
            if (!initial)
                mSession.setAltSpeedUp(session.getAltSpeedUp());
            ((EditText) findViewById(R.id.transmission_session_alt_up_limit))
                .setText(Long.toString(mSession.getAltSpeedUp()));
        }

        if (initial || mSession.isAltSpeedTimeEnabled() != session.isAltSpeedTimeEnabled()) {
            if (!initial)
                mSession.setAltSpeedTimeEnabled(session.isAltSpeedTimeEnabled());
            ((CheckBox) findViewById(R.id.transmission_session_alt_limits_time_check))
                .setChecked(mSession.isAltSpeedTimeEnabled());
            findViewById(R.id.transmission_session_alt_limit_time_from).setEnabled(mSession.isAltSpeedTimeEnabled());
            findViewById(R.id.transmission_session_alt_limit_time_to).setEnabled(mSession.isAltSpeedTimeEnabled());
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

        if (initial || mSession.isUploadSpeedLimited() != session.isUploadSpeedLimited()) {
            if (!initial)
                mSession.setSpeedLimitUpEnabled(session.isUploadSpeedLimited());
            ((CheckBox) findViewById(R.id.transmission_session_up_limit_check))
                .setChecked(mSession.isUploadSpeedLimited());
            findViewById(R.id.transmission_session_up_limit).setEnabled(mSession.isUploadSpeedLimited());
        }

        if (initial || mSession.getUploadSpeedLimit() != session.getUploadSpeedLimit()) {
            if (!initial)
                mSession.setSpeedLimitUp(session.getUploadSpeedLimit());
            ((EditText) findViewById(R.id.transmission_session_up_limit))
                .setText(Long.toString(mSession.getUploadSpeedLimit()));
        }

        if (initial || mSession.isSeedRatioLimited() != session.isSeedRatioLimited()) {
            if (!initial)
                mSession.setSeedRatioLimited(session.isSeedRatioLimited());
            ((CheckBox) findViewById(R.id.transmission_session_seed_ratio_limit_check))
                .setChecked(mSession.isSeedRatioLimited());
            findViewById(R.id.transmission_session_seed_ratio_limit).setEnabled(mSession.isSeedRatioLimited());
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
                    String dir = ((String) v.getText()).trim();
                    if (!mSession.getDownloadDir().equals(dir)) {
                        mSession.setDownloadDir(dir);
                        setSession(TransmissionSession.SetterFields.DOWNLOAD_DIR);
                    }
                }
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
    }

    private void setSession(String... keys) {
        Loader<TransmissionData> l = getSupportLoaderManager()
            .getLoader(SESSION_LOADER_ID);

        final TransmissionSessionLoader loader = (TransmissionSessionLoader) l;

        loader.setSession(mSession, keys);
    }
}

class TransmissionSessionLoader extends AsyncTaskLoader<TransmissionData> {

    private TransmissionSessionManager mSessManager;

    private TransmissionProfile mProfile;
    private TransmissionSession mSessionSet;
    private String[] mSessionSetKeys;

    private int mLastError;

    public TransmissionSessionLoader(Context context, TransmissionProfile profile) {
        super(context);

        mProfile = profile;
        mSessManager = new TransmissionSessionManager(getContext(), mProfile);
    }

    public void setSession(TransmissionSession session, String... keys) {
        mSessionSet = session;
        mSessionSetKeys = keys;
        onContentChanged();
    }


    @Override
    public TransmissionData loadInBackground() {
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
                mSessManager.setSession(mSessionSet, mSessionSetKeys);
            } catch (ManagerException e) {
                return handleError(e);
            } finally {
                mSessionSet = null;
                mSessionSetKeys = null;
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

    private TransmissionData handleError(ManagerException e) {
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
