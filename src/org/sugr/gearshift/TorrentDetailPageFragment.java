package org.sugr.gearshift;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.AsyncTaskLoader;
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

import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.datasource.TorrentDetails;

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
    private int torrentId;
    private TorrentDetails details;

    private static List<String> priorityNames;
    private static List<String> priorityValues;
    private static List<String> seedRatioModeValues;

    private static final String STATE_EXPANDED = "expanded_states";
    private static final String STATE_SCROLL_POSITION = "scroll_position_state";

    private static class Expanders {
        public static final int TOTAL_EXPANDERS = 4;

        public static final int OVERVIEW = 0;
        public static final int FILES = 1;
        public static final int LIMITS = 2;
        public static final int TRACKERS = 3;
    }

    private static final class Views {
        public static LinearLayout overviewContent;
        public static LinearLayout filesContent;
        public static LinearLayout limitsContent;
        public static LinearLayout trackersContent;

        public static LinearLayout trackersContainer;

        public static TextView name;
        public static TextView have;
        public static TextView downloaded;
        public static TextView uploaded;
        public static TextView runningTime;
        public static TextView remainingTime;
        public static TextView lastActivity;
        public static TextView size;
        public static TextView hash;
        public static TextView privacy;
        public static TextView origin;
        public static TextView comment;
        public static TextView state;
        public static TextView added;
        public static TextView queue;
        public static TextView error;
        public static TextView location;

        public static CheckBox globalLimits;
        public static Spinner torrentPriority;
        public static EditText queuePosition;
        public static CheckBox downloadLimited;
        public static EditText downloadLimit;
        public static CheckBox uploadLimited;
        public static EditText uploadLimit;
        public static Spinner seedRatioMode;
        public static EditText seedRatioLimit;
        public static EditText peerLimit;
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
                    content = Views.overviewContent;
                    index = Expanders.OVERVIEW;
                    break;
                case R.id.torrent_detail_files_expander:
                    image = v.findViewById(R.id.torrent_detail_files_expander_image);
                    content = Views.filesContent;
                    index = Expanders.FILES;
                    break;
                case R.id.torrent_detail_limits_expander:
                    image = v.findViewById(R.id.torrent_detail_limits_expander_image);
                    content = Views.limitsContent;
                    index = Expanders.LIMITS;
                    break;
                case R.id.torrent_detail_trackers_expander:
                    image = v.findViewById(R.id.torrent_detail_trackers_expander_image);
                    content = Views.trackersContent;
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

    private TrackersAdapter mTrackersAdapter;

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
                    if ((file.wanted
                            && key.equals(
                                Torrent.SetterFields.FILES_UNWANTED))
                    || (!file.wanted
                            && key.equals(
                                Torrent.SetterFields.FILES_WANTED))
                    ) {
                        indexes.add(file.index);
                    }
                } else {
                    if (file.priority != priority) {
                        indexes.add(file.index);
                        file.changed = true;
                        file.priority = priority;
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
            for (View v : mSelectedTrackers) {
                View parent = (View) v.getParent();
                Tracker tracker = mTrackersAdapter.getItem(
                    mTrackersAdapter.getViews().indexOf(parent));
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

    private LoaderManager.LoaderCallbacks<TorrentDetails> torrentDetailsLoaderCallbacks
        = new LoaderManager.LoaderCallbacks<TorrentDetails>() {

        @Override public Loader<TorrentDetails> onCreateLoader(int id, Bundle bundle) {
            if (id == G.TORRENTS_CURSOR_LOADER_ID && torrentId != -1) {
                return new TorrentDetailsLoader(getActivity());
            }
            return null;
        }

        @Override public void onLoadFinished(Loader<TorrentDetails> loader, TorrentDetails details) {
            if (TorrentDetailPageFragment.this.details != null) {
                TorrentDetailPageFragment.this.details.torrentCursor.close();
                TorrentDetailPageFragment.this.details.filesCursor.close();
                TorrentDetailPageFragment.this.details.trackersCursor.close();
            }
            TorrentDetailPageFragment.this.details = details;
            updateFields(getView());
        }

        @Override public void onLoaderReset(Loader<TorrentDetails> loader) {
            if (TorrentDetailPageFragment.this.details != null) {
                TorrentDetailPageFragment.this.details.torrentCursor.close();
                TorrentDetailPageFragment.this.details.filesCursor.close();
                TorrentDetailPageFragment.this.details.trackersCursor.close();
            }
            TorrentDetailPageFragment.this.details = null;
        }
    };

    private class UpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int index = intent.getIntExtra(G.ARG_TORRENT_INDEX, -1);
            if (index != -1 && torrentId != -1 && getActivity() != null) {
                TorrentDetailFragment fragment
                    = (TorrentDetailFragment) getActivity().getSupportFragmentManager().findFragmentByTag(G.DETAIL_FRAGMENT_TAG);

                if (fragment != null) {
                    int position = fragment.getTorrentPositionInCursor(torrentId);
                    if (position != -1) {
                        if (details != null)
                            G.logD("Updating detail view for '" + Torrent.getName(details.torrentCursor) + "'");

                        Loader<TorrentDetails> loader = getActivity().getSupportLoaderManager().getLoader(G.TORRENT_DETAILS_LOADER_ID);
                        if (loader != null) {
                            loader.onContentChanged();
                        }
                    }
                }
            }
        }
    }

    private UpdateReceiver mUpdateReceiver;

    private class PageUnselectedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mFileActionMode != null) {
                mFileActionMode.finish();
            }
            if (mTrackerActionMode != null) {
                mTrackerActionMode.finish();
            }
        }
    }

    private PageUnselectedReceiver mPageUnselectedReceiver;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TorrentDetailPageFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (priorityNames != null)
            priorityNames = Arrays.asList(getResources().getStringArray(R.array.torrent_priority));
        if (priorityValues != null)
            priorityValues = Arrays.asList(getResources().getStringArray(R.array.torrent_priority_values));
        if (seedRatioModeValues != null)
            seedRatioModeValues = Arrays.asList(getResources().getStringArray(R.array.torrent_seed_ratio_mode_values));

        mExpandedStates[Expanders.OVERVIEW] = true;

        mUpdateReceiver = new UpdateReceiver();
        mPageUnselectedReceiver = new PageUnselectedReceiver();

        if (getArguments().containsKey(G.ARG_PAGE_POSITION)) {
            int position = getArguments().getInt(G.ARG_PAGE_POSITION);
            TorrentDetailFragment fragment
                = (TorrentDetailFragment) getActivity().getSupportFragmentManager().findFragmentByTag(G.DETAIL_FRAGMENT_TAG);

            if (fragment != null) {
                torrentId = fragment.getTorrentId(position);
            }
        }
        getActivity().getSupportLoaderManager().initLoader(G.TORRENT_DETAILS_LOADER_ID,
            null, torrentDetailsLoaderCallbacks);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            final Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_torrent_detail_page, container, false);

        root.findViewById(R.id.torrent_detail_overview_expander).setOnClickListener(mExpanderListener);
        root.findViewById(R.id.torrent_detail_files_expander).setOnClickListener(mExpanderListener);
        root.findViewById(R.id.torrent_detail_limits_expander).setOnClickListener(mExpanderListener);
        root.findViewById(R.id.torrent_detail_trackers_expander).setOnClickListener(mExpanderListener);

        Views.overviewContent = (LinearLayout) root.findViewById(R.id.torrent_detail_overview_content);
        Views.filesContent = (LinearLayout) root.findViewById(R.id.torrent_detail_files_content);
        Views.limitsContent = (LinearLayout) root.findViewById(R.id.torrent_detail_limits_content);
        Views.trackersContent = (LinearLayout) root.findViewById(R.id.torrent_detail_trackers_content);

        Views.trackersContainer = (LinearLayout) root.findViewById(R.id.torrent_detail_trackers_list);

        Views.name = (TextView) root.findViewById(R.id.torrent_detail_title);
        Views.have = (TextView) root.findViewById(R.id.torrent_have);
        Views.downloaded = (TextView) root.findViewById(R.id.torrent_downloaded);
        Views.uploaded = (TextView) root.findViewById(R.id.torrent_uploaded);
        Views.runningTime = (TextView) root.findViewById(R.id.torrent_running_time);
        Views.remainingTime = (TextView) root.findViewById(R.id.torrent_remaining_time);
        Views.lastActivity = (TextView) root.findViewById(R.id.torrent_last_activity);
        Views.size = (TextView) root.findViewById(R.id.torrent_size);
        Views.hash = (TextView) root.findViewById(R.id.torrent_hash);
        Views.privacy = (TextView) root.findViewById(R.id.torrent_privacy);
        Views.origin = (TextView) root.findViewById(R.id.torrent_origin);
        Views.comment = (TextView) root.findViewById(R.id.torrent_comment);
        Views.state = (TextView) root.findViewById(R.id.torrent_state);
        Views.added = (TextView) root.findViewById(R.id.torrent_added);
        Views.queue = (TextView) root.findViewById(R.id.torrent_queue);
        Views.error = (TextView) root.findViewById(R.id.torrent_error);
        Views.location = (TextView) root.findViewById(R.id.torrent_location);

        Views.globalLimits = (CheckBox) root.findViewById(R.id.torrent_global_limits);
        Views.torrentPriority = (Spinner) root.findViewById(R.id.torrent_priority);
        Views.queuePosition = (EditText) root.findViewById(R.id.torrent_queue_position);
        Views.downloadLimited = (CheckBox) root.findViewById(R.id.torrent_limit_download_check);
        Views.downloadLimit = (EditText) root.findViewById(R.id.torrent_limit_download);
        Views.uploadLimited = (CheckBox) root.findViewById(R.id.torrent_limit_upload_check);
        Views.uploadLimit = (EditText) root.findViewById(R.id.torrent_limit_upload);
        Views.seedRatioMode = (Spinner) root.findViewById(R.id.torrent_seed_ratio_mode);
        Views.seedRatioLimit = (EditText) root.findViewById(R.id.torrent_seed_ratio_limit);
        Views.peerLimit = (EditText) root.findViewById(R.id.torrent_peer_limit);

        if (savedInstanceState != null) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (savedInstanceState.containsKey(STATE_EXPANDED)) {
                        mExpandedStates = savedInstanceState.getBooleanArray(STATE_EXPANDED);
                        Views.overviewContent.setVisibility(mExpandedStates[Expanders.OVERVIEW] ? View.VISIBLE : View.GONE);
                        Views.filesContent.setVisibility(mExpandedStates[Expanders.FILES] ? View.VISIBLE : View.GONE);
                        Views.limitsContent.setVisibility(mExpandedStates[Expanders.LIMITS] ? View.VISIBLE : View.GONE);
                        Views.trackersContent.setVisibility(mExpandedStates[Expanders.TRACKERS] ? View.VISIBLE : View.GONE);
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
        FilesDataSetObserver filesObserver = new FilesDataSetObserver();
        mFilesAdapter.registerDataSetObserver(filesObserver);

        mTrackersAdapter = new TrackersAdapter();
        TrackersDataSetObserver trackersObserver = new TrackersDataSetObserver();
        mTrackersAdapter.registerDataSetObserver(trackersObserver);

        updateFields(root);

        Views.globalLimits.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setTorrentProperty(Torrent.SetterFields.SESSION_LIMITS, isChecked);
            }
        });

        Views.torrentPriority.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    String val=priorityValues.get(pos);
                    int priority=Torrent.Priority.NORMAL;

                    if (val.equals("low")) {
                        priority=Torrent.Priority.LOW;
                    } else if (val.equals("high")) {
                        priority=Torrent.Priority.HIGH;
                    }
                    setTorrentProperty(Torrent.SetterFields.TORRENT_PRIORITY, priority);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

        Views.queuePosition.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int position;
                    try {
                        position=Integer.parseInt(v.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    setTorrentProperty(Torrent.SetterFields.QUEUE_POSITION, position);
                }
                new Handler().post(mLoseFocus);
                return false;
            }
        });

        Views.downloadLimited.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Views.downloadLimit.setEnabled(isChecked);
                setTorrentProperty(Torrent.SetterFields.DOWNLOAD_LIMITED, isChecked);
            }
        });

        Views.downloadLimit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                long limit;
                try {
                    limit=Long.parseLong(v.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                setTorrentProperty(Torrent.SetterFields.DOWNLOAD_LIMIT, limit);

                new Handler().post(mLoseFocus);
                return false;
            }

        });

        Views.uploadLimited.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Views.uploadLimit.setEnabled(isChecked);
                setTorrentProperty(Torrent.SetterFields.UPLOAD_LIMITED, isChecked);
            }
        });

        Views.uploadLimit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                long limit;
                try {
                    limit=Long.parseLong(v.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                setTorrentProperty(Torrent.SetterFields.UPLOAD_LIMIT, limit);

                new Handler().post(mLoseFocus);
                return false;
            }

        });

        Views.seedRatioMode.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    String val=seedRatioModeValues.get(pos);
                    int mode=Torrent.SeedRatioMode.GLOBAL_LIMIT;
                    if (val.equals("user")) {
                        mode=Torrent.SeedRatioMode.TORRENT_LIMIT;
                    } else if (val.equals("infinite")) {
                        mode=Torrent.SeedRatioMode.NO_LIMIT;
                    }
                    Views.seedRatioLimit.setEnabled(val.equals("user"));
                    setTorrentProperty(Torrent.SetterFields.SEED_RATIO_MODE, mode);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

        Views.seedRatioLimit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                float limit;
                try {
                    limit=Float.parseFloat(v.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                setTorrentProperty(Torrent.SetterFields.SEED_RATIO_LIMIT, limit);

                new Handler().post(mLoseFocus);
                return false;
            }

        });

        Views.peerLimit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                int limit;
                try {
                    limit=Integer.parseInt(v.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                setTorrentProperty(Torrent.SetterFields.PEER_LIMIT, limit);

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
                if (Views.queuePosition.hasFocus()) {
                    Views.queuePosition.clearFocus();
                } else if (Views.downloadLimit.hasFocus()) {
                    Views.downloadLimit.clearFocus();
                } else if (Views.uploadLimit.hasFocus()) {
                    Views.uploadLimit.clearFocus();
                } else if (Views.seedRatioLimit.hasFocus()) {
                    Views.seedRatioLimit.clearFocus();
                } else if (Views.peerLimit.hasFocus()) {
                    Views.peerLimit.clearFocus();
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

    @Override
    public void onResume() {
        super.onResume();

        getActivity().registerReceiver(mUpdateReceiver, new IntentFilter(G.INTENT_TORRENT_UPDATE));
        getActivity().registerReceiver(mPageUnselectedReceiver, new IntentFilter(G.INTENT_PAGE_UNSELECTED));
    }

    @Override
    public void onPause() {
        super.onPause();

        getActivity().unregisterReceiver(mUpdateReceiver);
        getActivity().unregisterReceiver(mPageUnselectedReceiver);
    }

    private void setTorrentProperty(String key, Object value) {

        Loader<TransmissionData> loader = getActivity()
            .getSupportLoaderManager().getLoader(
                    G.TORRENTS_LOADER_ID);
        ((TransmissionDataLoader) loader).setTorrentProperty(torrentId, key, value);
    }

    private void updateFields(View root) {
        if (root == null || details == null) return;

        /* TODO: check whether a view's value is different from the cursor's before updating */

        String name = Torrent.getName(details.torrentCursor);
        int status = Torrent.getStatus(details.torrentCursor);
        float uploadRatio = Torrent.getUploadRatio(details.torrentCursor);
        float seedRatioLimit = Torrent.getUploadRatio(details.torrentCursor);
        int queuePosition = Torrent.getQueuePosition(details.torrentCursor);

        if (!name.equals(Views.name.getText()))
            Views.name.setText(name);

        /* Overview start */
        Cursor cursor = details.torrentCursor;
        if (Views.overviewContent.getVisibility() != View.GONE) {
            long now = new Timestamp(new Date().getTime()).getTime() / 1000;

            float metadataPercent = Torrent.getMetadataPercentDone(cursor);
            long haveValid = Torrent.getHaveValid(cursor);
            long sizeWhenDone = Torrent.getSizeWhenDone(cursor);
            long leftUntilDone = Torrent.getLeftUntilDone(cursor);
            long downloadedEver = Torrent.getDownloadedEver(cursor);
            long uploadedEver = Torrent.getUploadedEver(cursor);
            long startDate = Torrent.getStartDate(cursor);
            long activityDate = Torrent.getActivityDate(cursor);
            long addedDate = Torrent.getAddedDate(cursor);
            long lastActive = now - activityDate;
            long eta = Torrent.getEta(cursor);
            long totalSize = Torrent.getTotalSize(cursor);
            long pieceSize = Torrent.getPieceSize(cursor);
            int pieceCount = Torrent.getPieceCount(cursor);
            String hash = Torrent.getHashString(cursor);
            boolean isPrivate = Torrent.isPrivate(cursor);
            long dateCreated = Torrent.getDateCreated(cursor);
            String creator = Torrent.getCreator(cursor);
            String comment = Torrent.getComment(cursor);
            int error = Torrent.getError(cursor);
            String errorString = Torrent.getErrorString(cursor);
            String downloadDir = Torrent.getDownloadDir(cursor);

            if (metadataPercent == 1) {
                String have = String.format(
                    getString(R.string.torrent_have_format),
                    G.readableFileSize(haveValid > 0
                        ? haveValid : sizeWhenDone - leftUntilDone),
                    G.readableFileSize(sizeWhenDone),
                    G.readablePercent(100 * (
                        sizeWhenDone > 0
                            ? (float) (sizeWhenDone - leftUntilDone) / sizeWhenDone
                            : 1
                    ))
                );
                if (!have.equals(Views.have.getText())) {
                    Views.have.setText(have);
                }

                String downloaded = downloadedEver == 0
                    ? getString(R.string.unknown)
                    : G.readableFileSize(downloadedEver);
                if (!downloaded.equals(Views.downloaded.getText())) {
                    Views.downloaded.setText(downloaded);
                }

                String uploaded = G.readableFileSize(uploadedEver);
                if (!uploaded.equals(Views.uploaded.getText())) {
                    Views.uploaded.setText(uploaded);
                }

                String runningTime = status == Torrent.Status.STOPPED
                    ? getString(R.string.status_finished)
                    : startDate > 0
                    ? G.readableRemainingTime(now - startDate, getActivity())
                    : getString(R.string.unknown);
                Views.runningTime.setText(runningTime);

                String remainingTime = eta < 0
                    ? getString(R.string.unknown)
                    : G.readableRemainingTime(eta, getActivity());
                if (!remainingTime.equals(Views.remainingTime.getText())) {
                    Views.remainingTime.setText(remainingTime);
                }

                String lastActivity = lastActive < 0 || activityDate <= 0
                    ? getString(R.string.unknown)
                    : lastActive < 5
                    ? getString(R.string.torrent_active_now)
                    : String.format(
                    getString(R.string.torrent_added_format),
                    G.readableRemainingTime(lastActive, getActivity()));
                Views.lastActivity.setText(lastActivity);

                String size = String.format(
                    getString(R.string.torrent_size_format),
                    G.readableFileSize(totalSize),
                    pieceCount,
                    G.readableFileSize(pieceSize)
                );
                if (!size.equals(Views.size.getText())) {
                    Views.size.setText(size);
                }

                if (!hash.equals(Views.hash.getText())) {
                    Views.hash.setText(hash);
                }

                Views.privacy.setText(isPrivate ? R.string.torrent_private : R.string.torrent_public);

                Date creationDate = new Date(dateCreated * 1000);
                String origin = TextUtils.isEmpty(creator)
                    ? String.format(
                        getString(R.string.torrent_origin_format),
                        creationDate.toString()
                    )
                    : String.format(
                        getString(R.string.torrent_origin_creator_format),
                        creator,
                        creationDate.toString()
                    );
                if (!origin.equals(Views.origin.getText())) {
                    Views.origin.setText(origin);
                }

                if (!comment.equals(Views.comment.getText())) {
                    Views.comment.setText(comment);
                }
            } else {
                String have = getString(R.string.none);
                if (!have.equals(Views.have.getText())) {
                    Views.have.setText(have);
                }
            }
            int state = R.string.none;
            switch(status) {
                case Torrent.Status.STOPPED:
                    state = uploadRatio < seedRatioLimit
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
                    state = metadataPercent < 0
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
            String stateString = getString(state);
            if (!stateString.equals(Views.state.getText())) {
                Views.state.setText(stateString);
            }

            if (addedDate > 0) {
                Views.added.setText(
                        String.format(
                                getString(R.string.torrent_added_format),
                                G.readableRemainingTime(now - addedDate, getActivity())
                        )
                );
            } else {
                Views.added.setText(R.string.unknown);
            }
            String queue = Integer.toString(queuePosition);
            if (!queue.equals(Views.queue.getText())) {
                Views.queue.setText(queue);
            }

            if (error == Torrent.Error.OK) {
                if (Views.error.isEnabled()) {
                    Views.error.setText(R.string.no_tracker_errors);
                    Views.error.setEnabled(false);
                }
            } else {
                if (!Views.error.isEnabled()) {
                    Views.error.setText(errorString);
                    Views.error.setEnabled(true);
                }
            }
            if (!downloadDir.equals(Views.location.getText())) {
                Views.location.setText(downloadDir);
            }
        }

        /* Files start */
        if (Views.filesContent.getVisibility() != View.GONE
                && details.filesCursor.getCount() > 0) {

            mFilesAdapter.setNotifyOnChange(false);
            if (mFilesAdapter.getCount() == 0) {
                mFilesAdapter.clear();
                ArrayList<TorrentFile> torrentFiles = new ArrayList<TorrentFile>();

                details.filesCursor.moveToFirst();

                int index = 0;
                while (!details.filesCursor.isAfterLast()) {
                    TorrentFile file = new TorrentFile(index, details.filesCursor);
                    torrentFiles.add(file);

                    details.filesCursor.moveToNext();
                    ++index;
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
        if (Views.limitsContent.getVisibility() != View.GONE) {
            boolean sessionLimitsHonored = Torrent.areSessionLimitsHonored(cursor);
            int torrentPriority = Torrent.getTorrentPriority(cursor);
            boolean downloadLimited = Torrent.isDownloadLimited(cursor);
            long downloadLimit = Torrent.getDownloadLimit(cursor);
            boolean uploadLimited = Torrent.isUploadLimited(cursor);
            long uploadLimit = Torrent.getUploadLimit(cursor);
            int seedRatioMode = Torrent.getSeedRatioMode(cursor);
            int peerLimit = Torrent.getPeerLimit(cursor);

            if (sessionLimitsHonored != Views.globalLimits.isChecked()) {
                Views.globalLimits.setChecked(sessionLimitsHonored);
            }

            String priority = "normal";
            switch (torrentPriority) {
                case Torrent.Priority.LOW:
                    priority = "low";
                    break;
                case Torrent.Priority.HIGH:
                    priority = "high";
                    break;
            }
            if (Views.torrentPriority.getSelectedItemPosition() != priorityValues.indexOf(priority)) {
                Views.torrentPriority.setSelection(priorityValues.indexOf(priority));
            }

            String queue = Integer.toString(queuePosition);
            if (!queue.equals(Views.queuePosition.getText().toString())) {
                Views.queuePosition.setText(queue);
            }

            if (downloadLimited != Views.downloadLimited.isChecked()) {
                Views.downloadLimited.setChecked(downloadLimited);
            }

            String download = Long.toString(downloadLimit);
            if (!download.equals(Views.downloadLimit.getText().toString())) {
                Views.downloadLimit.setText(download);
            }
            if (Views.downloadLimit.isEnabled() != Views.downloadLimited.isChecked()) {
                Views.downloadLimit.setEnabled(Views.downloadLimited.isChecked());
            }

            if (Views.uploadLimited.isChecked() != uploadLimited) {
                Views.uploadLimited.setChecked(uploadLimited);
            }

            String upload = Long.toString(uploadLimit);
            if (!upload.equals(Views.uploadLimit.getText().toString())) {
                Views.uploadLimit.setText(upload);
            }
            if (Views.uploadLimit.isEnabled() != Views.uploadLimited.isChecked()) {
                Views.uploadLimit.setEnabled(Views.uploadLimited.isChecked());
            }

            String mode = "global";
            switch (seedRatioMode) {
                case Torrent.SeedRatioMode.TORRENT_LIMIT:
                    mode = "user";
                    break;
                case Torrent.SeedRatioMode.NO_LIMIT:
                    mode = "infinite";
                    break;
            }
            if (Views.seedRatioMode.getSelectedItemPosition() != seedRatioModeValues.indexOf(mode)) {
                Views.seedRatioMode.setSelection(seedRatioModeValues.indexOf(mode));
            }

            String ratio = G.readablePercent(seedRatioLimit);
            if (!ratio.equals(Views.seedRatioLimit.getText().toString())) {
                Views.seedRatioLimit.setText(ratio);
            }

            String peers = Integer.toString(peerLimit);
            if (!peers.equals(Views.peerLimit.getText().toString())) {
                Views.peerLimit.setText(peers);
            }
        }

        /* Trackers start */
        if (Views.trackersContent.getVisibility() != View.GONE
                && details.trackersCursor.getCount() > 0) {
            mTrackersAdapter.setNotifyOnChange(false);
            if (mTrackersAdapter.getCount() != details.trackersCursor.getCount()) {
                mTrackersAdapter.clear();

                int index = 0;
                ArrayList<Tracker> trackers = new ArrayList<Tracker>();

                details.trackersCursor.moveToFirst();

                while (!details.trackersCursor.isAfterLast()) {
                    Tracker tracker = new Tracker(index, details.trackersCursor);
                    trackers.add(tracker);

                    details.trackersCursor.moveToNext();
                    ++index;
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
            if (file.wanted) {
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
        public int index = -1;

        public String directory;
        public String name;

        public long bytesCompleted;
        public long length;
        public int priority;
        public boolean wanted;

        boolean changed = false;

        public TorrentFile(int index, Cursor cursor) {
            this.index = index;

            setInfo(cursor);
        }

        public TorrentFile(String directory) {
            this.directory = directory;
        }

        public void setInfo(Cursor cursor) {
            String path = Torrent.File.getName(cursor);
            File f = new File(path);
            directory = f.getParent();
            name = f.getName();

            bytesCompleted = Torrent.File.getBytesCompleted(cursor);
            length = Torrent.File.getLength(cursor);
            priority = Torrent.File.getPriority(cursor);
            wanted = Torrent.File.isWanted(cursor);
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
                if (file.name == null) {
                    rowView = vi.inflate(R.layout.torrent_detail_files_directory_row, null);
                } else {
                    rowView = vi.inflate(R.layout.torrent_detail_files_row, null);
                }
                initial = true;
            }

            if (file.name == null) {
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
                            if (file.wanted != isChecked) {
                                if (isChecked) {
                                    setTorrentProperty(Torrent.SetterFields.FILES_WANTED, file.index);
                                } else {
                                    setTorrentProperty(Torrent.SetterFields.FILES_UNWANTED, file.index);
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
                switch(file.priority) {
                    case Torrent.Priority.LOW:
                        priority = priorityNames.get(0);
                        break;
                    case Torrent.Priority.HIGH:
                        priority = priorityNames.get(2);
                        break;
                    default:
                        priority = priorityNames.get(1);
                        break;
                }

                row.setText(Html.fromHtml(String.format(
                    getString(R.string.torrent_detail_file_format),
                    file.name,
                    G.readableFileSize(file.bytesCompleted),
                    G.readableFileSize(file.length),
                    priority
                )));

                row.setChecked(file.wanted);

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
        public FilesDataSetObserver() { }

        @Override public void onChanged() {
            int position = details.filesCursor.getPosition();

            for (int i = 0; i < mFilesAdapter.getCount(); i++) {
                TorrentFile file = mFilesAdapter.getItem(i);
                View v = null;
                boolean hasChild = false;

                if (!details.filesCursor.moveToPosition(file.index)) {
                    continue;
                }

                if (i < Views.filesContent.getChildCount()) {
                    v = Views.filesContent.getChildAt(i);
                    hasChild = true;
                }
                if (!hasChild || (file.index != -1 && fileChanged(file, details.filesCursor))) {
                    v = mFilesAdapter.getView(i, v, null);
                    if (!hasChild) {
                        Views.filesContent.addView(v, i);
                    }
                }
            }

            details.filesCursor.moveToPosition(position);
        }

        @Override public void onInvalidated() {
            Views.filesContent.removeAllViews();
        }

        private boolean fileChanged(TorrentFile file, Cursor cursor) {
            boolean changed = false;

            if (file.changed
                    || file.wanted != Torrent.File.isWanted(cursor)
                    || file.bytesCompleted != Torrent.File.getBytesCompleted(cursor)
                    || file.priority != Torrent.File.getPriority(cursor)) {
                file.setInfo(cursor);
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

        public Tracker(int index, Cursor cursor) {
            this.index = index;

            setInfo(cursor);
        }

        public void setInfo(Cursor cursor) {
            this.announce = Torrent.Tracker.getAnnounce(cursor);
            try {
                URI uri = new URI(this.announce);
                this.host = uri.getHost();
            } catch (URISyntaxException e) {
                this.host = getString(R.string.tracker_unknown_host);
            }

            this.id = Torrent.Tracker.getId(cursor);
            this.scrape = Torrent.Tracker.getScrape(cursor);
            this.tier = Torrent.Tracker.getTier(cursor);
            this.seederCount = Torrent.Tracker.getSeederCount(cursor);
            this.leecherCount = Torrent.Tracker.getLeecherCount(cursor);
            this.hasAnnounced = Torrent.Tracker.hasAnnounced(cursor);
            this.lastAnnounceTime = Torrent.Tracker.getLastAnnounceTime(cursor);
            this.hasLastAnnounceSucceeded = Torrent.Tracker.hasLastAnnounceSucceeded(cursor);
            this.lastAnnouncePeerCount = Torrent.Tracker.getLastAnnouncePeerCount(cursor);
            this.lastAnnounceResult = Torrent.Tracker.getLastAnnounceResult(cursor);
            this.hasScraped = Torrent.Tracker.hasScraped(cursor);
            this.lastScrapeTime = Torrent.Tracker.getLastScrapeTime(cursor);
            this.hasLastScrapeSucceeded = Torrent.Tracker.hasLastScrapeSucceeded(cursor);
            this.lastScrapeResult = Torrent.Tracker.getLastScrapeResult(cursor);
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
                        final List<Object> tuple = new ArrayList<Object>();
                        tuple.add(tracker.id);

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

                                            tuple.add(url.getText().toString());

                                            setTorrentProperty(Torrent.SetterFields.TRACKER_REPLACE, tuple);

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
        public TrackersDataSetObserver() { }

        @Override public void onChanged() {
            int position = details.trackersCursor.getPosition();

            for (int i = 0; i < mTrackersAdapter.getCount(); i++) {
                Tracker tracker = mTrackersAdapter.getItem(i);
                View v = null;
                boolean hasChild = false;

                if (!details.trackersCursor.moveToPosition(tracker.index)) {
                    continue;
                }

                if (i < Views.trackersContainer.getChildCount()) {
                    v = Views.trackersContainer.getChildAt(i);
                    hasChild = true;
                }
                if (!hasChild || (tracker.index != -1 && trackerChanged(tracker, details.trackersCursor))) {
                    v = mTrackersAdapter.getView(i, v, null);
                    if (!hasChild) {
                        Views.trackersContainer.addView(v, i);
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
                    Views.trackersContainer.removeView(v);
                }

                views.remove(starting);
            }

            details.trackersCursor.moveToPosition(position);
        }

        @Override public void onInvalidated() {
            Views.trackersContainer.removeAllViews();
        }

        private boolean trackerChanged(Tracker tracker, Cursor cursor) {
            boolean changed = false;

            if (!tracker.announce.equals(Torrent.Tracker.getAnnounce(cursor))
                    || tracker.lastAnnounceTime != Torrent.Tracker.getLastAnnounceTime(cursor)
                    || tracker.lastScrapeTime != Torrent.Tracker.getLastScrapeTime(cursor)
                    || tracker.leecherCount != Torrent.Tracker.getLeecherCount(cursor)
                    || tracker.seederCount != Torrent.Tracker.getSeederCount(cursor)) {
                tracker.setInfo(cursor);
                changed = true;
            }

            return changed;
        }

        @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
        private void animateRemoveView(final View v) {
            v.animate().setDuration(250).alpha(0).translationY(-50).withEndAction(new Runnable() {
                @Override public void run() {
                    Views.trackersContainer.removeView(v);
                }
            });
        }
    }

    private class TorrentDetailsLoader extends AsyncTaskLoader<TorrentDetails> {
        public TorrentDetailsLoader(Context context) {
            super(context);
        }
        @Override public TorrentDetails loadInBackground() {
            DataSource readSource = new DataSource(getContext());

            readSource.open();

            TorrentDetails details = readSource.getTorrentDetails(torrentId);

            readSource.close();

            return details;
        }
    }
}
