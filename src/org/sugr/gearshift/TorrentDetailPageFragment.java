package org.sugr.gearshift;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.Loader;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
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
                case R.id.torrent_detail_limits_expander:
                    image = v.findViewById(R.id.torrent_detail_limits_expander_image);
                    content = getView().findViewById(R.id.torrent_detail_limits_content);
                    break;
                case R.id.torrent_detail_advanced_expander:
                    image = v.findViewById(R.id.torrent_detail_advanced_expander_image);
                    content = getView().findViewById(R.id.torrent_detail_advanced_content);
                    break;
                default:
                    return;
            }

            if (content.getVisibility() == View.GONE) {
                content.setVisibility(View.VISIBLE);
                image.setBackgroundResource(R.drawable.ic_section_collapse);
            } else {
                content.setVisibility(View.GONE);
                image.setBackgroundResource(R.drawable.ic_section_expand);
            }

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
        root.findViewById(R.id.torrent_detail_limits_expander).setOnClickListener(mExpanderListener);
        root.findViewById(R.id.torrent_detail_advanced_expander).setOnClickListener(mExpanderListener);

        if (mTorrent == null) return root;

        updateFields(root);

        /* TODO: get loader, issue torrent-set commands */
        CheckBox check = (CheckBox) root.findViewById(R.id.torrent_global_limits);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mTorrent.areSessionLimitsHonored() != isChecked) {
                    setTorrent(Torrent.SetterFields.SESSION_LIMITS, isChecked);
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
                            setTorrent(Torrent.SetterFields.TORRENT_PRIORITY, priority);
                        }
                    }

                    @Override public void onNothingSelected(AdapterView<?> parent) { }
                });

        EditText edit = (EditText) root.findViewById(R.id.torrent_queue_position);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    int position;
                    try {
                        position = Integer.parseInt(v.getText().toString());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                    if (mTorrent.getQueuePosition() != position) {
                        setTorrent(Torrent.SetterFields.QUEUE_POSITION, position);
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
                    setTorrent(Torrent.SetterFields.DOWNLOAD_LIMITED, isChecked);
                }
            }
        });

        edit = (EditText) root.findViewById(R.id.torrent_limit_download);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                long limit;
                try {
                    limit = Long.parseLong(v.getText().toString());
                } catch (NumberFormatException e) {
                    return false;
                }
                if (mTorrent.getDownloadLimit() != limit) {
                    setTorrent(Torrent.SetterFields.DOWNLOAD_LIMIT, limit);
                }
                return false;
            }

        });

        check = (CheckBox) root.findViewById(R.id.torrent_limit_upload_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                root.findViewById(R.id.torrent_limit_upload).setEnabled(isChecked);
                if (mTorrent.isUploadLimited() != isChecked) {
                    setTorrent(Torrent.SetterFields.UPLOAD_LIMITED, isChecked);
                }
            }
        });

        edit = (EditText) root.findViewById(R.id.torrent_limit_upload);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                long limit;
                try {
                    limit = Long.parseLong(v.getText().toString());
                } catch (NumberFormatException e) {
                    return false;
                }
                if (mTorrent.getUploadLimit() != limit) {
                    setTorrent(Torrent.SetterFields.UPLOAD_LIMIT, limit);
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
                            setTorrent(Torrent.SetterFields.SEED_RATIO_MODE, mode);
                        }
                    }

                    @Override public void onNothingSelected(AdapterView<?> parent) { }
                });

        edit = (EditText) root.findViewById(R.id.torrent_seed_ratio_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                float limit;
                try {
                    limit = Float.parseFloat(v.getText().toString());
                } catch (NumberFormatException e) {
                    return false;
                }
                if (mTorrent.getSeedRatioLimit() != limit) {
                    setTorrent(Torrent.SetterFields.SEED_RATIO_LIMIT, limit);
                }
                return false;
            }

        });

        edit = (EditText) root.findViewById(R.id.torrent_peer_limit);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                int limit;
                try {
                    limit = Integer.parseInt(v.getText().toString());
                } catch (NumberFormatException e) {
                    return false;
                }
                if (mTorrent.getPeerLimit() != limit) {
                    setTorrent(Torrent.SetterFields.PEER_LIMIT, limit);
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

    private void setTorrent(String key, boolean value) {
        TransmissionSessionLoader loader = getLoader();
        loader.setTorrent(mTorrent.getId(), key, value);
    }

    private void setTorrent(String key, int value) {
        TransmissionSessionLoader loader = getLoader();
        loader.setTorrent(mTorrent.getId(), key, value);
    }

    private void setTorrent(String key, long value) {
        TransmissionSessionLoader loader = getLoader();
        loader.setTorrent(mTorrent.getId(), key, value);
    }

    private void setTorrent(String key, float value) {
        TransmissionSessionLoader loader = getLoader();
        loader.setTorrent(mTorrent.getId(), key, value);
    }

    private TransmissionSessionLoader getLoader() {
        Loader<TransmissionSessionData> loader = getActivity().getSupportLoaderManager()
                .getLoader(TorrentListActivity.SESSION_LOADER_ID);

        return (TransmissionSessionLoader) loader;
    }

    private void updateFields(View root) {
        if (root == null) return;

        ((TextView) root.findViewById(R.id.torrent_detail_title)).setText(mTorrent.getName());

        /* Overview start */
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

        /* Limits start */
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
