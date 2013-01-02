package us.supositi.gearshift;

import us.supositi.gearshift.dummy.DummyContent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class TorrentDetailOverviewFragment extends Fragment {
    private DummyContent.DummyItem mItem;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mItem = ((TorrentDetailFragment) getParentFragment()).getItem();
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_torrent_detail_overview, container, false);

        // Show the dummy content as text in a TextView.
        if (mItem != null) {
            ((TextView) root.findViewById(R.id.torrent_detail_title)).setText(mItem.content);
        }
        return root;
    }
}
