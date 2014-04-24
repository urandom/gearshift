package org.sugr.gearshift.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.core.Torrent;
import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.datasource.TorrentNameStatus;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.service.DataServiceManagerInterface;

import java.util.Arrays;
import java.util.HashMap;

public class TorrentDetailFragment extends Fragment implements TorrentListNotificationInterface {
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

    private static final String STATE_ACTION_MOVE_HASH_STRINGS= "action_move_hash_strings";

    private String[] actionMoveHashStrings;

    private Menu menu;

    private String[] torrentHashStrings;
    private HashMap<String, Integer> torrentPositionMap = new HashMap<>();

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
                    intent.putExtra(G.ARG_TORRENT_HASH_STRING, currentTorrentPosition);

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
        final DataServiceManager manager =
            ((DataServiceManagerInterface) getActivity()).getDataServiceManager();

        final TransmissionSessionInterface context = ((TransmissionSessionInterface) getActivity());

        if (manager == null || torrentHashStrings == null || torrentHashStrings.length <= currentTorrentPosition) {
            return super.onOptionsItemSelected(item);
        }

        final String[] hashStrings = new String[] { currentTorrentHashString };

        String action;
        switch (item.getItemId()) {
            case R.id.remove:
            case R.id.delete:
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.no, null);

                builder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int id) {
                        manager.removeTorrent(hashStrings, item.getItemId() == R.id.delete);
                        context.setRefreshing(true, DataService.Requests.REMOVE_TORRENT);
                    }
                })
                    .setMessage(String.format(getString(
                                    item.getItemId() == R.id.delete
                            ? R.string.delete_current_confirmation
                            : R.string.remove_current_confirmation),
                                G.trimTrailingWhitespace(Html.fromHtml(currentTorrentName))))
                    .show();
                return true;
            case R.id.resume:
                switch(currentTorrentStatus) {
                    case Torrent.Status.DOWNLOAD_WAITING:
                    case Torrent.Status.SEED_WAITING:
                        action = "torrent-start-now";
                        break;
                    default:
                        action = "torrent-start";
                        break;
                }
                break;
            case R.id.pause:
                action = "torrent-stop";
                break;
            case R.id.move:
                actionMoveHashStrings = hashStrings;
                return showMoveDialog(hashStrings);
            case R.id.verify:
                action = "torrent-verify";
                break;
            case R.id.reannounce:
                action = "torrent-reannounce";
                break;
            default:
                return false;
        }

        manager.setTorrentAction(hashStrings, action);
        context.setRefreshing(true, DataService.Requests.SET_TORRENT_ACTION);

        return true;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArray(STATE_ACTION_MOVE_HASH_STRINGS, actionMoveHashStrings);
    }

    public boolean changeCursor(Cursor newCursor) {
        boolean equal = updateTorrentData(newCursor);
        if (pager.getAdapter() == null) {
            setCurrentTorrentHashString(currentTorrentPosition);

            resetPagerAdapter();
            setMenuTorrentState();
        }

        return equal;
    }
    public int getTorrentPositionInCursor(String hash) {
        Integer position = torrentPositionMap.get(hash);

        return position == null ? -1 : position;
    }

    public String getTorrentHashString(int position) {
        return torrentHashStrings != null && torrentHashStrings.length > position && position != -1
            ? torrentHashStrings[position] : null;
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
    private boolean updateTorrentData(Cursor cursor) {
        int cursorPosition = cursor.getPosition();
        int position = -1;

        String[] oldHashStrings = torrentHashStrings;
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

        return Arrays.equals(oldHashStrings, torrentHashStrings);
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
                                         boolean status, boolean metadata, boolean connected) {
        TransmissionSessionInterface context = ((TransmissionSessionInterface) getActivity());
        if (context == null || context.getSession() == null || cursor == null || cursor.isClosed()) {
            setMenuTorrentState();
            return;
        }
        boolean updateMenu = status;

        if (!changeCursor(cursor)) {
            int position = getTorrentPositionInCursor(currentTorrentHashString);

            resetPagerAdapter();
            if (position != -1) {
                pager.setCurrentItem(position);
            } else {
                updateMenu = true;

                int cursorPosition = cursor.getPosition();

                cursor.moveToPosition(currentTorrentPosition);

                if (!cursor.isAfterLast()) {
                    currentTorrentHashString = Torrent.getHashString(cursor);
                    currentTorrentName = Torrent.getName(cursor);
                    currentTorrentStatus = Torrent.getStatus(cursor);
                }

                cursor.moveToPosition(cursorPosition);
            }
            new Handler().post(new Runnable() {
                @Override public void run() {
                    pagerCallbacks.onPageSelected(pager.getCurrentItem());
                }
            });
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
                intent.putExtra(G.ARG_TORRENT_HASH_STRING, getTorrentHashString(i));

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
        final DataServiceManager manager =
            ((DataServiceManagerInterface) getActivity()).getDataServiceManager();

        if (manager == null) {
            return true;
        }

        final TransmissionSession session = ((TransmissionSessionInterface) getActivity()).getSession();

        if (session == null) {
            return true;
        }

        AlertDialog dialog = ((LocationDialogHelperInterface) getActivity())
            .getLocationDialogHelper().showDialog(R.layout.torrent_location_dialog,
                R.string.set_location, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        actionMoveHashStrings = null;
                    }
                }, new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface dialog, int which) {
                        Spinner location = (Spinner) ((AlertDialog) dialog).findViewById(R.id.location_choice);
                        EditText entry = (EditText) ((AlertDialog) dialog).findViewById(R.id.location_entry);
                        CheckBox move = (CheckBox) ((AlertDialog) dialog).findViewById(R.id.move);
                        String dir;

                        if (location.getVisibility() != View.GONE) {
                            dir = (String) location.getSelectedItem();
                        } else {
                            dir = entry.getText().toString();
                        }

                        if (TextUtils.isEmpty(dir)) {
                            dir = session.getDownloadDir();
                        }

                        manager.setTorrentLocation(hashStrings, dir, move.isChecked());
                        ((TransmissionSessionInterface) getActivity()).setRefreshing(true,
                            DataService.Requests.SET_TORRENT_LOCATION);
                        actionMoveHashStrings = null;
                    }
                }
        );

        TransmissionProfile profile = ((TransmissionProfileInterface) getActivity()).getProfile();
        ((CheckBox) dialog.findViewById(R.id.move)).setChecked(
            profile != null && profile.getMoveData());

        return true;
    }

    private class QueryCurrentDataTask extends AsyncTask<String, Void, Boolean> {
        @Override protected Boolean doInBackground(String... hashStrings) {
            if (!isCancelled() && getActivity() != null) {
                TransmissionProfileInterface context = (TransmissionProfileInterface) getActivity();
                if (context == null) {
                    return null;
                }

                DataSource readSource = new DataSource(getActivity());

                readSource.open();

                try {
                    TorrentNameStatus tuple = readSource.getTorrentNameStatus(
                        context.getProfile().getId(), hashStrings[0]);

                    if (tuple != null) {
                        currentTorrentName = tuple.name;
                        currentTorrentStatus = tuple.status;
                    }
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
