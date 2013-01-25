package us.supositi.gearshift;

import java.text.MessageFormat;
import java.util.HashSet;

import us.supositi.gearshift.dummy.DummyContent;
import android.app.ActionBar;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.text.Html;
import android.text.Spanned;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * A list fragment representing a list of Torrents. This fragment
 * also supports tablet devices by allowing list items to be given an
 * 'activated' state upon selection. This helps indicate which item is
 * currently being viewed in a {@link TorrentDetailFragment}.
 * <p>
 * Activities containing this fragment MUST implement the {@link Callbacks}
 * interface.
 */
public class TorrentListFragment extends ListFragment {

    /**
     * The serialization (saved instance state) Bundle key representing the
     * activated item position. Only used on tablets.
     */
    private static final String STATE_ACTIVATED_POSITION = "activated_position";

    /**
     * The fragment's current callback object, which is notified of list item
     * clicks.
     */
    private Callbacks mCallbacks = sDummyCallbacks;

    /**
     * The current activated item position. Only used on tablets.
     */
    private int mActivatedPosition = ListView.INVALID_POSITION;
    
    private boolean mAltSpeed = false;
    
    private boolean mRefreshing = false;
    
    private boolean mIsCABDestroyed = false;

    private int mChoiceMode = ListView.CHOICE_MODE_NONE;
    
    private TorrentProfileListAdapter mProfileAdapter;
    private TorrentListAdapter mTorrentListAdapter;
    
    private static final int PROFILES_LOADER_ID = 1;
    private static final int TORRENTS_LOADER_ID = 2;
        
    private TorrentProfile mCurrentProfile;
    
    /**
     * A callback interface that all activities containing this fragment must
     * implement. This mechanism allows activities to be notified of item
     * selections.
     */
    public interface Callbacks {
        /**
         * Callback for when an item has been selected.
         */
        public void onItemSelected(String id);
    }

    /**
     * A dummy implementation of the {@link Callbacks} interface that does
     * nothing. Used only when this fragment is not attached to an activity.
     */
    private static Callbacks sDummyCallbacks = new Callbacks() {
        @Override
        public void onItemSelected(String id) {
        }
    };
    
