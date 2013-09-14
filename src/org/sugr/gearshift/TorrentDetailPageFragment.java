package org.sugr.gearshift;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.database.DataSetObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.content.Loader;
import android.text.Html;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A fragment representing a single Torrent detail screen.
 * This fragment is either contained in a {@link TorrentListActivity}
 * in two-pane mode (on tablets) or a {@link TorrentDetailActivity}
 * on handsets.
 */
/* TODO: Use this class to hold the view pager. Place the options menu and loader here.
 * Create a new TorrentDetailPagerFragment class for the individual pages */
public class TorrentDetailPageFragment extends Fragment {
    private Torrent mTorrent;
    private List<String> mPriorityNames;
    private List<String> mPriorityValues;
    private List<String> mSeedRatioModeValues;

    private Object[] mTextValues = {
        "", "", "", "", ""
    };
    private static final int QUEUE_POSITION = 0;
    private static final int DOWNLOAD_LIMIT = 1;
    private static final int UPLOAD_LIMIT = 2;
    private static final int SEED_RATIO_LIMIT = 3;
    private static final int PEER_LIMIT = 4;

    private static final String STATE_EXPANDED = "expanded_states";
    private static final String STATE_SCROLL_POSITION = "scroll_position_state";

    private static class Expanders {
        public static final int TOTAL_EXPANDERS = 4;

        public static final int OVERVIEW = 0;
        public static final int FILES = 1;
        public static final int LIMITS = 2;
        public static final int TRACKERS = 3;
    }

    private boolean[] mExpandedStates = new boolean[Expanders.TOTAL_EXPANDERS];

