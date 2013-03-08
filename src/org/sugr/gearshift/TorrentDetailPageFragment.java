package org.sugr.gearshift;

import java.io.File;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import android.content.Context;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.Loader;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

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

    private View.OnClickListener mExpanderListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            View image;
            View content;
            switch(v.getId()) {
                case R.id.torrent_detail_overview_expander:
                    image = v.findViewById(R.id.torrent_detail_overview_expander_image);
                    content = getView().findViewById(R.id.torrent_detail_overview_content);
                    break;
                case R.id.torrent_detail_files_expander:
                    image = v.findViewById(R.id.torrent_detail_files_expander_image);
                    content = getView().findViewById(R.id.torrent_detail_files_content);
                    break;
                case R.id.torrent_detail_limits_expander:
                    image = v.findViewById(R.id.torrent_detail_limits_expander_image);
                    content = getView().findViewById(R.id.torrent_detail_limits_content);
                    break;
                default:
                    return;
            }

            if (content.getVisibility() == View.GONE) {
                content.setVisibility(View.VISIBLE);
                image.setBackgroundResource(R.drawable.ic_section_collapse);
                updateFields(getView());
            } else {
                content.setVisibility(View.GONE);
                image.setBackgroundResource(R.drawable.ic_section_expand);
            }

        }
    };

    private FilesAdapter mFilesAdapter;
    private FilesDataSetObserver mFilesObserver;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TorrentDetailPageFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(TorrentDetailFragment.ARG_PAGE_POSITION)) {
            int position = getArguments().getInt(TorrentDetailFragment.ARG_PAGE_POSITION);
            ArrayList<Torrent> torrents = ((TransmissionSessionInterface) getActivity()).getTorrents();

            if (position < torrents.size())
                mTorrent = torrents.get(position);
        }

        mPriorityValues = Arrays.asList(getResources().getStringArray(R.array.torrent_priority_values));
        mSeedRatioModeValues = Arrays.asList(getResources().getStringArray(R.array.torrent_seed_ratio_mode_values));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_torrent_detail_page, container, false);

        root.findViewById(R.id.torrent_detail_overview_expander).setOnClickListener(mExpanderListener);
        root.findViewById(R.id.torrent_detail_files_expander).setOnClickListener(mExpanderListener);
        root.findViewById(R.id.torrent_detail_limits_expander).setOnClickListener(mExpanderListener);

        if (mTorrent == null) return root;

        mFilesAdapter = new FilesAdapter();
        mFilesObserver = new FilesDataSetObserver(root);
        mFilesAdapter.registerDataSetObserver(mFilesObserver);

        updateFields(root);

        CheckBox check = (CheckBox) root.findViewById(R.id.torrent_global_limits);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mTorrent.areSessionLimitsHonored() != isChecked) {
                    setTorrentProperty(Torrent.SetterFields.SESSION_LIMITS, new Boolean(isChecked));
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
                            setTorrentProperty(Torrent.SetterFields.TORRENT_PRIORITY, new Integer(priority));
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
                        position = Integer.parseInt(v.getText().toString());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mTorrent.getQueuePosition() != position) {
                        setTorrentProperty(Torrent.SetterFields.QUEUE_POSITION, new Integer(position));
                    }
                }
                return false;
            }
        });

        check = (CheckBox) root.findViewById(R.id.torrent_limit_download_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                root.findViewById(R.id.torrent_limit_download).setEnabled(isChecked);
                if (mTorrent.isDownloadLimited() != isChecked) {
                    setTorrentProperty(Torrent.SetterFields.DOWNLOAD_LIMITED, new Boolean(isChecked));
                }
            }
        });

        final EditText download = (EditText) root.findViewById(R.id.torrent_limit_download);
        download.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                long limit;
                try {
                    limit = Long.parseLong(v.getText().toString());
                } catch (NumberFormatException e) {
                    return false;
                }
                if (mTorrent.getDownloadLimit() != limit) {
                    setTorrentProperty(Torrent.SetterFields.DOWNLOAD_LIMIT, new Long(limit));
                }
                return false;
            }

        });

        check = (CheckBox) root.findViewById(R.id.torrent_limit_upload_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                root.findViewById(R.id.torrent_limit_upload).setEnabled(isChecked);
                if (mTorrent.isUploadLimited() != isChecked) {
                    setTorrentProperty(Torrent.SetterFields.UPLOAD_LIMITED, new Boolean(isChecked));
                }
            }
        });

        final EditText upload = (EditText) root.findViewById(R.id.torrent_limit_upload);
        upload.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                long limit;
                try {
                    limit = Long.parseLong(v.getText().toString());
                } catch (NumberFormatException e) {
                    return false;
                }
                if (mTorrent.getUploadLimit() != limit) {
                    setTorrentProperty(Torrent.SetterFields.UPLOAD_LIMIT, new Long(limit));
                }
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
                            setTorrentProperty(Torrent.SetterFields.SEED_RATIO_MODE, new Integer(mode));
                        }
                    }

                    @Override public void onNothingSelected(AdapterView<?> parent) { }
                });

        final EditText ratio = (EditText) root.findViewById(R.id.torrent_seed_ratio_limit);
        ratio.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                float limit;
                try {
                    limit = Float.parseFloat(v.getText().toString());
                } catch (NumberFormatException e) {
                    return false;
                }
                if (mTorrent.getSeedRatioLimit() != limit) {
                    setTorrentProperty(Torrent.SetterFields.SEED_RATIO_LIMIT, new Float(limit));
                }
                return false;
            }

        });

        final EditText peer = (EditText) root.findViewById(R.id.torrent_peer_limit);
        peer.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                int limit;
                try {
                    limit = Integer.parseInt(v.getText().toString());
                } catch (NumberFormatException e) {
                    return false;
                }
                if (mTorrent.getPeerLimit() != limit) {
                    setTorrentProperty(Torrent.SetterFields.PEER_LIMIT, new Integer(limit));
                }
                return false;
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

    public void notifyTorrentUpdate(Torrent torrent) {
        if (torrent.getId() != mTorrent.getId()) {
            return;
        }

        mTorrent = torrent;
        updateFields(getView());
    }

    private void setTorrentProperty(String key, Object value) {
        TransmissionSessionLoader loader = getLoader();
        loader.setTorrentProperty(mTorrent.getId(), key, value);
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
                        Torrent.readableFileSize(mTorrent.getHaveValid() > 0
                                ? mTorrent.getHaveValid() : mTorrent.getSizeWhenDone() - mTorrent.getLeftUntilDone()),
                        Torrent.readableFileSize(mTorrent.getSizeWhenDone()),
                        Torrent.readablePercent(100 * (
                            mTorrent.getSizeWhenDone() > 0
                                ? (mTorrent.getSizeWhenDone() - mTorrent.getLeftUntilDone()) / mTorrent.getSizeWhenDone()
                                : 1
                        ))
                    ));
                ((TextView) root.findViewById(R.id.torrent_downloaded)).setText(
                    mTorrent.getDownloadedEver() == 0
                        ? getString(R.string.unknown)
                        : Torrent.readableFileSize(mTorrent.getDownloadedEver())
                );
                ((TextView) root.findViewById(R.id.torrent_uploaded)).setText(
                    Torrent.readableFileSize(mTorrent.getUploadedEver())
                );
                int state = R.string.none;
                switch(mTorrent.getStatus()) {
                    case Torrent.Status.STOPPED:
                        state = R.string.status_stopped;
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
                        ? getString(R.string.status_stopped)
                        : mTorrent.getStartDate() > 0
                            ? Torrent.readableRemainingTime(now - mTorrent.getStartDate(), getActivity())
                            : getString(R.string.unknown)
                );
                ((TextView) root.findViewById(R.id.torrent_remaining_time)).setText(
                    mTorrent.getEta() < 0
                        ? getString(R.string.unknown)
                        : Torrent.readableRemainingTime(mTorrent.getEta(), getActivity())
                );
                long lastActive = now - mTorrent.getActivityDate();
                ((TextView) root.findViewById(R.id.torrent_last_activity)).setText(
                    lastActive < 0 || mTorrent.getActivityDate() <= 0
                        ? getString(R.string.unknown)
                        : lastActive < 5
                            ? getString(R.string.torrent_active_now)
                            : Torrent.readableRemainingTime(lastActive, getActivity())
                );
                if (mTorrent.getError() == Torrent.Error.OK) {
                    ((TextView) root.findViewById(R.id.torrent_error)).setText(
                            R.string.no_tracker_errors);
                } else {
                    ((TextView) root.findViewById(R.id.torrent_error)).setText(mTorrent.getErrorString());
                }
                ((TextView) root.findViewById(R.id.torrent_size)).setText(
                        String.format(
                            getString(R.string.torrent_size_format),
                            Torrent.readableFileSize(mTorrent.getTotalSize()),
                            mTorrent.getPieceCount(),
                            Torrent.readableFileSize(mTorrent.getPieceSize())
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
            }
        }

        /* Files start */
        if (root.findViewById(R.id.torrent_detail_files_content).getVisibility() != View.GONE
                && mTorrent.getFiles() != null && mTorrent.getFileStats() != null) {

            mFilesAdapter.setNotifyOnChange(false);
            if (mFilesAdapter.getCount() == 0) {
                Torrent.File[] files = mTorrent.getFiles();
                Torrent.FileStat[] stats = mTorrent.getFileStats();

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

            String ratio = Torrent.readablePercent(mTorrent.getSeedRatioLimit());
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
    }

    private class TorrentFile {
        int index = -1;
        Torrent.File info;
        Torrent.FileStat stat;

        String directory;
        String name;

        public TorrentFile(int index, Torrent.File info, Torrent.FileStat stat) {
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

        public void setStat(Torrent.FileStat stat) {
            this.stat = stat;
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

        public FilesAdapter() {
            super(getActivity(), mFieldId);
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

                row.setText(file.directory);
            } else {
                CheckBox row = (CheckBox) rowView.findViewById(mFieldId);
                if (initial) {
                    row.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            if (file.stat.isWanted() != isChecked) {
                                if (isChecked) {
                                    setTorrentProperty(Torrent.SetterFields.FILES_WANTED, new Integer(file.index));
                                } else {
                                    setTorrentProperty(Torrent.SetterFields.FILES_UNWANTED, new Integer(file.index));
                                }
                            }
                        }
                    });
                    row.setText(file.name);
                }
                row.setChecked(file.stat.isWanted());
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
            Torrent.FileStat[] stats = mTorrent.getFileStats();
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

        private boolean fileChanged(TorrentFile file, Torrent.File tFile, Torrent.FileStat stat) {
            boolean changed = false;

            if (file.stat.isWanted() != stat.isWanted()
                    || file.stat.getBytesCompleted() != stat.getBytesCompleted()
                    || file.stat.getPriority() != stat.getPriority()) {
                file.setStat(stat);
                changed = true;
            }

            return changed;
        }
    }
}
