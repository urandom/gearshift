package org.sugr.gearshift;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
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
    
    private NumberPickerDialogFragment.OnDialogListener mDialogListener
            = new NumberPickerDialogFragment.OnDialogListener() {
        @Override
        public void onOkClick(NumberPickerDialogFragment dialog) {
            View root = getView();
            TextView entry = (TextView) root.findViewById(dialog.getParentId());
            
            if (entry != null) {
                long val = dialog.getValue();
                if (val == 0)
                    entry.setText("");
                else
                    entry.setText(String.format("%d", val));
            }

            switch(dialog.getParentId()) {
                default:
                    return;
            }
        }
        @Override
        public void onCancelClick(NumberPickerDialogFragment dialog) {
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

        if (getArguments().containsKey(TorrentDetailActivity.ARG_TORRENT_ID)) {
            int id = getArguments().getInt(TorrentDetailActivity.ARG_TORRENT_ID);
            ArrayList<Torrent> torrents = ((TransmissionSessionInterface) getActivity()).getTorrents();
            for (Torrent t : torrents) {
                if (t.getId() == id) {
                    mTorrent = t;
                    break;
                }
            }
            if (mTorrent == null) {
                throw new IllegalStateException("A valid torrent was not found.");
            }
        }

        mPriorityValues = Arrays.asList(getResources().getStringArray(R.array.torrent_priority_values));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_torrent_detail, container, false);
        
        if (mTorrent != null) {
            ((TextView) root.findViewById(R.id.torrent_detail_title)).setText(mTorrent.getName());
        }

        
        root.findViewById(R.id.torrent_detail_overview_expander).setOnClickListener(mExpanderListener);
        root.findViewById(R.id.torrent_detail_limits_expander).setOnClickListener(mExpanderListener);
        root.findViewById(R.id.torrent_detail_advanced_expander).setOnClickListener(mExpanderListener);

        /* TODO: actually use whatever torrent priority is set */
        ((Spinner) root.findViewById(R.id.torrent_priority)).setSelection(mPriorityValues.indexOf("normal"));
        
        View.OnClickListener numberListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                NumberPickerDialogFragment dialog = new NumberPickerDialogFragment();
                String val = ((TextView) v).getText().toString();
                
                if (val == "")
                    val = "0";
                
                /* TODO: use the torrent queue position */
                dialog.setValue(Integer.parseInt(val))
                    .setParentId(v.getId()).setListener(mDialogListener);
                dialog.show(TorrentDetailPageFragment.this.getActivity().getSupportFragmentManager(),
                        "NumberPickerDialogFragment");
            }
        };
        root.findViewById(R.id.torrent_queue_position).setOnClickListener(numberListener);
        root.findViewById(R.id.torrent_limit_download).setOnClickListener(numberListener);
        root.findViewById(R.id.torrent_limit_upload).setOnClickListener(numberListener);
        root.findViewById(R.id.torrent_seed_ratio_limit).setOnClickListener(numberListener);
        root.findViewById(R.id.torrent_peer_limit).setOnClickListener(numberListener);
        
        CheckBox check = (CheckBox) root.findViewById(R.id.torrent_limit_download_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {   
                root.findViewById(R.id.torrent_limit_download).setEnabled(isChecked);
            }
        });
        root.findViewById(R.id.torrent_limit_download).setEnabled(check.isChecked());
        
        check = (CheckBox) root.findViewById(R.id.torrent_limit_upload_check);
        check.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {   
                root.findViewById(R.id.torrent_limit_upload).setEnabled(isChecked);
            }
        });
        root.findViewById(R.id.torrent_limit_upload).setEnabled(check.isChecked());
        
        ((Spinner) root.findViewById(R.id.torrent_seed_ratio_mode)).setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                        String[] values = getResources().getStringArray(R.array.torrent_seed_ratio_mode_values);
                        
                        root.findViewById(R.id.torrent_seed_ratio_limit).setEnabled(values[pos].equals("user"));
                    }
                    
                    public void onNothingSelected(AdapterView<?> parent) {
                        
                    }
                });
        
        /* TODO: use the torrent global limits override */
        check = (CheckBox) root.findViewById(R.id.torrent_global_limits); 
        check.setChecked(true);

                
        return root;
    }
}