    private View.OnClickListener mExpanderListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            View image;
            View content;
            int index;
            switch(v.getId()) {
                case R.id.torrent_detail_overview_expander:
                    image = v.findViewById(R.id.torrent_detail_overview_expander_image);
                    content = getView().findViewById(R.id.torrent_detail_overview_content);
                    index = Expanders.OVERVIEW;
                    break;
                case R.id.torrent_detail_files_expander:
                    image = v.findViewById(R.id.torrent_detail_files_expander_image);
                    content = getView().findViewById(R.id.torrent_detail_files_content);
                    index = Expanders.FILES;
                    break;
                case R.id.torrent_detail_limits_expander:
                    image = v.findViewById(R.id.torrent_detail_limits_expander_image);
                    content = getView().findViewById(R.id.torrent_detail_limits_content);
                    index = Expanders.LIMITS;
                    break;
                case R.id.torrent_detail_trackers_expander:
                    image = v.findViewById(R.id.torrent_detail_trackers_expander_image);
                    content = getView().findViewById(R.id.torrent_detail_trackers_content);
                    index = Expanders.TRACKERS;
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
                updateFields(getView());
            } else {
                content.setVisibility(View.GONE);
                image.setBackgroundResource(R.drawable.ic_section_expand);
                mExpandedStates[index] = false;
            }

        }
    };

    private FilesAdapter mFilesAdapter;
    private FilesDataSetObserver mFilesObserver;

    private TrackersAdapter mTrackersAdapter;
    private TrackersDataSetObserver mTrackersObserver;

    private ActionMode mFileActionMode;
    private Set<View> mSelectedFiles = new HashSet<View>();

    private ActionMode.Callback mActionModeFiles = new ActionMode.Callback() {

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mFileActionMode = null;
            for (View v : mSelectedFiles) {
                v.setActivated(false);
            }
            mSelectedFiles.clear();
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.torrent_detail_file_multiselect, menu);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            String key;
            Integer priority = null;
            switch (item.getItemId()) {
                case R.id.select_all:
                    List<View> files = mFilesAdapter.getViews();

                    for (View v : files) {
                        if (v != null) {
                            if (!v.isActivated()) {
                                v.setActivated(true);
                                mSelectedFiles.add(v);
                            }
                        }
                    }
                    invalidateFileActionMenu(mode.getMenu());

                    return true;
                case R.id.priority_low:
                    key = Torrent.SetterFields.FILES_LOW;
                    priority = Torrent.Priority.LOW;
                    break;
                case R.id.priority_normal:
                    key = Torrent.SetterFields.FILES_NORMAL;
                    priority = Torrent.Priority.NORMAL;
                    break;
                case R.id.priority_high:
                    key = Torrent.SetterFields.FILES_HIGH;
                    priority = Torrent.Priority.HIGH;
                    break;
                case R.id.check_selected:
                    key = Torrent.SetterFields.FILES_UNWANTED;
                    break;
                case R.id.uncheck_selected:
                    key = Torrent.SetterFields.FILES_WANTED;
                    break;
                default:
                    return false;
            }
            List<View> allViews = mFilesAdapter.getViews();
            List<Integer> indexes = new ArrayList<Integer>();
            for (View v : mSelectedFiles) {
                TorrentFile file = mFilesAdapter.getItem(allViews.indexOf(v));
                if (priority == null) {
                    if ((file.stat.isWanted()
                            && key.equals(
                                Torrent.SetterFields.FILES_UNWANTED))
                    || (!file.stat.isWanted()
                            && key.equals(
                                Torrent.SetterFields.FILES_WANTED))
                    ) {
                        indexes.add(file.index);
                    }
                } else {
                    if (file.stat.getPriority() != priority) {
                        indexes.add(file.index);
                        file.changed = true;
                        file.stat.setPriority(priority);
                    }
                }
            }
            if (indexes.size() > 0) {
                setTorrentProperty(key, indexes);
                mFilesAdapter.notifyDataSetChanged();
                if (priority == null) {
                    mode.finish();
                    ((TransmissionSessionInterface) getActivity()).setRefreshing(true);
                    Loader<TransmissionData> loader = getActivity().getSupportLoaderManager()
                        .getLoader(G.TORRENTS_LOADER_ID);
                    loader.onContentChanged();
                }
            }
            return true;
        }
    };

    private ActionMode mTrackerActionMode;
    private Set<View> mSelectedTrackers = new HashSet<View>();

    private ActionMode.Callback mActionModeTrackers = new ActionMode.Callback() {

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mTrackerActionMode = null;
            for (View v : mSelectedTrackers) {
                v.setActivated(false);
            }
            mSelectedTrackers.clear();
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.torrent_detail_tracker_multiselect, menu);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            String key;
            switch (item.getItemId()) {
                case R.id.select_all:
                    List<View> trackers = mTrackersAdapter.getViews();

                    for (View v : trackers) {
                        if (v != null) {
                            View info = v.findViewById(R.id.torrent_detail_trackers_row_info);
                            if (info != null && !info.isActivated()) {
                                info.setActivated(true);
                                mSelectedTrackers.add(info);
                            }
                        }
                    }

                    return true;
                case R.id.remove:
                    key = Torrent.SetterFields.TRACKER_REMOVE;
                    break;
                default:
                    return false;
            }
            List<Integer> ids = new ArrayList<Integer>();
            List<String> urls = new ArrayList<String>();
            for (View v : mSelectedTrackers) {
                Tracker tracker = mTrackersAdapter.getItem(mTrackersAdapter.getViews().indexOf(v.getParent()));
                ids.add(tracker.id);
                mTrackersAdapter.remove(tracker);
            }
            if (ids.size() > 0) {
                setTorrentProperty(key, ids);
                mode.finish();
                ((TransmissionSessionInterface) getActivity()).setRefreshing(true);
                Loader<TransmissionData> loader = getActivity().getSupportLoaderManager()
                        .getLoader(G.TORRENTS_LOADER_ID);
                loader.onContentChanged();
            }

            return true;
        }
    };

    private Runnable mLoseFocus = new Runnable() {
        @Override public void run() {
            getView().findViewById(R.id.torrent_detail_page_container).requestFocus();
        }
    };

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TorrentDetailPageFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mExpandedStates[Expanders.OVERVIEW] = true;

        if (getArguments().containsKey(G.ARG_PAGE_POSITION)) {
            int position = getArguments().getInt(G.ARG_PAGE_POSITION);
            ArrayList<Torrent> torrents = ((TransmissionSessionInterface) getActivity()).getTorrents();

            if (position < torrents.size())
                mTorrent = torrents.get(position);
        }

        mPriorityNames = Arrays.asList(getResources().getStringArray(R.array.torrent_priority));
        mPriorityValues = Arrays.asList(getResources().getStringArray(R.array.torrent_priority_values));
        mSeedRatioModeValues = Arrays.asList(getResources().getStringArray(R.array.torrent_seed_ratio_mode_values));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            final Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_torrent_detail_page, container, false);

        root.findViewById(R.id.torrent_detail_overview_expander).setOnClickListener(mExpanderListener);
        root.findViewById(R.id.torrent_detail_files_expander).setOnClickListener(mExpanderListener);
        root.findViewById(R.id.torrent_detail_limits_expander).setOnClickListener(mExpanderListener);
        root.findViewById(R.id.torrent_detail_trackers_expander).setOnClickListener(mExpanderListener);

        if (mTorrent == null) return root;

        if (savedInstanceState != null) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (savedInstanceState.containsKey(STATE_EXPANDED)) {
                        mExpandedStates = savedInstanceState.getBooleanArray(STATE_EXPANDED);
                        root.findViewById(R.id.torrent_detail_overview_content).setVisibility(mExpandedStates[Expanders.OVERVIEW] ? View.VISIBLE : View.GONE);
                        root.findViewById(R.id.torrent_detail_files_content).setVisibility(mExpandedStates[Expanders.FILES] ? View.VISIBLE : View.GONE);
                        root.findViewById(R.id.torrent_detail_limits_content).setVisibility(mExpandedStates[Expanders.LIMITS] ? View.VISIBLE : View.GONE);
                        root.findViewById(R.id.torrent_detail_trackers_content).setVisibility(mExpandedStates[Expanders.TRACKERS] ? View.VISIBLE : View.GONE);
                        updateFields(root);
                    }
                    if (savedInstanceState.containsKey(STATE_SCROLL_POSITION)) {
                        final int position = savedInstanceState.getInt(STATE_SCROLL_POSITION);
                        final ScrollView scroll = (ScrollView) root.findViewById(R.id.detail_scroll);
                        scroll.scrollTo(0, position);
                    }
                }
            });
        }

        mFilesAdapter = new FilesAdapter();
        mFilesObserver = new FilesDataSetObserver(root);
        mFilesAdapter.registerDataSetObserver(mFilesObserver);

        mTrackersAdapter = new TrackersAdapter();
        mTrackersObserver = new TrackersDataSetObserver(root);
        mTrackersAdapter.registerDataSetObserver(mTrackersObserver);

        updateFields(root);

        CheckBox check = (CheckBox) root.findViewById(R.id.torrent_global_limits);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mTorrent.areSessionLimitsHonored() != isChecked) {
                    setTorrentProperty(Torrent.SetterFields.SESSION_LIMITS, Boolean.valueOf(isChecked));
                }
            }
        });

        ((Spinner) root.findViewById(R.id.torrent_priority)).setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        String val = mPriorityValues.get(pos);
                        int priority = Torrent.Priority.NORMAL;

                        if (val.equals("low")) {
                            priority = Torrent.Priority.LOW;
                        } else if (val.equals("high")) {
                            priority = Torrent.Priority.HIGH;
                        }
                        if (mTorrent.getTorrentPriority() != priority) {
                            setTorrentProperty(Torrent.SetterFields.TORRENT_PRIORITY, Integer.valueOf(priority));
                        }
                    }

                    @Override public void onNothingSelected(AdapterView<?> parent) { }
                });

        final EditText queue = (EditText) root.findViewById(R.id.torrent_queue_position);
        queue.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int position;
                    try {
                        position = Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mTorrent.getQueuePosition() != position) {
                        setTorrentProperty(Torrent.SetterFields.QUEUE_POSITION, Integer.valueOf(position));
                    }
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        check = (CheckBox) root.findViewById(R.id.torrent_limit_download_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                root.findViewById(R.id.torrent_limit_download).setEnabled(isChecked);
                if (mTorrent.isDownloadLimited() != isChecked) {
                    setTorrentProperty(Torrent.SetterFields.DOWNLOAD_LIMITED, Boolean.valueOf(isChecked));
                }
            }
        });

        final EditText download = (EditText) root.findViewById(R.id.torrent_limit_download);
        download.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                long limit;
                try {
                    limit = Long.parseLong(v.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                if (mTorrent.getDownloadLimit() != limit) {
                    setTorrentProperty(Torrent.SetterFields.DOWNLOAD_LIMIT, Long.valueOf(limit));
                }
                new Handler().post(mLoseFocus);
                return false;
            }

        });

        check = (CheckBox) root.findViewById(R.id.torrent_limit_upload_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                root.findViewById(R.id.torrent_limit_upload).setEnabled(isChecked);
                if (mTorrent.isUploadLimited() != isChecked) {
                    setTorrentProperty(Torrent.SetterFields.UPLOAD_LIMITED, Boolean.valueOf(isChecked));
                }
            }
        });

        final EditText upload = (EditText) root.findViewById(R.id.torrent_limit_upload);
        upload.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                long limit;
                try {
                    limit = Long.parseLong(v.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                if (mTorrent.getUploadLimit() != limit) {
                    setTorrentProperty(Torrent.SetterFields.UPLOAD_LIMIT, Long.valueOf(limit));
                }
                new Handler().post(mLoseFocus);
                return false;
            }

        });

        ((Spinner) root.findViewById(R.id.torrent_seed_ratio_mode)).setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        String val = mSeedRatioModeValues.get(pos);
                        int mode = Torrent.SeedRatioMode.GLOBAL_LIMIT;
                        if (val.equals("user")) {
                            mode = Torrent.SeedRatioMode.TORRENT_LIMIT;
                        } else if (val.equals("infinite")) {
                            mode = Torrent.SeedRatioMode.NO_LIMIT;
                        }
                        root.findViewById(R.id.torrent_seed_ratio_limit).setEnabled(val.equals("user"));
                        if (mTorrent.getSeedRatioMode() != mode) {
                            setTorrentProperty(Torrent.SetterFields.SEED_RATIO_MODE, Integer.valueOf(mode));
                        }
                    }

                    @Override public void onNothingSelected(AdapterView<?> parent) { }
                });

        final EditText ratio = (EditText) root.findViewById(R.id.torrent_seed_ratio_limit);
        ratio.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                float limit;
                try {
                    limit = Float.parseFloat(v.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                if (mTorrent.getSeedRatioLimit() != limit) {
                    setTorrentProperty(Torrent.SetterFields.SEED_RATIO_LIMIT, Float.valueOf(limit));
                }
                new Handler().post(mLoseFocus);
                return false;
            }

        });

        final EditText peer = (EditText) root.findViewById(R.id.torrent_peer_limit);
        peer.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                int limit;
                try {
                    limit = Integer.parseInt(v.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                if (mTorrent.getPeerLimit() != limit) {
                    setTorrentProperty(Torrent.SetterFields.PEER_LIMIT, Integer.valueOf(limit));
                }
                new Handler().post(mLoseFocus);
                return false;
            }

        });

        Button addTracker = (Button) root.findViewById(R.id.torrent_detail_add_tracker);
        addTracker.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final List<String> urls = new ArrayList<String>();
                LayoutInflater inflater = getActivity().getLayoutInflater();

                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                        .setTitle(R.string.tracker_add)
                        .setCancelable(false)
                        .setNegativeButton(android.R.string.no, null)
                        .setPositiveButton(android.R.string.yes,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int id) {
                                        EditText url = (EditText) ((AlertDialog) dialog).findViewById(R.id.tracker_announce_url);

                                        urls.add(url.getText().toString());

                                        setTorrentProperty(Torrent.SetterFields.TRACKER_ADD, urls);

                                        ((TransmissionSessionInterface) getActivity()).setRefreshing(true);
                                        Loader<TransmissionData> loader = getActivity()
                                                .getSupportLoaderManager().getLoader(
                                                        G.TORRENTS_LOADER_ID);
                                        loader.onContentChanged();
                                    }
                                }).setView(inflater.inflate(R.layout.replace_tracker_dialog, null));
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (queue.hasFocus()) {
                    queue.clearFocus();
                } else if (download.hasFocus()) {
                    download.clearFocus();
                } else if (upload.hasFocus()) {
                    upload.clearFocus();
                } else if (ratio.hasFocus()) {
                    ratio.clearFocus();
                } else if (peer.hasFocus()) {
                    peer.clearFocus();
                }
                return false;
            }
        });

        return root;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBooleanArray(STATE_EXPANDED, mExpandedStates);
        ScrollView scroll = (ScrollView) getView().findViewById(R.id.detail_scroll);
        if (scroll != null) {
            outState.putInt(STATE_SCROLL_POSITION, scroll.getScrollY());
        }
    }

    public void notifyTorrentUpdate(Torrent torrent) {
        if (torrent.getId() != mTorrent.getId()) {
            return;
        }

        mTorrent = torrent;
        updateFields(getView());
    }

    public void onPageUnselected() {
        if (mFileActionMode != null) {
            mFileActionMode.finish();
        }
        if (mTrackerActionMode != null) {
            mTrackerActionMode.finish();
        }
    }

    private void setTorrentProperty(String key, Object value) {

        Loader<TransmissionData> loader = getActivity()
            .getSupportLoaderManager().getLoader(
                    G.TORRENTS_LOADER_ID);
        ((TransmissionDataLoader) loader).setTorrentProperty(mTorrent.getId(), key, value);
    }

    private void updateFields(View root) {
        if (root == null) return;

        ((TextView) root.findViewById(R.id.torrent_detail_title)).setText(mTorrent.getName());

        /* Overview start */
        if (root.findViewById(R.id.torrent_detail_overview_content).getVisibility() != View.GONE) {
            long now = new Timestamp(new Date().getTime()).getTime() / 1000;
            if (mTorrent.getMetadataPercentComplete() == 1) {
                ((TextView) root.findViewById(R.id.torrent_have)).setText(
                    String.format(
                        getString(R.string.torrent_have_format),
                        G.readableFileSize(mTorrent.getHaveValid() > 0
                                ? mTorrent.getHaveValid() : mTorrent.getSizeWhenDone() - mTorrent.getLeftUntilDone()),
                        G.readableFileSize(mTorrent.getSizeWhenDone()),
                        G.readablePercent(100 * (
                            mTorrent.getSizeWhenDone() > 0
                                ? (mTorrent.getSizeWhenDone() - mTorrent.getLeftUntilDone()) / mTorrent.getSizeWhenDone()
                                : 1
                        ))
                    ));
                ((TextView) root.findViewById(R.id.torrent_downloaded)).setText(
                    mTorrent.getDownloadedEver() == 0
                        ? getString(R.string.unknown)
                        : G.readableFileSize(mTorrent.getDownloadedEver())
                );
                ((TextView) root.findViewById(R.id.torrent_uploaded)).setText(
                    G.readableFileSize(mTorrent.getUploadedEver())
                );
                int state = R.string.none;
                switch(mTorrent.getStatus()) {
                    case Torrent.Status.STOPPED:
                        state = mTorrent.isPaused()
                            ? R.string.status_paused
                            : R.string.status_finished;
                        break;
                    case Torrent.Status.CHECK_WAITING:
                        state = R.string.status_check_waiting;
                        break;
                    case Torrent.Status.CHECKING:
                        state = R.string.status_checking;
                        break;
                    case Torrent.Status.DOWNLOAD_WAITING:
                        state = R.string.status_download_waiting;
                        break;
                    case Torrent.Status.DOWNLOADING:
                        state = mTorrent.getMetadataPercentComplete() < 0
                            ? R.string.status_downloading_metadata
                            : R.string.status_downloading;
                        break;
                    case Torrent.Status.SEED_WAITING:
                        state = R.string.status_seed_waiting;
                        break;
                    case Torrent.Status.SEEDING:
                        state = R.string.status_seeding;
                        break;
                }
                ((TextView) root.findViewById(R.id.torrent_state)).setText(state);
                ((TextView) root.findViewById(R.id.torrent_running_time)).setText(
                    mTorrent.getStatus() == Torrent.Status.STOPPED
                        ? getString(R.string.status_finished)
                        : mTorrent.getStartDate() > 0
                            ? G.readableRemainingTime(now - mTorrent.getStartDate(), getActivity())
                            : getString(R.string.unknown)
                );
                ((TextView) root.findViewById(R.id.torrent_remaining_time)).setText(
                    mTorrent.getEta() < 0
                        ? getString(R.string.unknown)
                        : G.readableRemainingTime(mTorrent.getEta(), getActivity())
                );
                long lastActive = now - mTorrent.getActivityDate();
                ((TextView) root.findViewById(R.id.torrent_last_activity)).setText(
                    lastActive < 0 || mTorrent.getActivityDate() <= 0
                        ? getString(R.string.unknown)
                        : lastActive < 5
                            ? getString(R.string.torrent_active_now)
                            : G.readableRemainingTime(lastActive, getActivity())
                );
                TextView errorText =((TextView) root.findViewById(R.id.torrent_error));
                if (mTorrent.getError() == Torrent.Error.OK) {
                    errorText.setText(R.string.no_tracker_errors);
                    errorText.setEnabled(false);
                } else {
                    errorText.setText(mTorrent.getErrorString());
                    errorText.setEnabled(true);
                }
                ((TextView) root.findViewById(R.id.torrent_size)).setText(
                        String.format(
                            getString(R.string.torrent_size_format),
                            G.readableFileSize(mTorrent.getTotalSize()),
                            mTorrent.getPieceCount(),
                            G.readableFileSize(mTorrent.getPieceSize())
                        ));
                ((TextView) root.findViewById(R.id.torrent_location)).setText(mTorrent.getDownloadDir());
                ((TextView) root.findViewById(R.id.torrent_hash)).setText(mTorrent.getHashString());
                ((TextView) root.findViewById(R.id.torrent_privacy)).setText(
                        mTorrent.isPrivate() ? R.string.torrent_private : R.string.torrent_public);

                Date creationDate = new Date(mTorrent.getDateCreated() * 1000);
                ((TextView) root.findViewById(R.id.torrent_origin)).setText(
                        mTorrent.getCreator() == null || mTorrent.getCreator().isEmpty()
                        ? String.format(
                                    getString(R.string.torrent_origin_format),
                                    creationDate.toString()
                            )
                        : String.format(
                                    getString(R.string.torrent_origin_creator_format),
                                    mTorrent.getCreator(),
                                    creationDate.toString()
                            ));
                ((TextView) root.findViewById(R.id.torrent_comment)).setText(mTorrent.getComment());
            } else {
                ((TextView) root.findViewById(R.id.torrent_have)).setText(R.string.none);
                int state;
                switch(mTorrent.getStatus()) {
                    case Torrent.Status.STOPPED:
                        state = mTorrent.isPaused()
                            ? R.string.status_paused
                            : R.string.status_finished;
                        break;
                    case Torrent.Status.DOWNLOAD_WAITING:
                        state = R.string.status_download_waiting;
                        break;
                    default:
                        state = R.string.status_downloading_metadata;
                        break;
                }
                ((TextView) root.findViewById(R.id.torrent_state)).setText(state);
            }
        }

        /* Files start */
        if (root.findViewById(R.id.torrent_detail_files_content).getVisibility() != View.GONE
                && mTorrent.getFiles() != null && mTorrent.getFileStats() != null) {

            mFilesAdapter.setNotifyOnChange(false);
            if (mFilesAdapter.getCount() == 0) {
                Torrent.File[] files = mTorrent.getFiles();
                Torrent.FileStats[] stats = mTorrent.getFileStats();

                mFilesAdapter.clear();
                ArrayList<TorrentFile> torrentFiles = new ArrayList<TorrentFile>();
                for (int i = 0; i < files.length; i++) {
                    TorrentFile file = new TorrentFile(i, files[i], stats[i]);
                    torrentFiles.add(file);
                }
                Collections.sort(torrentFiles, new TorrentFileComparator());
                String directory = "";

                ArrayList<Integer> directories = new ArrayList<Integer>();
                for (int i = 0; i < torrentFiles.size(); i++) {
                    TorrentFile file = torrentFiles.get(i);
                    if (!directory.equals(file.directory)) {
                        directory = file.directory;
                        directories.add(i);
                    }
                }
                int offset = 0;
                for (Integer i : directories) {
                    TorrentFile file = torrentFiles.get(i + offset);
                    torrentFiles.add(i + offset, new TorrentFile(file.directory));
                    offset++;
                }

                mFilesAdapter.addAll(torrentFiles);
            }
            mFilesAdapter.notifyDataSetChanged();
        }

        /* Limits start */
        if (root.findViewById(R.id.torrent_detail_limits_content).getVisibility() != View.GONE) {
            CheckBox check = (CheckBox) root.findViewById(R.id.torrent_global_limits);
            check.setChecked(mTorrent.areSessionLimitsHonored());

            String priority = "normal";
            switch (mTorrent.getTorrentPriority()) {
                case Torrent.Priority.LOW:
                    priority = "low";
                    break;
                case Torrent.Priority.HIGH:
                    priority = "high";
                    break;
            }
            ((Spinner) root.findViewById(R.id.torrent_priority)).setSelection(mPriorityValues.indexOf(priority));

            String queue = Integer.toString(mTorrent.getQueuePosition());
            if (!mTextValues[QUEUE_POSITION].equals(queue)) {
                ((EditText) root.findViewById(R.id.torrent_queue_position))
                    .setText(queue);
                mTextValues[QUEUE_POSITION] = queue;
            }

            check = (CheckBox) root.findViewById(R.id.torrent_limit_download_check);
            check.setChecked(mTorrent.isDownloadLimited());

            EditText limit;
            String download = Long.toString(mTorrent.getDownloadLimit());
            if (!mTextValues[DOWNLOAD_LIMIT].equals(download)) {
                limit = (EditText) root.findViewById(R.id.torrent_limit_download);
                limit.setText(download);
                limit.setEnabled(check.isChecked());
                mTextValues[DOWNLOAD_LIMIT] = download;
            }

            check = (CheckBox) root.findViewById(R.id.torrent_limit_upload_check);
            check.setChecked(mTorrent.isUploadLimited());

            String upload = Long.toString(mTorrent.getUploadLimit());
            if (!mTextValues[UPLOAD_LIMIT].equals(upload)) {
                limit = (EditText) root.findViewById(R.id.torrent_limit_upload);
                limit.setText(upload);
                limit.setEnabled(check.isChecked());
                mTextValues[UPLOAD_LIMIT] = upload;
            }

            String mode = "global";
            switch (mTorrent.getSeedRatioMode()) {
                case Torrent.SeedRatioMode.TORRENT_LIMIT:
                    mode = "user";
                    break;
                case Torrent.SeedRatioMode.NO_LIMIT:
                    mode = "infinite";
                    break;
            }
            ((Spinner) root.findViewById(R.id.torrent_seed_ratio_mode)).setSelection(
                mSeedRatioModeValues.indexOf(mode));

            String ratio = G.readablePercent(mTorrent.getSeedRatioLimit());
            if (!mTextValues[SEED_RATIO_LIMIT].equals(ratio)) {
                limit = (EditText) root.findViewById(R.id.torrent_seed_ratio_limit);
                limit.setText(ratio);
                mTextValues[SEED_RATIO_LIMIT] = ratio;
            }

            String peers = Integer.toString(mTorrent.getPeerLimit());
            if (!mTextValues[PEER_LIMIT].equals(peers)) {
                limit = (EditText) root.findViewById(R.id.torrent_peer_limit);
                limit.setText(peers);
                mTextValues[PEER_LIMIT] = peers;
            }
        }

        /* Trackers start */
        if (root.findViewById(R.id.torrent_detail_trackers_content).getVisibility() != View.GONE
                && mTorrent.getTrackers() != null && mTorrent.getTrackerStats() != null) {
            mTrackersAdapter.setNotifyOnChange(false);
            if (mTrackersAdapter.getCount() != mTorrent.getTrackers().length) {
                Torrent.Tracker[] tTrackers = mTorrent.getTrackers();
                Torrent.TrackerStats[] stats = mTorrent.getTrackerStats();

                mTrackersAdapter.clear();
                ArrayList<Tracker> trackers = new ArrayList<Tracker>();
                for (int i = 0; i < tTrackers.length && i < stats.length; i++) {
                    Tracker tracker = new Tracker(i, tTrackers[i], stats[i]);
                    trackers.add(tracker);
                }
                Collections.sort(trackers, new TrackerComparator());
                mTrackersAdapter.addAll(trackers);
            }
            mTrackersAdapter.notifyDataSetChanged();
        }
    }

    private void invalidateFileActionMenu(Menu menu) {
        boolean hasChecked = false, hasUnchecked = false;
        MenuItem checked = menu.findItem(R.id.check_selected);
        MenuItem unchecked = menu.findItem(R.id.uncheck_selected);
        List<View> allViews = mFilesAdapter.getViews();

        for (View v : mSelectedFiles) {
            TorrentFile file = mFilesAdapter.getItem(allViews.indexOf(v));
            if (file.stat.isWanted()) {
                hasChecked = true;
                checked.setVisible(true);
            } else {
                hasUnchecked = true;
                unchecked.setVisible(true);
            }

            if (hasChecked && hasUnchecked) {
                break;
            }
        }
        if (!hasChecked) {
            checked.setVisible(false);
        }
        if (!hasUnchecked) {
            unchecked.setVisible(false);
        }
    }

    private class TorrentFile {
        int index = -1;
        Torrent.File info;
        Torrent.FileStats stat;

        String directory;
        String name;

        boolean changed = false;

        public TorrentFile(int index, Torrent.File info, Torrent.FileStats stat) {
            this.index = index;
            this.stat = stat;

            setInfo(info);
        }

        public TorrentFile(String directory) {
            this.directory = directory;
        }

        public void setInfo(Torrent.File info) {
            this.info = info;
            String path = info.getName();
            File f = new File(path);
            this.directory = f.getParent();
            this.name = f.getName();
        }

        public void setStat(Torrent.FileStats stat) {
            this.stat = stat;
        }

        public void setIndex(int index) {
            this.index = index;
        }
    }

    private class TorrentFileComparator implements Comparator<TorrentFile> {
        @Override
        public int compare(TorrentFile lhs, TorrentFile rhs) {
            int path = lhs.directory.compareTo(rhs.directory);

            if (path != 0) {
                return path;
            }
            return lhs.name.compareToIgnoreCase(rhs.name);
        }

    }

    private class FilesAdapter extends ArrayAdapter<TorrentFile> {
        private static final int mFieldId = R.id.torrent_detail_files_row;
        private List<View> mViews = new ArrayList<View>();

        public FilesAdapter() {
            super(getActivity(), mFieldId);
        }

        public List<View> getViews() {
            return mViews;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            final TorrentFile file = getItem(position);
            boolean initial = false;

            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                if (file.info == null) {
                    rowView = vi.inflate(R.layout.torrent_detail_files_directory_row, null);
                } else {
                    rowView = vi.inflate(R.layout.torrent_detail_files_row, null);
                }
                initial = true;
            }

            if (file.info == null) {
                TextView row = (TextView) rowView.findViewById(mFieldId);
                if (file.directory == null) {
                    row.setVisibility(View.GONE);
                } else {
                    row.setText(file.directory);
                }
            } else {
                final View container = rowView;
                CheckBox row = (CheckBox) rowView.findViewById(mFieldId);
                if (initial) {
                    row.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (mFileActionMode != null) {
                                buttonView.setChecked(!isChecked);
                                if (container.isActivated()) {
                                    container.setActivated(false);
                                    mSelectedFiles.remove(container);
                                    if (mSelectedFiles.size() == 0) {
                                        mFileActionMode.finish();
                                    } else {
                                        invalidateFileActionMenu(mFileActionMode.getMenu());
                                    }
                                } else {
                                    container.setActivated(true);
                                    mSelectedFiles.add(container);
                                    invalidateFileActionMenu(mFileActionMode.getMenu());
                                }
                                return;
                            }
                            if (file.stat.isWanted() != isChecked) {
                                if (isChecked) {
                                    setTorrentProperty(Torrent.SetterFields.FILES_WANTED, Integer.valueOf(file.index));
                                } else {
                                    setTorrentProperty(Torrent.SetterFields.FILES_UNWANTED, Integer.valueOf(file.index));
                                }
                            }
                        }
                    });
                    row.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            if (mFileActionMode != null) {
                                return false;
                            }
                            container.setActivated(true);
                            mSelectedFiles.add(container);
                            mFileActionMode = getActivity().startActionMode(mActionModeFiles);
                            invalidateFileActionMenu(mFileActionMode.getMenu());
                            return true;
                        }
                    });
                }
                String priority;
                switch(file.stat.getPriority()) {
                    case Torrent.Priority.LOW:
                        priority = mPriorityNames.get(0);
                        break;
                    case Torrent.Priority.HIGH:
                        priority = mPriorityNames.get(2);
                        break;
                    default:
                        priority = mPriorityNames.get(1);
                        break;
                }

                row.setText(Html.fromHtml(String.format(
                    getString(R.string.torrent_detail_file_format),
                    file.name,
                    G.readableFileSize(file.stat.getBytesCompleted()),
                    G.readableFileSize(file.info.getLength()),
                    priority
                )));

                row.setChecked(file.stat.isWanted());

                if (initial) {
                    while (mViews.size() <= position)
                        mViews.add(null);
                    mViews.set(position, rowView);
                }
            }

            return rowView;
        }
    }

    private class FilesDataSetObserver extends DataSetObserver {
        private View mRoot;
        private LinearLayout mContainer;

        public FilesDataSetObserver(View root) {
            mRoot = root;
            mContainer = (LinearLayout) mRoot.findViewById(R.id.torrent_detail_files_content);
        }

        @Override public void onChanged() {
            Torrent.File[] files = mTorrent.getFiles();
            Torrent.FileStats[] stats = mTorrent.getFileStats();

            for (int i = 0; i < mFilesAdapter.getCount(); i++) {
                TorrentFile file = mFilesAdapter.getItem(i);
                View v = null;
                boolean hasChild = false;
                if (i < mContainer.getChildCount()) {
                    v = mContainer.getChildAt(i);
                    hasChild = true;
                }
                if (!hasChild || (file.index != -1 && fileChanged(file, files[file.index], stats[file.index]))) {
                    v = mFilesAdapter.getView(i, v, null);
                    if (!hasChild) {
                        mContainer.addView(v, i);
                    }
                }
            }
        }

        @Override public void onInvalidated() {
            mContainer.removeAllViews();
        }

        private boolean fileChanged(TorrentFile file, Torrent.File tFile, Torrent.FileStats stat) {
            boolean changed = false;

            if (file.changed
                    || file.stat.isWanted() != stat.isWanted()
                    || file.stat.getBytesCompleted() != stat.getBytesCompleted()
                    || file.stat.getPriority() != stat.getPriority()) {
                file.setStat(stat);
                file.changed = false;
                changed = true;
            }

            return changed;
        }
    }

    private class Tracker {
        public int index = -1;
        public String host;
        public int id;
        public String announce;
        public String scrape;
        public int tier;

        public int seederCount;
        public int leecherCount;
        public boolean hasAnnounced;
        public long lastAnnounceTime;
        public boolean hasLastAnnounceSucceeded;
        public int lastAnnouncePeerCount;
        public String lastAnnounceResult;

        public boolean hasScraped;
        public long lastScrapeTime;
        public boolean hasLastScrapeSucceeded;
        public String lastScrapeResult;

        public Tracker(int index, Torrent.Tracker info, Torrent.TrackerStats stat) {
            this.index = index;

            setInfo(info);
            setStat(stat);
        }

        public void setInfo(Torrent.Tracker info) {
            try {
                URI uri = new URI(info.getAnnounce());
                this.host = uri.getHost();
            } catch (URISyntaxException e) {
                this.host = getString(R.string.tracker_unknown_host);
            }

            this.id = info.getId();
            this.announce = new String(info.getAnnounce());
            this.scrape = new String(info.getScrape());
            this.tier = info.getTier();
        }

        public void setStat(Torrent.TrackerStats stat) {
            this.seederCount = stat.getSeederCount();
            this.leecherCount = stat.getLeecherCount();
            this.hasAnnounced = stat.hasAnnounced();
            this.lastAnnounceTime = stat.getLastAnnouceTime();
            this.hasLastAnnounceSucceeded = stat.hasLastAnnouceSucceeded();
            this.lastAnnouncePeerCount = stat.getLastAnnoucePeerCount();
            this.lastAnnounceResult = new String(stat.getLastAnnouceResult());
            this.hasScraped = stat.hasScraped();
            this.lastScrapeTime = stat.getLastScrapeTime();
            this.hasLastScrapeSucceeded = stat.hasLastScrapeSucceeded();
            this.lastScrapeResult = new String(stat.getLastScrapeResult());
        }

        public void setIndex(int index) {
            this.index = index;
        }
    }

    private class TrackerComparator implements Comparator<Tracker> {
        @Override
        public int compare(Tracker lhs, Tracker rhs) {
            int tier = lhs.tier - rhs.tier;

            if (tier != 0) {
                return tier;
            }

            return lhs.host.compareToIgnoreCase(rhs.host);
        }

    }

    private class TrackersAdapter extends ArrayAdapter<Tracker> {
        private static final int mFieldId = R.id.torrent_detail_trackers_row;
        private List<View> mViews = new ArrayList<View>();

        public TrackersAdapter() {
            super(getActivity(), mFieldId);
        }

        public List<View> getViews() {
            return mViews;
        }

        @Override
        public void remove(Tracker tracker) {
            int index = tracker.index;

            super.remove(tracker);

            for (int i = 0; i < getCount(); ++i) {
                Tracker t = getItem(i);
                if (t.index > index) {
                    t.setIndex(t.index - 1);
                }
            }
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            final Tracker tracker = getItem(position);
            boolean initial = false;

            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_detail_trackers_row, null);
                initial = true;
            }

            TextView url = (TextView) rowView.findViewById(R.id.tracker_url);
            TextView tier = (TextView) rowView.findViewById(R.id.tracker_tier);
            TextView seeders = (TextView) rowView.findViewById(R.id.tracker_seeders);
            TextView leechers = (TextView) rowView.findViewById(R.id.tracker_leechers);
            TextView announce = (TextView) rowView.findViewById(R.id.tracker_announce);
            TextView scrape = (TextView) rowView.findViewById(R.id.tracker_scrape);

            url.setText(tracker.host);
            tier.setText(String.format(getString(R.string.tracker_tier),
                    tracker.tier));

            seeders.setText(String.format(getString(R.string.tracker_seeders),
                    tracker.seederCount));
            leechers.setText(String.format(getString(R.string.tracker_leechers),
                    tracker.leecherCount));

            long now = (new Date().getTime() / 1000);
            if (tracker.hasAnnounced) {
                String time = G.readableRemainingTime(now - tracker.lastAnnounceTime,
                        getActivity());
                if (tracker.hasLastAnnounceSucceeded) {
                    announce.setText(String.format(
                            getString(R.string.tracker_announce_success),
                            time, String.format(
                                getResources().getQuantityString(R.plurals.tracker_peers,
                                        tracker.lastAnnouncePeerCount,
                                        tracker.lastAnnouncePeerCount)
                    )));
                } else {
                    announce.setText(String.format(
                            getString(R.string.tracker_announce_error),
                            TextUtils.isEmpty(tracker.lastAnnounceResult)
                                ? ""
                                : (tracker.lastAnnounceResult + " - "),
                            time
                    ));
                }
            } else {
                announce.setText(R.string.tracker_announce_never);
            }

            if (tracker.hasScraped) {
                String time = G.readableRemainingTime(now - tracker.lastScrapeTime,
                        getActivity());
                if (tracker.hasLastScrapeSucceeded) {
                    scrape.setText(String.format(
                            getString(R.string.tracker_scrape_success),
                            time
                    ));
                } else {
                    scrape.setText(String.format(
                            getString(R.string.tracker_scrape_error),
                            TextUtils.isEmpty(tracker.lastScrapeResult)
                                    ? ""
                                    : (tracker.lastScrapeResult + " - "),
                            time
                    ));
                }
            } else {
                scrape.setText(R.string.tracker_scrape_never);
            }

            if (initial) {
                while (mViews.size() <= position)
                    mViews.add(null);
                mViews.set(position, rowView);

                View row = rowView.findViewById(R.id.torrent_detail_trackers_row_info);
                final View buttons = rowView.findViewById(R.id.torrent_detail_tracker_buttons);

                row.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (mTrackerActionMode != null) {
                            return false;
                        }
                        v.setActivated(true);
                        mSelectedTrackers.add(v);
                        mTrackerActionMode = getActivity().startActionMode(mActionModeTrackers);
                        hideAllButtons(null);

                        return true;
                    }
                });
                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (mTrackerActionMode != null) {
                            if (v.isActivated()) {
                                mSelectedTrackers.remove(v);
                                v.setActivated(false);
                                if (mSelectedTrackers.size() == 0) {
                                    mTrackerActionMode.finish();
                                }
                            } else {
                                mSelectedTrackers.add(v);
                                v.setActivated(true);
                            }

                            return;
                        }

                        hideAllButtons(buttons);

                        if (buttons.getVisibility() == View.GONE) {
                            buttons.setVisibility(View.VISIBLE);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)  {
                                buttons.setAlpha(0.3f);
                                buttons.setTranslationY(-50);
                                buttons.animate().setDuration(200).alpha(1).translationY(0).start();
                            }
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)  {
                                animateHideButtons(buttons);
                            } else {
                                buttons.setVisibility(View.GONE);
                            }
                        }
                    }
                });

                final TransmissionSessionInterface context = (TransmissionSessionInterface) getActivity();
                final Loader<TransmissionData> loader = getActivity().getSupportLoaderManager()
                        .getLoader(G.TORRENTS_LOADER_ID);

                buttons.findViewById(R.id.torrent_detail_tracker_remove).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        mTrackersAdapter.remove(tracker);
                        List<Integer> ids = new ArrayList<Integer>();
                        ids.add(tracker.id);

                        setTorrentProperty(Torrent.SetterFields.TRACKER_REMOVE, ids);
                        context.setRefreshing(true);
                        loader.onContentChanged();

                        hideAllButtons(null);
                    }
                });

                buttons.findViewById(R.id.torrent_detail_tracker_replace).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        final List<Integer> ids = new ArrayList<Integer>();
                        ids.add(tracker.id);

                        final List<String> urls = new ArrayList<String>();
                        LayoutInflater inflater = getActivity().getLayoutInflater();

                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.tracker_replace)
                            .setCancelable(false)
                            .setNegativeButton(android.R.string.no, null)
                            .setPositiveButton(android.R.string.yes,
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int id) {
                                            EditText url = (EditText) ((AlertDialog) dialog).findViewById(R.id.tracker_announce_url);

                                            urls.add(url.getText().toString());

                                            setTorrentProperty(Torrent.SetterFields.TRACKER_REPLACE, new Torrent.TrackerReplaceTuple(ids, urls));

                                            context.setRefreshing(true);
                                            loader.onContentChanged();
                                        }
                                    }).setView(inflater.inflate(R.layout.replace_tracker_dialog, null));
                        AlertDialog dialog = builder.create();
                        dialog.show();

                        hideAllButtons(null);
                    }
                });

                buttons.findViewById(R.id.torrent_detail_tracker_copy).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String url = tracker.announce;
                        ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText(getString(R.string.tracker_announce_url), url);
                        clipboard.setPrimaryClip(clip);

                        Toast.makeText(getActivity(),
                                R.string.tracker_url_copy, Toast.LENGTH_SHORT).show();

                        hideAllButtons(null);
                    }
                });
            }

            return rowView;
        }

        private void hideAllButtons(View ignore) {
            for (View v : mViews) {
                if (v != null) {
                    View buttons = v.findViewById(R.id.torrent_detail_tracker_buttons);
                    if (buttons != null && (ignore == null || ignore != buttons) && buttons.getVisibility() != View.GONE) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)  {
                            animateHideButtons(buttons);
                        } else {
                            buttons.setVisibility(View.GONE);
                        }
                    }
                }
            }
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        private void animateHideButtons(final View v) {
            v.animate().setDuration(250).alpha(0).translationY(-50).withEndAction(new Runnable() {
                @Override public void run() {
                    v.setVisibility(View.GONE);
                }
            });
        }
    }

    private class TrackersDataSetObserver extends DataSetObserver {
        private View mRoot;
        private LinearLayout mContainer;

        public TrackersDataSetObserver(View root) {
            mRoot = root;
            mContainer = (LinearLayout) mRoot.findViewById(R.id.torrent_detail_trackers_list);
        }

        @Override public void onChanged() {
            Torrent.Tracker[] trackers = mTorrent.getTrackers();
            Torrent.TrackerStats[] stats = mTorrent.getTrackerStats();

            for (int i = 0; i < mTrackersAdapter.getCount(); i++) {
                Tracker tracker = mTrackersAdapter.getItem(i);
                View v = null;
                boolean hasChild = false;
                if (i < mContainer.getChildCount()) {
                    v = mContainer.getChildAt(i);
                    hasChild = true;
                }
                if (!hasChild || (tracker.index != -1 && trackerChanged(tracker, trackers[tracker.index], stats[tracker.index]))) {
                    v = mTrackersAdapter.getView(i, v, null);
                    if (!hasChild) {
                        mContainer.addView(v, i);
                    }
                }
            }

            int starting = mTrackersAdapter.getCount();
            List<View> views = mTrackersAdapter.getViews();
            while (views.size() > starting) {
                View v = views.get(starting);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)  {
                    animateRemoveView(v);
                } else {
                    mContainer.removeView(v);
                }

                views.remove(starting);
            }
        }

        @Override public void onInvalidated() {
            mContainer.removeAllViews();
        }

        private boolean trackerChanged(Tracker tracker, Torrent.Tracker tTracker, Torrent.TrackerStats stat) {
            boolean changed = false;

            if (!tracker.announce.equals(tTracker.getAnnounce())
                    || tracker.lastAnnounceTime != stat.getLastAnnouceTime()
                    || tracker.lastScrapeTime != stat.getLastScrapeTime()
                    || tracker.leecherCount != stat.getLeecherCount()
                    || tracker.seederCount != stat.getSeederCount()) {
                tracker.setInfo(tTracker);
                tracker.setStat(stat);
                changed = true;
            }

            return changed;
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        private void animateRemoveView(final View v) {
            v.animate().setDuration(250).alpha(0).translationY(-50).withEndAction(new Runnable() {
                @Override public void run() {
                    mContainer.removeView(v);
                }
            });
        }
    }
}
