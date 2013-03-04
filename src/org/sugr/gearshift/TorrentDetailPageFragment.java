package org.sugr.gearshift;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

        /* TODO: get loader, issue torrent-set commands */
        CheckBox check = (CheckBox) root.findViewById(R.id.torrent_global_limits);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
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
                    }

                    @Override public void onNothingSelected(AdapterView<?> parent) { }
                });

        check = (CheckBox) root.findViewById(R.id.torrent_limit_download_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                root.findViewById(R.id.torrent_limit_download).setEnabled(isChecked);
            }
        });

        ((EditText) root.findViewById(R.id.torrent_limit_download)).addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                long limit;
                try {
                    limit = Long.parseLong(s.toString());
                } catch (NumberFormatException e) {
                    return;
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        check = (CheckBox) root.findViewById(R.id.torrent_limit_upload_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                root.findViewById(R.id.torrent_limit_upload).setEnabled(isChecked);
            }
        });

        ((EditText) root.findViewById(R.id.torrent_limit_upload)).addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                long limit;
                try {
                    limit = Long.parseLong(s.toString());
                } catch (NumberFormatException e) {
                    return;
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
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
                    }

                    @Override public void onNothingSelected(AdapterView<?> parent) { }
                });

        ((EditText) root.findViewById(R.id.torrent_seed_ratio_limit)).addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                float limit;
                try {
                    limit = Float.parseFloat(s.toString());
                } catch (NumberFormatException e) {
                    return;
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        ((EditText) root.findViewById(R.id.torrent_peer_limit)).addTextChangedListener(new TextWatcher() {
            @Override public void afterTextChanged(Editable s) {
                int limit;
                try {
                    limit = Integer.parseInt(s.toString());
                } catch (NumberFormatException e) {
                    return;
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
        });

        if (mTorrent == null) return root;

        updateFields(root);

        return root;
    }

    public void notifyTorrentUpdate(Torrent torrent) {
        if (torrent.getId() != mTorrent.getId()) {
            return;
        }

        mTorrent = torrent;
        updateFields(getView());
    }

    private void updateFields(View root) {
        if (root == null) return;

        ((TextView) root.findViewById(R.id.torrent_detail_title)).setText(mTorrent.getName());

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

        CheckBox check = (CheckBox) root.findViewById(R.id.torrent_global_limits);
        check.setChecked(mTorrent.isHonorsSessionLimits());

        String priority = "normal";
        switch (mTorrent.getBandwidthPriority()) {
            case Torrent.Priority.LOW:
                priority = "low";
                break;
            case Torrent.Priority.HIGH:
                priority = "high";
                break;
        }
        ((Spinner) root.findViewById(R.id.torrent_priority)).setSelection(mPriorityValues.indexOf(priority));

        ((EditText) root.findViewById(R.id.torrent_queue_position)).setText(mTorrent.getQueuePosition());

        check = (CheckBox) root.findViewById(R.id.torrent_limit_download_check);
        check.setChecked(mTorrent.isDownloadLimited());

        EditText limit = (EditText) root.findViewById(R.id.torrent_limit_download);
        limit.setText(Long.toString(mTorrent.getDownloadLimit()));
        limit.setEnabled(check.isChecked());

        check = (CheckBox) root.findViewById(R.id.torrent_limit_upload_check);
        check.setChecked(mTorrent.isUploadLimited());

        limit = (EditText) root.findViewById(R.id.torrent_limit_upload);
        limit.setText(Long.toString(mTorrent.getUploadLimit()));
        limit.setEnabled(check.isChecked());

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
        limit = (EditText) root.findViewById(R.id.torrent_seed_ratio_limit);
        limit.setText(Torrent.readablePercent(mTorrent.getSeedRatioLimit()));

        limit = (EditText) root.findViewById(R.id.torrent_peer_limit);
        limit.setText(mTorrent.getPeerLimit());
    }
}
