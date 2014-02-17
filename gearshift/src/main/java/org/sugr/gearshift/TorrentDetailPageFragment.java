package org.sugr.gearshift;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
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
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
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
import android.view.ViewTreeObserver;
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
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.service.DataServiceManagerInterface;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A fragment representing a single Torrent detail screen.
 * This fragment is either contained in a {@link TorrentListActivity}
 * in two-pane mode (on tablets) or a {@link TorrentDetailActivity}
 * on handsets.
 */
public class TorrentDetailPageFragment extends Fragment {
    private String torrentHash;
    private TorrentDetails details;

    private static List<String> priorityNames;
    private static List<String> priorityValues;
    private static List<String> seedRatioModeValues;

    private static final String STATE_EXPANDED = "expanded_states";
    private static final String STATE_SCROLL_POSITION = "scroll_position_state";
    private static final String STATE_TORRENT_HASH = "torrent_hash";

    private static class Expanders {
        public static final int TOTAL_EXPANDERS = 4;

        public static final int OVERVIEW = 0;
        public static final int FILES = 1;
        public static final int LIMITS = 2;
        public static final int TRACKERS = 3;
    }

    private class Views {
        public LinearLayout overviewContent;
        public LinearLayout filesContent;
        public LinearLayout limitsContent;
        public LinearLayout trackersContent;

        public LinearLayout trackersContainer;

        public TextView name;
        public TextView have;
        public TextView downloaded;
        public TextView uploaded;
        public TextView runningTime;
        public TextView remainingTime;
        public TextView lastActivity;
        public TextView size;
        public TextView hash;
        public TextView privacy;
        public TextView origin;
        public TextView comment;
        public TextView state;
        public TextView added;
        public TextView queue;
        public TextView error;
        public TextView location;

        public CheckBox globalLimits;
        public Spinner torrentPriority;
        public EditText queuePosition;
        public CheckBox downloadLimited;
        public EditText downloadLimit;
        public CheckBox uploadLimited;
        public EditText uploadLimit;
        public Spinner seedRatioMode;
        public EditText seedRatioLimit;
        public EditText peerLimit;
    }
    private Views views;

    private boolean[] expandedStates = new boolean[Expanders.TOTAL_EXPANDERS];