    private LoaderCallbacks<TorrentProfile[]> mProfileLoaderCallbacks = new LoaderCallbacks<TorrentProfile[]>() {
        @Override
        public android.support.v4.content.Loader<TorrentProfile[]> onCreateLoader(
                int id, Bundle args) {
            if (id == PROFILES_LOADER_ID) {
                return new TorrentProfileSupportLoader(getActivity()); 
            }
            return null;
        }

        @Override
        public void onLoadFinished(
                android.support.v4.content.Loader<TorrentProfile[]> loader,
                TorrentProfile[] profiles) {
            
            mProfileAdapter.clear();
            if (profiles.length > 0) {
                mProfileAdapter.addAll(profiles);
            } else {
                mProfileAdapter.add(TorrentProfileListAdapter.EMPTY_PROFILE);
            }
            
            String currentId = TorrentProfile.getCurrentProfileId(getActivity());
            int index = 0;
            for (TorrentProfile prof : profiles) {
                if (prof.getId().equals(currentId)) {
                    ActionBar actionBar = getActivity().getActionBar();
                    if (actionBar != null)
                        actionBar.setSelectedNavigationItem(index);
                    mCurrentProfile = prof;
                    break;
                }
                index++;
            }
        }

        @Override
        public void onLoaderReset(
                android.support.v4.content.Loader<TorrentProfile[]> loader) {
            mProfileAdapter.clear();
        }
        
    };

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public TorrentListFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTorrentListAdapter = new TorrentListAdapter(getActivity());
        mTorrentListAdapter.add(new Torrent(1, "Item 1"));
        mTorrentListAdapter.add(new Torrent(2, "Item 2"));
        Torrent torrent = new Torrent(3, "Item 3");
        torrent.setPercentDone(1f);
        torrent.setStatus(Torrent.Status.SEEDING);
        mTorrentListAdapter.add(torrent);
        torrent = new Torrent(4, "Item 4");
        torrent.setPercentDone(0.3f);
        torrent.setStatus(Torrent.Status.DOWNLOAD_WAITING);
        mTorrentListAdapter.add(torrent);
        setListAdapter(mTorrentListAdapter);

        
        setHasOptionsMenu(true);
        getActivity().requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);  
        getActivity().setProgressBarIndeterminateVisibility(true);
        
        ActionBar actionBar = getActivity().getActionBar();
        if (actionBar != null) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            
            mProfileAdapter = new TorrentProfileListAdapter(getActivity());
            
            actionBar.setListNavigationCallbacks(mProfileAdapter, new ActionBar.OnNavigationListener() {
                @Override
                public boolean onNavigationItemSelected(int pos, long id) {
                    TorrentProfile profile = mProfileAdapter.getItem(pos);
                    TorrentProfile.setCurrentProfile(profile, getActivity());
                    
                    return false;
                }
            });
            
            actionBar.setDisplayOptions(0, ActionBar.DISPLAY_SHOW_TITLE);
        }

        getActivity().getSupportLoaderManager().initLoader(PROFILES_LOADER_ID, null, mProfileLoaderCallbacks);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Restore the previously serialized activated item position.
        if (savedInstanceState != null
                && savedInstanceState.containsKey(STATE_ACTIVATED_POSITION)) {
            setActivatedPosition(savedInstanceState.getInt(STATE_ACTIVATED_POSITION));
        }
    }
    
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        
        final ListView list = getListView();
        list.setChoiceMode(mChoiceMode);
        list.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view,
                    int position, long id) {
                
                if (!((TorrentListActivity) getActivity()).isDetailsPanelShown()) {
                    list.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
                    mIsCABDestroyed = false;
                    setActivatedPosition(position);
                    return true;
                }
                return false;
            }});
        
        list.setMultiChoiceModeListener(new MultiChoiceModeListener() {
            private HashSet<String> mSelectedTorrentIds;
            
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.remove:
                        mode.finish();
                        break;
                    case R.id.delete:
                        mode.finish();
                        break;
                    case R.id.resume:
                        mode.finish();
                        break;
                    case R.id.pause:
                        mode.finish();
                        break;
                }
                return true;
            }
        
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.torrent_list_multiselect, menu);
                
                /* FIXME: Set these states depending on the torrent state */
                MenuItem item = menu.findItem(R.id.resume);
                item.setVisible(false).setEnabled(false);
                
                item = menu.findItem(R.id.pause);
                item.setVisible(true).setEnabled(true);
                
                mSelectedTorrentIds = new HashSet<String>();
                return true;
            }
        
            @Override
            public void onDestroyActionMode(ActionMode mode) {
                TorrentListActivity.logD("Destroying context menu");
                mIsCABDestroyed = true;
                mSelectedTorrentIds = null;
            }
        
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }
        
            @Override
            public void onItemCheckedStateChanged(ActionMode mode,
                    int position, long id, boolean checked) {
                
                if (checked)
                    mSelectedTorrentIds.add(DummyContent.ITEMS.get(position).id);
                else
                    mSelectedTorrentIds.remove(DummyContent.ITEMS.get(position).id);
            }});
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        // Activities containing this fragment must implement its callbacks.
        if (!(activity instanceof Callbacks)) {
            throw new IllegalStateException("Activity must implement fragment's callbacks.");
        }

        mCallbacks = (Callbacks) activity;
    }

    @Override
    public void onDetach() {
        super.onDetach();

        // Reset the active callbacks interface to the dummy implementation.
        mCallbacks = sDummyCallbacks;
    }

    @Override
    public void onListItemClick(ListView listView, View view, int position, long id) {
        super.onListItemClick(listView, view, position, id);
        
        if (mIsCABDestroyed)
            listView.setChoiceMode(mChoiceMode);
        
        // Notify the active callbacks interface (the activity, if the
        // fragment is attached to one) that an item has been selected.
        mCallbacks.onItemSelected(DummyContent.ITEMS.get(position).id);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mActivatedPosition != ListView.INVALID_POSITION) {
            // Serialize and persist the activated item position.
            outState.putInt(STATE_ACTIVATED_POSITION, mActivatedPosition);
        }
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_torrent_list, container, false);
                
        return rootView;
    }
    
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.torrent_list_options, menu);
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_alt_speed:
                if (mAltSpeed) {
                    mAltSpeed = false;
                    item.setIcon(R.drawable.ic_menu_alt_speed_off);
                    item.setTitle(R.string.alt_speed_label_on);
                } else {
                    mAltSpeed = true;
                    item.setIcon(R.drawable.ic_menu_alt_speed_on);
                    item.setTitle(R.string.alt_speed_label_off);
                }
                return true;
            case R.id.menu_refresh:
                if (mRefreshing)
                    item.setActionView(null);
                else
                    item.setActionView(R.layout.action_progress_bar);

                mRefreshing = !mRefreshing;
                return true;
            case R.id.menu_settings:
                Intent i = new Intent(getActivity(), SettingsActivity.class); 
                getActivity().startActivity(i);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    
    /**
     * Turns on activate-on-click mode. When this mode is on, list items will be
     * given the 'activated' state when touched.
     */
    public void setActivateOnItemClick(boolean activateOnItemClick) {
        // When setting CHOICE_MODE_SINGLE, ListView will automatically
        // give items the 'activated' state when touched.
        mChoiceMode = activateOnItemClick
                ? ListView.CHOICE_MODE_SINGLE
                : ListView.CHOICE_MODE_NONE;
        getListView().setChoiceMode(mChoiceMode);
    }

    private void setActivatedPosition(int position) {
        if (position == ListView.INVALID_POSITION) {
            getListView().setItemChecked(mActivatedPosition, false);
        } else {
            getListView().setItemChecked(position, true);
        }

        mActivatedPosition = position;
    }
    
    private static class TorrentProfileListAdapter extends ArrayAdapter<TorrentProfile> {
        public static final TorrentProfile EMPTY_PROFILE = new TorrentProfile();        
        
        public TorrentProfileListAdapter(Context context) {
            super(context, 0);

            add(EMPTY_PROFILE);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            TorrentProfile profile = getItem(position);
            
            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_profile_selector, null);
            }
            
            TextView name = (TextView) rowView.findViewById(R.id.name);
            TextView summary = (TextView) rowView.findViewById(R.id.summary);
            
            if (profile == EMPTY_PROFILE) {
                name.setText(R.string.no_profiles);
                if (summary != null)
                    summary.setText(R.string.create_profile_in_settings);
            } else {
                name.setText(profile.getName());
                if (summary != null)
                    summary.setText((profile.getUsername().length() > 0 ? profile.getUsername() + "@" : "")
                        + profile.getHost() + ":" + profile.getPort());
            }
            
            return rowView;
        }
        
        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;

            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_profile_selector_dropdown, null);
            }

            return getView(position, rowView, parent);
        }
    }
    
    private class TorrentListAdapter extends ArrayAdapter<Torrent> {        

        public TorrentListAdapter(Context context) {
            super(context, R.layout.torrent_list_item, R.id.name);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            Torrent torrent = getItem(position);
            
            if (rowView == null) {
                LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = vi.inflate(R.layout.torrent_list_item, parent, false);
            }
            
            TextView name = (TextView) rowView.findViewById(R.id.name);
            
            TextView traffic = (TextView) rowView.findViewById(R.id.summary);
            ProgressBar progress = (ProgressBar) rowView.findViewById(R.id.progress);
            TextView status = (TextView) rowView.findViewById(R.id.status);
            
            name.setText(torrent.getName());
            
            progress.setProgress((int) (torrent.getPercentDone() * 100));
            
            String statusFormat = getString(R.string.status_format);
            String formattedStatus, statusType, statusMoreFormat, statusSpeedFormat, statusSpeed;
            int peers;
            
            switch(torrent.getStatus()) {
                case Torrent.Status.DOWNLOAD_WAITING:
                case Torrent.Status.DOWNLOADING:
                    statusType = getString(R.string.status_downloading);
                    statusMoreFormat = getString(R.string.status_more_downloading_format);
                    statusSpeedFormat = getString(R.string.status_more_downloading_speed_format);
                    
                    if (torrent.isStalled()) {
                        statusSpeed = getString(R.string.status_more_idle);
                    } else {
                        statusSpeed = MessageFormat.format(statusSpeedFormat, new Object[] {
                            Torrent.readableFileSize(torrent.getRateDownload()),
                            Torrent.readableFileSize(torrent.getRateUpload())
                        });
                    }
                    
                    peers = torrent.getPeersSendingToUs();
                    
                    formattedStatus = MessageFormat.format(statusFormat, new Object[] {statusType, 
                            MessageFormat.format(statusMoreFormat, new Object[] {
                                peers, torrent.getPeersConnected(), statusSpeed
                            })
                        });
                    break;
                case Torrent.Status.SEED_WAITING:
                case Torrent.Status.SEEDING:
                    statusType = getString(R.string.status_uploading);
                    statusMoreFormat = getString(R.string.status_more_uploading_format);
                    statusSpeedFormat = getString(R.string.status_more_uploading_speed_format);
                    
                    if (torrent.isStalled()) {
                        statusSpeed = getString(R.string.status_more_idle);
                    } else {
                        statusSpeed = MessageFormat.format(statusSpeedFormat, new Object[] {
                            Torrent.readableFileSize(torrent.getRateUpload())
                        });
                    }
                    peers = torrent.getPeersGettingFromUs();
                    
                    formattedStatus = MessageFormat.format(statusFormat, new Object[] {statusType, 
                            MessageFormat.format(statusMoreFormat, new Object[] {
                                peers, torrent.getPeersConnected(), statusSpeed
                            })
                        });
                    break;
                case Torrent.Status.CHECK_WAITING:
                    statusType = getString(R.string.status_checking);
                    
                    formattedStatus = MessageFormat.format(statusFormat, new Object[] {
                        statusType,
                        "-" + getString(R.string.status_more_idle)
                    });
                    break;
                case Torrent.Status.CHECKING:
                    formattedStatus = getString(R.string.status_checking);

                    break;
                default:
                    formattedStatus = getString(R.string.status_stopped);
                    
                    break;
            }

            Spanned processedStatus = Html.fromHtml(formattedStatus);
            status.setText(processedStatus);

            return rowView;
        }
    }
}
