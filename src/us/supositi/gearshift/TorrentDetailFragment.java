package us.supositi.gearshift;

import java.util.Arrays;
import java.util.List;

import us.supositi.gearshift.dummy.DummyContent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * A fragment representing a single Torrent detail screen.
 * This fragment is either contained in a {@link TorrentListActivity}
 * in two-pane mode (on tablets) or a {@link TorrentDetailActivity}
 * on handsets.
 */
public class TorrentDetailFragment extends Fragment {
    /**
     * The dummy content this fragment is presenting.
     */
    private DummyContent.DummyItem mItem;
    
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

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TorrentDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments().containsKey(TorrentDetailActivity.ARG_ITEM_ID)) {
            // Load the dummy content specified by the fragment
            // arguments. In a real-world scenario, use a Loader
            // to load content from a content provider.
            mItem = DummyContent.ITEM_MAP.get(getArguments().getString(TorrentDetailActivity.ARG_ITEM_ID));
        }
        
        mPriorityValues = Arrays.asList(getResources().getStringArray(R.array.torrent_priority_values));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_torrent_detail, container, false);
        
        if (mItem != null) {
            ((TextView) root.findViewById(R.id.torrent_detail_title)).setText(mItem.content);
        }

        
        root.findViewById(R.id.torrent_detail_overview_expander).setOnClickListener(mExpanderListener);
        root.findViewById(R.id.torrent_detail_limits_expander).setOnClickListener(mExpanderListener);
        root.findViewById(R.id.torrent_detail_advanced_expander).setOnClickListener(mExpanderListener);

        /* TODO: actually use whatever torrent priority is set */
        ((Spinner) root.findViewById(R.id.torrent_priority)).setSelection(mPriorityValues.indexOf("normal"));
                
        return root;
    }
}
