package org.sugr.gearshift;


import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class TorrentListMenuFragment extends Fragment {
    private ListView mFilterList;
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        
        Context context = new ContextThemeWrapper(getActivity(), android.R.style.Theme_Holo);
        LayoutInflater localInflater = inflater.cloneInContext(context);
        
        View root = localInflater.inflate(R.layout.fragment_torrent_list_menu, container, false);

        mFilterList = (ListView) root.findViewById(R.id.filter_list);
        
        View header = localInflater.inflate(R.layout.menu_list_header, mFilterList, false);
        mFilterList.addHeaderView(header);
        
        View footer = localInflater.inflate(R.layout.menu_list_footer, mFilterList, false);
        mFilterList.addFooterView(footer);

        /* TODO: The list items should have a count that indicates
         *  how many torrents are matched by the filter */
        mFilterList.setAdapter(new ArrayAdapter<String>(context,
                android.R.layout.simple_list_item_1,
                android.R.id.text1,
                getResources().getStringArray(R.array.filter_list_entries)));

        return root;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        
        setStatus(null, null);
    }
    
    private void setStatus(Object[] speed_args, Object[] space_args) {
        TextView speed = (TextView) getView().findViewById(R.id.status_speed);
        TextView space = (TextView) getView().findViewById(R.id.status_free_space);
        
        if (speed_args == null)
            speed_args = new Object[] {0, 0, "kb/s", 0, 0, "kb/s"};
        
        speed.setText(String.format(getString(R.string.speed_format), speed_args));
        
        if (space_args == null)
            space_args = new Object[] {"0MB"};
        
        space.setText(String.format(getString(R.string.free_space_format), space_args));
    }
}
