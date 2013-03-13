package org.sugr.gearshift;

import java.util.ArrayList;
import java.util.List;

import android.app.LoaderManager;
import android.content.Context;
import android.content.Loader;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

public class SettingsActivity extends PreferenceActivity
        implements LoaderManager.LoaderCallbacks<TransmissionProfile[]> {

    private Header mAppPreferencesHeader;
    private Header mProfileHeaderSeparatorHeader;
    private Header[] mProfileHeaders = new Header[0];

    private List<Header> mHeaders = new ArrayList<Header>();
    private TransmissionProfile[] mProfiles;

    private static final int LOADER_ID = 1;

    @Override
    public void onBuildHeaders(List<Header> target) {
        target.clear();
        target.add(getAppPreferencesHeader());

        if (mProfileHeaders.length > 0) {
            if (mProfileHeaderSeparatorHeader == null) {
                mProfileHeaderSeparatorHeader = new Header();
                mProfileHeaderSeparatorHeader.title = getText(R.string.header_label_profiles);
            }
            target.add(mProfileHeaderSeparatorHeader);

            for (Header profile : mProfileHeaders)
                target.add(profile);
        }

        mHeaders = target;
    }

    @Override
    public void setListAdapter(ListAdapter adapter) {
        if (adapter == null)
            super.setListAdapter(null);
        else
            super.setListAdapter(new HeaderAdapter(this, mHeaders));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

        if (savedInstanceState == null) {
            TorrentListActivity.logD("Creating the profile loader");

            getLoaderManager().initLoader(LOADER_ID, null, this);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (mProfiles != null)
            getMenuInflater().inflate(R.menu.add_profile_option, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.menu_add_profile:
            String name = TransmissionProfileSettingsFragment.class.getCanonicalName();
            if (!onIsHidingHeaders() && onIsMultiPane())
                switchToHeader(name, new Bundle());
            else
                startWithFragment(name, new Bundle(), null, 0);

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void onProfileListChange() {
        getLoaderManager().getLoader(LOADER_ID).onContentChanged();
    }

    @Override
    public Loader<TransmissionProfile[]> onCreateLoader(int id, Bundle args) {
        return new TransmissionProfileLoader(this);
    }

    @Override
    public void onLoadFinished(Loader<TransmissionProfile[]> loader,
            TransmissionProfile[] profiles) {
        mProfiles = profiles;

        TorrentListActivity.logD("Finished loading %d profiles", new Object[] {profiles.length});

        mProfileHeaders = new Header[profiles.length];
        int index = 0;
        for (TransmissionProfile profile : profiles) {
            mProfileHeaders[index++] = getProfileHeader(profile);
        }

        invalidateOptionsMenu();
        invalidateHeaders();
    }

    @Override
    public void onLoaderReset(Loader<TransmissionProfile[]> loader) {/*
        mProfileHeaders = new Header[0];
        mProfiles = null;

        TorrentListActivity.logD("Received profile loader reset");
        */
        invalidateHeaders();
        invalidateOptionsMenu();
    }

    private Header getProfileHeader(TransmissionProfile profile) {
        Header header = new Header();

        header.id = profile.getId().hashCode();
        header.title = profile.getName();
        header.summary = (profile.getUsername().length() > 0 ? profile.getUsername() + "@" : "")
                + profile.getHost() + ":" + profile.getPort();

        header.fragment = TransmissionProfileSettingsFragment.class.getCanonicalName();
        Bundle args = new Bundle();
        args.putString(TransmissionProfileSettingsFragment.ARG_PROFILE_ID, profile.getId());
        header.fragmentArguments = args;

        return header;
    }

    private Header getAppPreferencesHeader() {
        // Set up fixed header for general settings
        if (mAppPreferencesHeader == null) {
            mAppPreferencesHeader = new Header();
            mAppPreferencesHeader.id = R.id.general_preferences;
            mAppPreferencesHeader.title = getText(R.string.header_label_general_preferences);
            mAppPreferencesHeader.summary = null;
            mAppPreferencesHeader.iconRes = 0;
            mAppPreferencesHeader.fragment = GeneralSettingsFragment.class.getCanonicalName();
            mAppPreferencesHeader.fragmentArguments = null;
        }
        return mAppPreferencesHeader;
    }

    private static class HeaderAdapter extends ArrayAdapter<Header> {
        static final int HEADER_TYPE_CATEGORY = 0;
        static final int HEADER_TYPE_NORMAL = 1;
        private static final int HEADER_TYPE_COUNT = HEADER_TYPE_NORMAL + 1;

        private static class HeaderViewHolder {
            TextView title;
            TextView summary;
        }

        private LayoutInflater mInflater;

        static int getHeaderType(Header header) {
            if (header.fragment == null && header.intent == null) {
                return HEADER_TYPE_CATEGORY;
            } else {
                return HEADER_TYPE_NORMAL;
            }
        }

        @Override
        public int getItemViewType(int position) {
            Header header = getItem(position);
            return getHeaderType(header);
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false; // because of categories
        }

        @Override
        public boolean isEnabled(int position) {
            return getItemViewType(position) != HEADER_TYPE_CATEGORY;
        }

        @Override
        public int getViewTypeCount() {
            return HEADER_TYPE_COUNT;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        public HeaderAdapter(Context context, List<Header> objects) {
            super(context, 0, objects);

            mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            HeaderViewHolder holder;
            Header header = getItem(position);
            int headerType = getHeaderType(header);
            View view = null;

            if (convertView == null) {
                holder = new HeaderViewHolder();
                switch (headerType) {
                    case HEADER_TYPE_CATEGORY:
                        view = new TextView(getContext(), null,
                                android.R.attr.listSeparatorTextViewStyle);
                        holder.title = (TextView) view;
                        break;
                    case HEADER_TYPE_NORMAL:
                        view = mInflater.inflate(
                                R.layout.preference_header_item, parent,
                                false);
                        holder.title = (TextView)
                                view.findViewById(android.R.id.title);
                        holder.summary = (TextView)
                                view.findViewById(android.R.id.summary);
                        break;
                }
                view.setTag(holder);
            } else {
                view = convertView;
                holder = (HeaderViewHolder) view.getTag();
            }

            // All view fields must be updated every time, because the view may be recycled
            switch (headerType) {
                case HEADER_TYPE_CATEGORY:
                    holder.title.setText(header.getTitle(getContext().getResources()));
                    break;


                case HEADER_TYPE_NORMAL:
                    holder.title.setText(header.getTitle(getContext().getResources()));
                    CharSequence summary = header.getSummary(getContext().getResources());
                    if (!TextUtils.isEmpty(summary)) {
                        holder.summary.setVisibility(View.VISIBLE);
                        holder.summary.setText(summary);
                    } else {
                        holder.summary.setVisibility(View.GONE);
                    }
                    break;
            }

            return view;
        }
    }
}
