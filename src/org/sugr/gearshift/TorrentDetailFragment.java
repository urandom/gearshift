package org.sugr.gearshift;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.Spinner;

import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.datasource.TorrentNameStatus;

import java.util.HashMap;

public class TorrentDetailFragment extends Fragment implements TorrentListNotification {
    public static final String ARG_SHOW_PAGER = "show_pager";

    public interface PagerCallbacks {
        public void onPageSelected(int position);
    }

    private PagerCallbacks pagerCallbacks = dummyCallbacks;
    private ViewPager pager;
    private String currentTorrentHashString;
    private int currentTorrentPosition = 0;
    private int currentTorrentStatus;
    private String currentTorrentName;

    private boolean showPager = false;

    private static final String STATE_LOCATION_POSITION = "location_position";
    private static final String STATE_ACTION_MOVE_HASH_STRINGS= "action_move_hash_strings";

    private int locationPosition = AdapterView.INVALID_POSITION;

    private String[] actionMoveHashStrings;

    private Menu menu;

    private String[] torrentHashStrings;
    private HashMap<String, Integer> torrentPositionMap = new HashMap<String, Integer>();

    private static PagerCallbacks dummyCallbacks = new PagerCallbacks() {
        @Override
        public void onPageSelected(int position) { }
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

        if (getArguments().containsKey(G.ARG_PAGE_POSITION)) {
            currentTorrentPosition = getArguments().getInt(G.ARG_PAGE_POSITION);
            if (currentTorrentPosition < 0) {
                currentTorrentPosition = 0;
            }
            setHasOptionsMenu(true);
        }

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(STATE_LOCATION_POSITION)) {
                locationPosition = savedInstanceState.getInt(STATE_LOCATION_POSITION);
            }
            if (savedInstanceState.containsKey(STATE_ACTION_MOVE_HASH_STRINGS)) {
                actionMoveHashStrings = savedInstanceState.getStringArray(STATE_ACTION_MOVE_HASH_STRINGS);
                if (actionMoveHashStrings != null) {
                    showMoveDialog(actionMoveHashStrings);
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View root = inflater.inflate(R.layout.fragment_torrent_detail, container, false);

        pager = (ViewPager) root.findViewById(R.id.torrent_detail_pager);
        pager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                if (currentTorrentPosition != -1) {
                    Intent intent=new Intent(G.INTENT_PAGE_UNSELECTED);
                    intent.putExtra(G.ARG_TORRENT_INDEX, currentTorrentPosition);

                    getActivity().sendBroadcast(intent);
                }

                currentTorrentPosition = position;
                setCurrentTorrentHashString(position);

                pagerCallbacks.onPageSelected(position);
                new QueryCurrentDataTask().execute(currentTorrentHashString);
            }
        });

        if (getArguments().containsKey(ARG_SHOW_PAGER)) {
            if (getArguments().getBoolean(ARG_SHOW_PAGER)) {
                showPager = true;
            }
        }

        return root;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Activities containing this fragment must implement its callbacks.
        if (!(activity instanceof PagerCallbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        pagerCallbacks = (PagerCallbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // Reset the active callbacks interface to the dummy implementation.
        pagerCallbacks = dummyCallbacks;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        this.menu = menu;

        inflater.inflate(R.menu.torrent_detail_fragment, menu);

        setMenuTorrentState();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final Loader<TransmissionData> loader = getActivity().getSupportLoaderManager()
            .getLoader(G.TORRENTS_LOADER_ID);

        final TransmissionSessionInterface context = ((TransmissionSessionInterface) getActivity());

        if (loader == null || torrentHashStrings == null || torrentHashStrings.length <= currentTorrentPosition) {
            return super.onOptionsItemSelected(item);
        }

        final String[] hashStrings = new String[] { currentTorrentHashString };

        switch (item.getItemId()) {
            case R.id.remove:
            case R.id.delete:
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.no, null);

                builder.setPositiveButton(android.R.string.yes,
                    new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        ((TransmissionDataLoader) loader).setTorrentsRemove(hashStrings, item.getItemId() == R.id.delete);
                        context.setRefreshing(true);
                    }
                })
                    .setMessage(String.format(getString(
                                    item.getItemId() == R.id.delete
                            ? R.string.delete_current_confirmation
                            : R.string.remove_current_confirmation),
                                currentTorrentName))
                .show();
                return true;
            case R.id.resume:
                String action;
                switch(currentTorrentStatus) {
                    case Torrent.Status.DOWNLOAD_WAITING:
                    case Torrent.Status.SEED_WAITING:
                        action = "torrent-start-now";
                        break;
                    default:
                        action = "torrent-start";
                        break;
                }
                ((TransmissionDataLoader) loader).setTorrentsAction(action, hashStrings);
                break;
            case R.id.pause:
                ((TransmissionDataLoader) loader).setTorrentsAction("torrent-stop", hashStrings);
                break;
            case R.id.move:
                actionMoveHashStrings = hashStrings;
                return showMoveDialog(hashStrings);
            case R.id.verify:
                ((TransmissionDataLoader) loader).setTorrentsAction("torrent-verify", hashStrings);
                break;
            case R.id.reannounce:
                ((TransmissionDataLoader) loader).setTorrentsAction("torrent-reannounce", hashStrings);
                break;
            default:
                return false;
        }

        context.setRefreshing(true);

        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_LOCATION_POSITION, locationPosition);
        outState.putStringArray(STATE_ACTION_MOVE_HASH_STRINGS, actionMoveHashStrings);
    }

    public void changeCursor(Cursor newCursor) {
        updateTorrentData(newCursor);
        if (pager.getAdapter() == null) {
            setCurrentTorrentHashString(currentTorrentPosition);

            resetPagerAdapter();
            setMenuTorrentState();
        }
    }
    public int getTorrentPositionInCursor(String hash) {
        Integer position = torrentPositionMap.get(hash);

        return position == null ? -1 : position;
    }

    public String getTorrentHashString(int position) {
        return torrentHashStrings != null && torrentHashStrings.length > position ? torrentHashStrings[position] : null;
    }

    public void removeMenuEntries() {
        if (menu == null) {
            return;
        }

        menu.removeItem(R.id.resume);
        menu.removeItem(R.id.pause);
        menu.removeItem(R.id.remove);
        menu.removeItem(R.id.delete);
        menu.removeItem(R.id.move);
        menu.removeItem(R.id.verify);
        menu.removeItem(R.id.reannounce);
    }

    public void setCurrentTorrent(int position) {
        if (pager.getAdapter() == null) {
            currentTorrentPosition = position;
            return;
        }
        if (position == pager.getCurrentItem()) {
            if (position != currentTorrentPosition) {
                currentTorrentPosition = position;
                setCurrentTorrentHashString(position);
                new QueryCurrentDataTask().execute(currentTorrentHashString);
            }
        } else {
            pager.setCurrentItem(position);
        }
    }
    private void updateTorrentData(Cursor cursor) {
        int cursorPosition = cursor.getPosition();
        int position = -1;

        torrentHashStrings = new String[cursor.getCount()];
        torrentPositionMap.clear();
        cursor.moveToFirst();

        while (!cursor.isAfterLast()) {
            String hash = Torrent.getHashString(cursor);
            torrentHashStrings[++position] = hash;
            torrentPositionMap.put(hash, position);
            if (currentTorrentHashString == null && position == currentTorrentPosition
                || hash.equals(currentTorrentHashString)) {
                currentTorrentStatus = Torrent.getStatus(cursor);
                currentTorrentName = Torrent.getName(cursor);
            }

            cursor.moveToNext();
        }

        cursor.moveToPosition(cursorPosition);
    }

    public void resetPagerAdapter() {
        if (torrentHashStrings == null) {
            pager.setAdapter(null);
        } else {
            pager.setAdapter(new TorrentDetailPagerAdapter(getActivity(), torrentHashStrings.length));
            pager.setCurrentItem(currentTorrentPosition);

            if (showPager) {
                showPager = false;
                pager.setVisibility(View.VISIBLE);
                pager.animate().alpha((float) 1.0);
            }
        }
    }

    public void notifyTorrentListChanged(Cursor cursor, int error, boolean added, boolean removed,
                                         boolean status, boolean metadata) {
        if (((TransmissionSessionInterface) getActivity()).getSession() == null || cursor.isClosed()) {
            setMenuTorrentState();
            return;
        }
        boolean updateMenu = status;

        changeCursor(cursor);

        if (removed || added) {
            int position = getTorrentPositionInCursor(currentTorrentHashString);

            resetPagerAdapter();
            if (position != -1) {
                pager.setCurrentItem(position);
            } else {
                updateMenu = true;
            }
            pagerCallbacks.onPageSelected(pager.getCurrentItem());
        }

        if (updateMenu) {
            setMenuTorrentState();
        }

        if (currentTorrentPosition != -1) {
            int limit = pager.getOffscreenPageLimit();
            int startPosition = currentTorrentPosition - limit;
            if (startPosition < 0) {
                startPosition = 0;
            }
            for (int i = startPosition; i < currentTorrentPosition + limit + 1; ++i) {
                Intent intent = new Intent(G.INTENT_TORRENT_UPDATE);
                intent.putExtra(G.ARG_TORRENT_INDEX, i);

                getActivity().sendBroadcast(intent);
            }

        }
    }

    private void setCurrentTorrentHashString(int position) {
        currentTorrentHashString = getTorrentHashString(position);
    }

    private void setMenuTorrentState() {
        if (menu == null || getActivity() == null || menu.findItem(R.id.remove) == null) {
            return;
        }
        boolean visible = ((TransmissionSessionInterface) getActivity()).getSession() != null;
        menu.findItem(R.id.remove).setVisible(visible);
        menu.findItem(R.id.delete).setVisible(visible);
        menu.findItem(R.id.move).setVisible(visible);
        menu.findItem(R.id.verify).setVisible(visible);
        menu.findItem(R.id.reannounce).setVisible(visible);

        boolean isActive = Torrent.isActive(currentTorrentStatus);

        boolean resumeState = false;
        boolean pauseState = false;
        if (currentTorrentHashString != null) {
            if (isActive) {
                resumeState = false;
                pauseState = true;
            } else {
                resumeState = true;
                pauseState = false;
            }
        }

        MenuItem item = menu.findItem(R.id.resume);
        item.setVisible(resumeState).setEnabled(resumeState);

        item = menu.findItem(R.id.pause);
        item.setVisible(pauseState).setEnabled(pauseState);
    }

    private boolean showMoveDialog(final String[] hashStrings) {
        LayoutInflater inflater = getActivity().getLayoutInflater();
        final TransmissionSessionInterface context = ((TransmissionSessionInterface) getActivity());
        final Loader<TransmissionData> loader = getActivity().getSupportLoaderManager()
            .getLoader(G.TORRENTS_LOADER_ID);

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
            .setTitle(R.string.set_location)
            .setCancelable(false)
            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialogInterface, int i) {
                    actionMoveHashStrings = null;
                }
            })
            .setPositiveButton(android.R.string.yes,
            new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                Spinner location = (Spinner) ((AlertDialog) dialog).findViewById(R.id.location_choice);
                CheckBox move = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.move);

                String dir = (String) location.getSelectedItem();
                ((TransmissionDataLoader) loader).setTorrentsLocation(
                    hashStrings, dir, move.isChecked());

                context.setRefreshing(true);
                actionMoveHashStrings = null;
            }
        }).setView(inflater.inflate(R.layout.torrent_location_dialog, null));

        TransmissionSession session = ((TransmissionSessionInterface) getActivity()).getSession();

        if (session == null) {
            return true;
        }

        AlertDialog dialog = builder.create();
        dialog.show();

        TransmissionProfileDirectoryAdapter adapter =
                new TransmissionProfileDirectoryAdapter(
                getActivity(), android.R.layout.simple_spinner_item);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        adapter.addAll(session.getDownloadDirectories());
        adapter.sort();

        Spinner location = (Spinner) dialog.findViewById(R.id.location_choice);
        location.setAdapter(adapter);

        TransmissionProfile profile = ((TransmissionSessionInterface) getActivity()).getProfile();
        if (locationPosition == AdapterView.INVALID_POSITION) {
            if (profile.getLastDownloadDirectory() != null) {
                int position = adapter.getPosition(profile.getLastDownloadDirectory());

                if (position > -1) {
                    location.setSelection(position);
                }
            }
        } else {
            location.setSelection(locationPosition);
        }
        location.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                locationPosition = i;
            }
            @Override public void onNothingSelected(AdapterView<?> adapterView) {}
        });

        ((CheckBox) dialog.findViewById(R.id.move)).setChecked(
            profile != null && profile.getMoveData());

        return true;
    }

    private class QueryCurrentDataTask extends AsyncTask<String, Void, Boolean> {
        @Override protected Boolean doInBackground(String... hashStrings) {
            if (!isCancelled()) {
                DataSource readSource = new DataSource(getActivity());

                readSource.open();

                try {
                    TorrentNameStatus tuple = readSource.getTorrentNameStatus(hashStrings[0]);
                    currentTorrentName = tuple.name;
                    currentTorrentStatus = tuple.status;
                } finally {
                    readSource.close();
                }
            }

            return null;
        }

        @Override protected void onPostExecute(Boolean success) {
            if (isResumed()) {
                setMenuTorrentState();
            }
        }
    }
}