    private View.OnClickListener expanderListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            View image;
            View content;
            int index;
            switch(v.getId()) {
                case R.id.torrent_detail_overview_expander:
                    image = v.findViewById(R.id.torrent_detail_overview_expander_image);
                    content = views.overviewContent;
                    index = Expanders.OVERVIEW;
                    break;
                case R.id.torrent_detail_files_expander:
                    image = v.findViewById(R.id.torrent_detail_files_expander_image);
                    content = views.filesContent;
                    index = Expanders.FILES;
                    break;
                case R.id.torrent_detail_limits_expander:
                    image = v.findViewById(R.id.torrent_detail_limits_expander_image);
                    content = views.limitsContent;
                    index = Expanders.LIMITS;
                    break;
                case R.id.torrent_detail_trackers_expander:
                    image = v.findViewById(R.id.torrent_detail_trackers_expander_image);
                    content = views.trackersContent;
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
                expandedStates[index] = true;
                updateFields(getView());
            } else {
                content.setVisibility(View.GONE);
                image.setBackgroundResource(R.drawable.ic_section_expand);
                expandedStates[index] = false;
            }

        }
    };

    private FilesAdapter filesAdapter;

    private TrackersAdapter trackersAdapter;

    private ActionMode fileActionMode;
    private Set<View> selectedFiles = new HashSet<>();

    private ActionMode.Callback actionModeFiles = new ActionMode.Callback() {

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            fileActionMode= null;
            for (View v : selectedFiles) {
                v.setActivated(false);
            }
            selectedFiles.clear();
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
                    List<View> files = filesAdapter.getViews();

                    for (View v : files) {
                        if (v != null) {
                            if (!v.isActivated()) {
                                v.setActivated(true);
                                selectedFiles.add(v);
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
            List<View> allViews = filesAdapter.getViews();
            ArrayList<Integer> indexes = new ArrayList<>();
            for (View v : selectedFiles) {
                TorrentFile file = filesAdapter.getItem(allViews.indexOf(v));
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
                filesAdapter.notifyDataSetChanged();
                if (priority == null) {
                    mode.finish();
                    ((TransmissionSessionInterface) getActivity()).setRefreshing(true,
                        DataService.Requests.GET_TORRENTS);
                    DataServiceManager manager =
                        ((DataServiceManagerInterface) getActivity()).getDataServiceManager();

                    if (manager != null) {
                        manager.update();
                    }
                }
            }
            return true;
        }
    };

    private ActionMode trackerActionMode;
    private Set<View> selectedTrackers = new HashSet<>();

    private ActionMode.Callback actionModeTrackers = new ActionMode.Callback() {

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            trackerActionMode = null;
            for (View v : selectedTrackers) {
                v.setActivated(false);
            }
            selectedTrackers.clear();
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
                    List<View> trackers = trackersAdapter.getViews();

                    for (View v : trackers) {
                        if (v != null) {
                            View info = v.findViewById(R.id.torrent_detail_trackers_row_info);
                            if (info != null && !info.isActivated()) {
                                info.setActivated(true);
                                selectedTrackers.add(info);
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
            ArrayList<Integer> ids = new ArrayList<>();
            for (View v : selectedTrackers) {
                View parent = (View) v.getParent();
                Tracker tracker = trackersAdapter.getItem(
                    trackersAdapter.getViews().indexOf(parent));
                ids.add(tracker.id);
                trackersAdapter.remove(tracker);
            }
            if (ids.size() > 0) {
                setTorrentProperty(key, ids);
                mode.finish();
                ((TransmissionSessionInterface) getActivity()).setRefreshing(true,
                    DataService.Requests.SET_TORRENT);
                DataServiceManager manager =
                    ((DataServiceManagerInterface) getActivity()).getDataServiceManager();

                if (manager != null) {
                    manager.update();
                }
            }

            return true;
        }
    };

    private Runnable loseFocusRunnable = new Runnable() {
        @Override public void run() {
            getView().findViewById(R.id.torrent_detail_page_container).requestFocus();
        }
    };

    private class UpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String hash = intent.getStringExtra(G.ARG_TORRENT_HASH_STRING);
            if (hash != null && hash.equals(torrentHash) && getActivity() != null) {
                TorrentDetailFragment fragment
                    = (TorrentDetailFragment) getActivity().getSupportFragmentManager().findFragmentByTag(G.DETAIL_FRAGMENT_TAG);

                if (fragment != null) {
                    new TorrentDetailTask().execute(torrentHash);
                }
            }
        }
    }

    private UpdateReceiver updateReceiver;

    private class PageUnselectedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (fileActionMode != null) {
                fileActionMode.finish();
            }
            if (trackerActionMode != null) {
                trackerActionMode.finish();
            }
        }
    }

    private PageUnselectedReceiver pageUnselectedReceiver;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TorrentDetailPageFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (priorityNames == null)
            priorityNames = Arrays.asList(getResources().getStringArray(R.array.torrent_priority));
        if (priorityValues == null)
            priorityValues = Arrays.asList(getResources().getStringArray(R.array.torrent_priority_values));
        if (seedRatioModeValues == null)
            seedRatioModeValues = Arrays.asList(getResources().getStringArray(R.array.torrent_seed_ratio_mode_values));

        expandedStates[Expanders.OVERVIEW] = true;

        updateReceiver = new UpdateReceiver();
        pageUnselectedReceiver = new PageUnselectedReceiver();

        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_TORRENT_HASH)) {
            torrentHash = savedInstanceState.getString(STATE_TORRENT_HASH);
        } else if (getArguments().containsKey(G.ARG_PAGE_POSITION)) {
            int position = getArguments().getInt(G.ARG_PAGE_POSITION);
            TorrentDetailFragment fragment
                = (TorrentDetailFragment) getActivity().getSupportFragmentManager().findFragmentByTag(G.DETAIL_FRAGMENT_TAG);

            if (fragment != null) {
                torrentHash = fragment.getTorrentHashString(position);
            }
        }

        if (torrentHash != null) {
            new TorrentDetailTask().execute(torrentHash);
        }
    }

    @Override public void onDestroy() {
        if (details != null) {
            details.torrentCursor.close();
            details.filesCursor.close();
            details.trackersCursor.close();

            details = null;
        }
        super.onDestroy();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            final Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_torrent_detail_page, container, false);

        root.findViewById(R.id.torrent_detail_overview_expander).setOnClickListener(expanderListener);
        root.findViewById(R.id.torrent_detail_files_expander).setOnClickListener(expanderListener);
        root.findViewById(R.id.torrent_detail_limits_expander).setOnClickListener(expanderListener);
        root.findViewById(R.id.torrent_detail_trackers_expander).setOnClickListener(expanderListener);

        views = new Views();

        views.overviewContent = (LinearLayout) root.findViewById(R.id.torrent_detail_overview_content);
        views.filesContent = (LinearLayout) root.findViewById(R.id.torrent_detail_files_content);
        views.limitsContent = (LinearLayout) root.findViewById(R.id.torrent_detail_limits_content);
        views.trackersContent = (LinearLayout) root.findViewById(R.id.torrent_detail_trackers_content);

        views.trackersContainer = (LinearLayout) root.findViewById(R.id.torrent_detail_trackers_list);

        views.name = (TextView) root.findViewById(R.id.torrent_detail_title);
        views.have = (TextView) root.findViewById(R.id.torrent_have);
        views.downloaded = (TextView) root.findViewById(R.id.torrent_downloaded);
        views.uploaded = (TextView) root.findViewById(R.id.torrent_uploaded);
        views.runningTime = (TextView) root.findViewById(R.id.torrent_running_time);
        views.remainingTime = (TextView) root.findViewById(R.id.torrent_remaining_time);
        views.lastActivity = (TextView) root.findViewById(R.id.torrent_last_activity);
        views.size = (TextView) root.findViewById(R.id.torrent_size);
        views.hash = (TextView) root.findViewById(R.id.torrent_hash);
        views.privacy = (TextView) root.findViewById(R.id.torrent_privacy);
        views.origin = (TextView) root.findViewById(R.id.torrent_origin);
        views.comment = (TextView) root.findViewById(R.id.torrent_comment);
        views.state = (TextView) root.findViewById(R.id.torrent_state);
        views.added = (TextView) root.findViewById(R.id.torrent_added);
        views.queue = (TextView) root.findViewById(R.id.torrent_queue);
        views.error = (TextView) root.findViewById(R.id.torrent_error);
        views.location = (TextView) root.findViewById(R.id.torrent_location);

        views.globalLimits = (CheckBox) root.findViewById(R.id.torrent_global_limits);
        views.torrentPriority = (Spinner) root.findViewById(R.id.torrent_priority);
        views.queuePosition = (EditText) root.findViewById(R.id.torrent_queue_position);
        views.downloadLimited = (CheckBox) root.findViewById(R.id.torrent_limit_download_check);
        views.downloadLimit = (EditText) root.findViewById(R.id.torrent_limit_download);
        views.uploadLimited = (CheckBox) root.findViewById(R.id.torrent_limit_upload_check);
        views.uploadLimit = (EditText) root.findViewById(R.id.torrent_limit_upload);
        views.seedRatioMode = (Spinner) root.findViewById(R.id.torrent_seed_ratio_mode);
        views.seedRatioLimit = (EditText) root.findViewById(R.id.torrent_seed_ratio_limit);
        views.peerLimit = (EditText) root.findViewById(R.id.torrent_peer_limit);

        if (savedInstanceState != null) {
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    if (savedInstanceState.containsKey(STATE_EXPANDED)) {
                        expandedStates = savedInstanceState.getBooleanArray(STATE_EXPANDED);
                        views.overviewContent.setVisibility(expandedStates[Expanders.OVERVIEW] ? View.VISIBLE : View.GONE);
                        views.filesContent.setVisibility(expandedStates[Expanders.FILES] ? View.VISIBLE : View.GONE);
                        views.limitsContent.setVisibility(expandedStates[Expanders.LIMITS] ? View.VISIBLE : View.GONE);
                        views.trackersContent.setVisibility(expandedStates[Expanders.TRACKERS] ? View.VISIBLE : View.GONE);
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

        filesAdapter = new FilesAdapter();
        FilesDataSetObserver filesObserver = new FilesDataSetObserver();
        filesAdapter.registerDataSetObserver(filesObserver);

        trackersAdapter = new TrackersAdapter();
        TrackersDataSetObserver trackersObserver = new TrackersDataSetObserver();
        trackersAdapter.registerDataSetObserver(trackersObserver);

        updateFields(root);

        views.globalLimits.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setTorrentProperty(Torrent.SetterFields.SESSION_LIMITS, isChecked);
            }
        });

        views.torrentPriority.setOnItemSelectedListener(
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

        views.queuePosition.setOnEditorActionListener(new TextView.OnEditorActionListener() {
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
                new Handler().post(loseFocusRunnable);
                return false;
            }
        });

        views.downloadLimited.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                views.downloadLimit.setEnabled(isChecked);
                setTorrentProperty(Torrent.SetterFields.DOWNLOAD_LIMITED, isChecked);
            }
        });

        views.downloadLimit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                long limit;
                try {
                    limit=Long.parseLong(v.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                setTorrentProperty(Torrent.SetterFields.DOWNLOAD_LIMIT, limit);

                new Handler().post(loseFocusRunnable);
                return false;
            }

        });

        views.uploadLimited.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                views.uploadLimit.setEnabled(isChecked);
                setTorrentProperty(Torrent.SetterFields.UPLOAD_LIMITED, isChecked);
            }
        });

        views.uploadLimit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                long limit;
                try {
                    limit=Long.parseLong(v.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                setTorrentProperty(Torrent.SetterFields.UPLOAD_LIMIT, limit);

                new Handler().post(loseFocusRunnable);
                return false;
            }

        });

        views.seedRatioMode.setOnItemSelectedListener(
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
                    views.seedRatioLimit.setEnabled(val.equals("user"));
                    setTorrentProperty(Torrent.SetterFields.SEED_RATIO_MODE, mode);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

        views.seedRatioLimit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                float limit;
                try {
                    limit=Float.parseFloat(v.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                setTorrentProperty(Torrent.SetterFields.SEED_RATIO_LIMIT, limit);

                new Handler().post(loseFocusRunnable);
                return false;
            }

        });

        views.peerLimit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                int limit;
                try {
                    limit=Integer.parseInt(v.getText().toString().trim());
                } catch (NumberFormatException e) {
                    return false;
                }
                setTorrentProperty(Torrent.SetterFields.PEER_LIMIT, limit);

                new Handler().post(loseFocusRunnable);
                return false;
            }

        });

        Button addTracker = (Button) root.findViewById(R.id.torrent_detail_add_tracker);
        addTracker.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final ArrayList<String> urls = new ArrayList<>();
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

                                        ((TransmissionSessionInterface) getActivity()).setRefreshing(true,
                                            DataService.Requests.GET_TORRENTS);
                                        DataServiceManager manager =
                                            ((DataServiceManagerInterface) getActivity()).getDataServiceManager();

                                        if (manager != null) {
                                            manager.update();
                                        }
                                    }
                                }).setView(inflater.inflate(R.layout.replace_tracker_dialog, null));
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (views.queuePosition.hasFocus()) {
                    views.queuePosition.clearFocus();
                } else if (views.downloadLimit.hasFocus()) {
                    views.downloadLimit.clearFocus();
                } else if (views.uploadLimit.hasFocus()) {
                    views.uploadLimit.clearFocus();
                } else if (views.seedRatioLimit.hasFocus()) {
                    views.seedRatioLimit.clearFocus();
                } else if (views.peerLimit.hasFocus()) {
                    views.peerLimit.clearFocus();
                }
                return false;
            }
        });

        return root;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBooleanArray(STATE_EXPANDED, expandedStates);
        ScrollView scroll = (ScrollView) getView().findViewById(R.id.detail_scroll);
        if (scroll != null) {
            outState.putInt(STATE_SCROLL_POSITION, scroll.getScrollY());
        }

        outState.putString(STATE_TORRENT_HASH, torrentHash);
    }

    @Override
    public void onResume() {
        super.onResume();

        getActivity().registerReceiver(updateReceiver, new IntentFilter(G.INTENT_TORRENT_UPDATE));
        getActivity().registerReceiver(pageUnselectedReceiver, new IntentFilter(G.INTENT_PAGE_UNSELECTED));
    }

    @Override
    public void onPause() {
        super.onPause();

        getActivity().unregisterReceiver(updateReceiver);
        getActivity().unregisterReceiver(pageUnselectedReceiver);
    }

    private void setTorrentProperty(String key, int value) {
        DataServiceManager manager =
            ((DataServiceManagerInterface) getActivity()).getDataServiceManager();

        if (manager != null) {
            manager.setTorrent(new String[] { torrentHash }, key, value);
        }
    }

    private void setTorrentProperty(String key, long value) {
        DataServiceManager manager =
            ((DataServiceManagerInterface) getActivity()).getDataServiceManager();

        if (manager != null) {
            manager.setTorrent(new String[] { torrentHash }, key, value);
        }
    }

    private void setTorrentProperty(String key, boolean value) {
        DataServiceManager manager =
            ((DataServiceManagerInterface) getActivity()).getDataServiceManager();

        if (manager != null) {
            manager.setTorrent(new String[] { torrentHash }, key, value);
        }
    }

    private void setTorrentProperty(String key, float value) {
        DataServiceManager manager =
            ((DataServiceManagerInterface) getActivity()).getDataServiceManager();

        if (manager != null) {
            manager.setTorrent(new String[] { torrentHash }, key, value);
        }
    }

    private void setTorrentProperty(String key, ArrayList<?> value) {
        DataServiceManager manager =
            ((DataServiceManagerInterface) getActivity()).getDataServiceManager();

        if (manager != null) {
            manager.setTorrent(new String[] { torrentHash }, key, value);
        }
    }

    private void updateFields(View root) {
         if (root == null || details == null || details.torrentCursor.isClosed()
             || details.torrentCursor.getCount() == 0) return;

        String name = Torrent.getName(details.torrentCursor);
        int status = Torrent.getStatus(details.torrentCursor);
        float uploadRatio = Torrent.getUploadRatio(details.torrentCursor);
        float seedRatioLimit = Torrent.getUploadRatio(details.torrentCursor);
        int queuePosition = Torrent.getQueuePosition(details.torrentCursor);

        if (!name.equals(views.name.getText()))
            views.name.setText(name);

        /* Overview start */
        Cursor cursor = details.torrentCursor;
        if (views.overviewContent.getVisibility() != View.GONE) {
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
                if (!have.equals(views.have.getText())) {
                    views.have.setText(have);
                }

                String downloaded = downloadedEver == 0
                    ? getString(R.string.unknown)
                    : G.readableFileSize(downloadedEver);
                if (!downloaded.equals(views.downloaded.getText())) {
                    views.downloaded.setText(downloaded);
                }

                String uploaded = G.readableFileSize(uploadedEver);
                if (!uploaded.equals(views.uploaded.getText())) {
                    views.uploaded.setText(uploaded);
                }

                String runningTime = status == Torrent.Status.STOPPED
                    ? getString(R.string.status_finished)
                    : startDate > 0
                    ? G.readableRemainingTime(now - startDate, getActivity())
                    : getString(R.string.unknown);
                views.runningTime.setText(runningTime);

                String remainingTime = eta < 0
                    ? getString(R.string.unknown)
                    : G.readableRemainingTime(eta, getActivity());
                if (!remainingTime.equals(views.remainingTime.getText())) {
                    views.remainingTime.setText(remainingTime);
                }

                String lastActivity = lastActive < 0 || activityDate <= 0
                    ? getString(R.string.unknown)
                    : lastActive < 5
                    ? getString(R.string.torrent_active_now)
                    : String.format(
                    getString(R.string.torrent_added_format),
                    G.readableRemainingTime(lastActive, getActivity()));
                views.lastActivity.setText(lastActivity);

                String size = String.format(
                    getString(R.string.torrent_size_format),
                    G.readableFileSize(totalSize),
                    pieceCount,
                    G.readableFileSize(pieceSize)
                );
                if (!size.equals(views.size.getText())) {
                    views.size.setText(size);
                }

                if (!hash.equals(views.hash.getText())) {
                    views.hash.setText(hash);
                }

                views.privacy.setText(isPrivate ? R.string.torrent_private : R.string.torrent_public);

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
                if (!origin.equals(views.origin.getText())) {
                    views.origin.setText(origin);
                }

                if (!comment.equals(views.comment.getText())) {
                    views.comment.setText(comment);
                }
            } else {
                String have = getString(R.string.none);
                if (!have.equals(views.have.getText())) {
                    views.have.setText(have);
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
            if (!stateString.equals(views.state.getText())) {
                views.state.setText(stateString);
            }

            if (addedDate > 0) {
                views.added.setText(
                        String.format(
                                getString(R.string.torrent_added_format),
                                G.readableRemainingTime(now - addedDate, getActivity())
                        )
                );
            } else {
                views.added.setText(R.string.unknown);
            }
            String queue = Integer.toString(queuePosition);
            if (!queue.equals(views.queue.getText())) {
                views.queue.setText(queue);
            }

            if (error == Torrent.Error.OK) {
                if (views.error.isEnabled()) {
                    views.error.setText(R.string.no_tracker_errors);
                    views.error.setEnabled(false);
                }
            } else {
                if (!views.error.isEnabled()) {
                    views.error.setText(errorString);
                    views.error.setEnabled(true);
                }
            }
            if (!downloadDir.equals(views.location.getText())) {
                views.location.setText(downloadDir);
            }
        }

        /* Files start */
        if (views.filesContent.getVisibility() != View.GONE
                && details.filesCursor.getCount() > 0) {

            filesAdapter.setNotifyOnChange(false);
            if (filesAdapter.getCount() == 0) {
                filesAdapter.clear();
                ArrayList<TorrentFile> torrentFiles = new ArrayList<>();

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

                ArrayList<Integer> directories = new ArrayList<>();
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

                filesAdapter.addAll(torrentFiles);
            }
            filesAdapter.notifyDataSetChanged();
        }

        /* Limits start */
        if (views.limitsContent.getVisibility() != View.GONE) {
            boolean sessionLimitsHonored = Torrent.areSessionLimitsHonored(cursor);
            int torrentPriority = Torrent.getTorrentPriority(cursor);
            boolean downloadLimited = Torrent.isDownloadLimited(cursor);
            long downloadLimit = Torrent.getDownloadLimit(cursor);
            boolean uploadLimited = Torrent.isUploadLimited(cursor);
            long uploadLimit = Torrent.getUploadLimit(cursor);
            int seedRatioMode = Torrent.getSeedRatioMode(cursor);
            int peerLimit = Torrent.getPeerLimit(cursor);

            if (sessionLimitsHonored != views.globalLimits.isChecked()) {
                views.globalLimits.setChecked(sessionLimitsHonored);
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
            if (views.torrentPriority.getSelectedItemPosition() != priorityValues.indexOf(priority)) {
                views.torrentPriority.setSelection(priorityValues.indexOf(priority));
            }

            String queue = Integer.toString(queuePosition);
            if (!views.queuePosition.isFocused() && !queue.equals(views.queuePosition.getText().toString())) {
                views.queuePosition.setText(queue);
            }

            if (downloadLimited != views.downloadLimited.isChecked()) {
                views.downloadLimited.setChecked(downloadLimited);
            }

            String download = Long.toString(downloadLimit);
            if (!views.downloadLimit.isFocused() && !download.equals(views.downloadLimit.getText().toString())) {
                views.downloadLimit.setText(download);
            }
            if (views.downloadLimit.isEnabled() != views.downloadLimited.isChecked()) {
                views.downloadLimit.setEnabled(views.downloadLimited.isChecked());
            }

            if (views.uploadLimited.isChecked() != uploadLimited) {
                views.uploadLimited.setChecked(uploadLimited);
            }

            String upload = Long.toString(uploadLimit);
            if (!views.uploadLimit.isFocused() && !upload.equals(views.uploadLimit.getText().toString())) {
                views.uploadLimit.setText(upload);
            }
            if (views.uploadLimit.isEnabled() != views.uploadLimited.isChecked()) {
                views.uploadLimit.setEnabled(views.uploadLimited.isChecked());
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
            if (views.seedRatioMode.getSelectedItemPosition() != seedRatioModeValues.indexOf(mode)) {
                views.seedRatioMode.setSelection(seedRatioModeValues.indexOf(mode));
            }

            String ratio = G.readablePercent(seedRatioLimit);
            if (!views.seedRatioLimit.isFocused() && !ratio.equals(views.seedRatioLimit.getText().toString())) {
                views.seedRatioLimit.setText(ratio);
            }

            String peers = Integer.toString(peerLimit);
            if (!views.peerLimit.isFocused() && !peers.equals(views.peerLimit.getText().toString())) {
                views.peerLimit.setText(peers);
            }
        }

        /* Trackers start */
        if (views.trackersContent.getVisibility() != View.GONE
                && details.trackersCursor.getCount() > 0) {
            trackersAdapter.setNotifyOnChange(false);
            if (trackersAdapter.getCount() != details.trackersCursor.getCount()) {
                trackersAdapter.clear();

                int index = 0;
                ArrayList<Tracker> trackers = new ArrayList<>();

                details.trackersCursor.moveToFirst();

                while (!details.trackersCursor.isAfterLast()) {
                    Tracker tracker = new Tracker(index, details.trackersCursor);
                    trackers.add(tracker);

                    details.trackersCursor.moveToNext();
                    ++index;
                }
                Collections.sort(trackers, new TrackerComparator());
                trackersAdapter.addAll(trackers);
            }
            trackersAdapter.notifyDataSetChanged();
        }
    }

    private void invalidateFileActionMenu(Menu menu) {
        boolean hasChecked = false, hasUnchecked = false;
        MenuItem checked = menu.findItem(R.id.check_selected);
        MenuItem unchecked = menu.findItem(R.id.uncheck_selected);
        List<View> allViews = filesAdapter.getViews();

        for (View v : selectedFiles) {
            TorrentFile file = filesAdapter.getItem(allViews.indexOf(v));
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

            if (directory == null) {
                directory = "";
            }

            bytesCompleted = Torrent.File.getBytesCompleted(cursor);
            length = Torrent.File.getLength(cursor);
            priority = Torrent.File.getPriority(cursor);
            wanted = Torrent.File.isWanted(cursor);
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
        private static final int fieldId = R.id.torrent_detail_files_row;
        private List<View> views = new ArrayList<>();

        public FilesAdapter() {
            super(getActivity(), fieldId);
        }

        public List<View> getViews() {
            return views;
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
                TextView row = (TextView) rowView.findViewById(fieldId);
                if (TextUtils.isEmpty(file.directory)) {
                    row.setVisibility(View.GONE);
                } else {
                    row.setText(file.directory);
                }
            } else {
                final View container = rowView;
                CheckBox row = (CheckBox) rowView.findViewById(fieldId);
                if (initial) {
                    row.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (fileActionMode != null) {
                                buttonView.setChecked(!isChecked);
                                if (container.isActivated()) {
                                    container.setActivated(false);
                                    selectedFiles.remove(container);
                                    if (selectedFiles.size() == 0) {
                                        fileActionMode.finish();
                                    } else {
                                        invalidateFileActionMenu(fileActionMode.getMenu());
                                    }
                                } else {
                                    container.setActivated(true);
                                    selectedFiles.add(container);
                                    invalidateFileActionMenu(fileActionMode.getMenu());
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
                            if (fileActionMode != null) {
                                return false;
                            }
                            container.setActivated(true);
                            selectedFiles.add(container);
                            fileActionMode= getActivity().startActionMode(actionModeFiles);
                            invalidateFileActionMenu(fileActionMode.getMenu());
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
                    while (views.size() <= position)
                        views.add(null);
                    views.set(position, rowView);
                }
            }

            return rowView;
        }
    }

    private class FilesDataSetObserver extends DataSetObserver {
        public FilesDataSetObserver() { }

        @Override public void onChanged() {
            int position = details.filesCursor.getPosition();

            for (int i = 0; i < filesAdapter.getCount(); i++) {
                TorrentFile file = filesAdapter.getItem(i);
                View v = null;
                boolean hasChild = false;

                if (file.index != -1 && !details.filesCursor.moveToPosition(file.index)) {
                    continue;
                }

                if (i < views.filesContent.getChildCount()) {
                    v = views.filesContent.getChildAt(i);
                    hasChild = true;
                }
                if (!hasChild || (file.index != -1 && fileChanged(file, details.filesCursor))) {
                    v = filesAdapter.getView(i, v, null);
                    if (!hasChild && v != null) {
                        views.filesContent.addView(v, i);
                    }
                }
            }

            details.filesCursor.moveToPosition(position);
        }

        @Override public void onInvalidated() {
            views.filesContent.removeAllViews();
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
        private static final int fieldId = R.id.torrent_detail_trackers_row;
        private List<View> views = new ArrayList<>();

        public TrackersAdapter() {
            super(getActivity(), fieldId);
        }

        public List<View> getViews() {
            return views;
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
                while (views.size() <= position)
                    views.add(null);
                views.set(position, rowView);

                View row = rowView.findViewById(R.id.torrent_detail_trackers_row_info);
                final View buttons = rowView.findViewById(R.id.torrent_detail_tracker_buttons);

                row.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        if (trackerActionMode != null) {
                            return false;
                        }
                        v.setActivated(true);
                        selectedTrackers.add(v);
                        trackerActionMode = getActivity().startActionMode(actionModeTrackers);
                        hideAllButtons(null);

                        return true;
                    }
                });
                row.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        if (trackerActionMode != null) {
                            if (v.isActivated()) {
                                selectedTrackers.remove(v);
                                v.setActivated(false);
                                if (selectedTrackers.size() == 0) {
                                    trackerActionMode.finish();
                                }
                            } else {
                                selectedTrackers.add(v);
                                v.setActivated(true);
                            }

                            return;
                        }

                        final Map<View, int[]> coordinates = new HashMap<>();
                        for (View child : views) {
                            coordinates.put(child, new int[]{child.getTop(), child.getBottom()});
                        }

                        final ViewTreeObserver observer =
                            TorrentDetailPageFragment.this.views.trackersContent.getViewTreeObserver();

                        observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                            @Override public boolean onPreDraw() {
                                observer.removeOnPreDrawListener(this);

                                List<Animator> animations = new ArrayList<>();
                                final List<View> hiddenButtons = new ArrayList<>();

                                for (View child : views) {
                                    int[] oldCoordinates = coordinates.get(child);
                                    int top = child.getTop();
                                    int bottom = child.getBottom();

                                    animations.add(getCoordinateAnimator(
                                        child, oldCoordinates[0], oldCoordinates[1]));

                                    View buttons = child.findViewById(R.id.torrent_detail_tracker_buttons);
                                    if (bottom - top > oldCoordinates[1] - oldCoordinates[0]
                                            && buttons.getVisibility() == View.VISIBLE) {
                                        animations.add(ObjectAnimator.ofFloat(buttons, View.ALPHA, 0.3f, 1));
                                        animations.add(ObjectAnimator.ofFloat(buttons, View.TRANSLATION_Y, -50f, 0f));
                                    } else if (bottom - top < oldCoordinates[1] - oldCoordinates[0]
                                            && buttons.getVisibility() == View.GONE) {
                                        buttons.setVisibility(View.VISIBLE);
                                        animations.add(ObjectAnimator.ofFloat(buttons, View.ALPHA, 1, 0));
                                        animations.add(ObjectAnimator.ofFloat(buttons, View.TRANSLATION_Y, 0f, -50f));
                                        hiddenButtons.add(buttons);
                                    }
                                }

                                AnimatorSet set = new AnimatorSet();
                                set.playTogether(animations);
                                set.addListener(new AnimatorListenerAdapter() {
                                    @Override public void onAnimationEnd(Animator animation) {
                                        super.onAnimationEnd(animation);

                                        for (View v : hiddenButtons) {
                                            v.setAlpha(1f);
                                            v.setVisibility(View.GONE);
                                        }
                                    }
                                });

                                set.start();

                                return true;
                            }
                        });

                        if (buttons.getVisibility() == View.GONE) {
                            buttons.setVisibility(View.VISIBLE);
                        } else {
                            buttons.setVisibility(View.GONE);
                        }

                    }
                });

                final TransmissionSessionInterface context = (TransmissionSessionInterface) getActivity();
                final DataServiceManager manager =
                    ((DataServiceManagerInterface) getActivity()).getDataServiceManager();

                buttons.findViewById(R.id.torrent_detail_tracker_remove).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        trackersAdapter.remove(tracker);
                        ArrayList<Integer> ids = new ArrayList<>();
                        ids.add(tracker.id);

                        setTorrentProperty(Torrent.SetterFields.TRACKER_REMOVE, ids);
                        context.setRefreshing(true, DataService.Requests.SET_TORRENT);

                        if (manager != null) {
                            manager.update();
                        }

                        hideAllButtons(null);
                    }
                });

                buttons.findViewById(R.id.torrent_detail_tracker_replace).setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        final ArrayList<String> tuple = new ArrayList<>();
                        tuple.add(Integer.toString(tracker.id));

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

                                            context.setRefreshing(true, DataService.Requests.GET_TORRENTS);
                                            if (manager != null) {
                                                manager.update();
                                            }
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
            for (View v : views) {
                if (v != null) {
                    View buttons = v.findViewById(R.id.torrent_detail_tracker_buttons);
                    if (buttons != null && (ignore == null || ignore != buttons) && buttons.getVisibility() != View.GONE) {
                        buttons.setVisibility(View.GONE);
                    }
                }
            }
        }

        private Animator getCoordinateAnimator(View view, int oldTop, int oldBottom) {
            PropertyValuesHolder translationTop =
                PropertyValuesHolder.ofInt("top", oldTop, view.getTop());
            PropertyValuesHolder translationBottom =
                PropertyValuesHolder.ofInt("bottom", oldBottom, view.getBottom());

            return ObjectAnimator.ofPropertyValuesHolder(view, translationTop, translationBottom);
        }
    }

    private class TrackersDataSetObserver extends DataSetObserver {
        public TrackersDataSetObserver() { }

        @Override public void onChanged() {
            int position = details.trackersCursor.getPosition();

            for (int i = 0; i < trackersAdapter.getCount(); i++) {
                Tracker tracker = trackersAdapter.getItem(i);
                View v = null;
                boolean hasChild = false;

                if (!details.trackersCursor.moveToPosition(tracker.index)) {
                    continue;
                }

                if (i < views.trackersContainer.getChildCount()) {
                    v = views.trackersContainer.getChildAt(i);
                    hasChild = true;
                }
                if (!hasChild || (tracker.index != -1 && trackerChanged(tracker, details.trackersCursor))) {
                    v = trackersAdapter.getView(i, v, null);
                    if (!hasChild) {
                        views.trackersContainer.addView(v, i);
                    }
                }
            }

            int starting = trackersAdapter.getCount();
            List<View> trackerViews = trackersAdapter.getViews();
            while (trackerViews.size() > starting) {
                View v = trackerViews.get(starting);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)  {
                    animateRemoveView(v);
                } else {
                    views.trackersContainer.removeView(v);
                }

                trackerViews.remove(starting);
            }

            details.trackersCursor.moveToPosition(position);
        }

        @Override public void onInvalidated() {
            views.trackersContainer.removeAllViews();
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
                    views.trackersContainer.removeView(v);
                }
            });
        }
    }

    private class TorrentDetailTask extends AsyncTask<String, Void, TorrentDetails> {
        @Override protected TorrentDetails doInBackground(String... hashStrings) {
            if (!isCancelled() && getActivity() != null) {
                DataSource readSource = new DataSource(getActivity());

                readSource.open();
                try {
                    TorrentDetails details = readSource.getTorrentDetails(hashStrings[0]);
                    /* fill the windows */
                    details.torrentCursor.getCount();
                    details.filesCursor.getCount();
                    details.trackersCursor.getCount();

                    details.torrentCursor.moveToFirst();

                    return details;
                } finally {
                    readSource.close();
                }
            }

            return null;
        }

        @Override protected void onPostExecute(TorrentDetails details) {
            if (isResumed()) {
                if (TorrentDetailPageFragment.this.details != null) {
                    TorrentDetailPageFragment.this.details.torrentCursor.close();
                    TorrentDetailPageFragment.this.details.filesCursor.close();
                    TorrentDetailPageFragment.this.details.trackersCursor.close();

                    TorrentDetailPageFragment.this.details = null;
                }
                TorrentDetailPageFragment.this.details = details;
                updateFields(getView());
            }
        }
    }
}
