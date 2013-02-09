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

public class TorrentDetailFragment extends Fragment {
    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TorrentDetailFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.torrent_detail_fragment, menu);

        /* FIXME: Set these states depending on the torrent state */
        MenuItem item = menu.findItem(R.id.resume);
        item.setVisible(false).setEnabled(false);

        item = menu.findItem(R.id.pause);
        item.setVisible(true).setEnabled(true);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.remove:
                return true;
            case R.id.delete:
                return true;
            case R.id.resume:
                return true;
            case R.id.pause:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
