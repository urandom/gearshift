package org.sugr.gearshift;

import android.app.LoaderManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends PreferenceActivity
        implements LoaderManager.LoaderCallbacks<TransmissionProfile[]> {

    private Header appPreferencesHeader;
    private Header filtersHeader;
    private Header sortHeader;
    private Header profileHeaderSeparatorHeader;
    private Header[] profileHeaders = new Header[0];

    private List<Header> headers = new ArrayList<Header>();
    private TransmissionProfile[] profiles;

    private static final int LOADER_ID = 1;

    private SharedPreferences.OnSharedPreferenceChangeListener defaultPrefListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(G.PREF_PROFILES)) {
                Loader loader = getLoaderManager().getLoader(LOADER_ID);

                if (loader != null) {
                    loader.onContentChanged();
                }
            }
        }
    };

    private SharedPreferences.OnSharedPreferenceChangeListener profilesPrefListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Loader loader = getLoaderManager().getLoader(LOADER_ID);

            if (loader != null) {
                loader.onContentChanged();
            }
        }
    };

    @Override
    public void onBuildHeaders(List<Header> target) {
        target.clear();
        target.add(getAppPreferencesHeader());
        target.add(getFiltersHeader());
        target.add(getSortHeader());

        if (profileHeaders.length > 0) {
            if (profileHeaderSeparatorHeader == null) {
                profileHeaderSeparatorHeader = new Header();
                profileHeaderSeparatorHeader.title = getText(R.string.header_label_profiles);
            }
            target.add(profileHeaderSeparatorHeader);

            for (Header profile : profileHeaders)
                target.add(profile);
        }

        headers = target;
    }

    @Override
    public void setListAdapter(ListAdapter adapter) {
        if (adapter == null)
            super.setListAdapter(null);
        else
            super.setListAdapter(new HeaderAdapter(this, headers));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setHomeButtonEnabled(true);

        if (savedInstanceState == null) {
            G.logD("Creating the profile loader");

            getLoaderManager().initLoader(LOADER_ID, null, this);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.registerOnSharedPreferenceChangeListener(defaultPrefListener);

            prefs = TransmissionProfile.getPreferences(this);
            prefs.registerOnSharedPreferenceChangeListener(profilesPrefListener);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        if (profiles != null)
            getMenuInflater().inflate(R.menu.add_profile_option, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
        case R.id.menu_add_profile:
            String name = TransmissionProfileSettingsFragment.class.getCanonicalName();
            Bundle args = new Bundle();
            if (!onIsHidingHeaders() && onIsMultiPane())
                switchToHeader(name, args);
            else
                startWithFragment(name, args, null, 0);

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public Loader<TransmissionProfile[]> onCreateLoader(int id, Bundle args) {
        return new TransmissionProfileLoader(this);
    }

    @Override
    public void onLoadFinished(Loader<TransmissionProfile[]> loader,
            TransmissionProfile[] profiles) {
        this.profiles = profiles;

        G.logD("Finished loading %d profiles", new Object[] {profiles.length});

        profileHeaders = new Header[profiles.length];
        int index = 0;
        for (TransmissionProfile profile : profiles) {
            profileHeaders[index++] = getProfileHeader(profile);
        }

        invalidateOptionsMenu();
        invalidateHeaders();
    }

    @Override
    public void onLoaderReset(Loader<TransmissionProfile[]> loader) {/*
        profileHeaders = new Header[0];
        profiles = null;

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
        args.putString(G.ARG_PROFILE_ID, profile.getId());

        Intent intent = getIntent();
        if (intent.hasExtra(G.ARG_PROFILE_ID) &&
                profile.getId().equals(intent.getStringExtra(G.ARG_PROFILE_ID))) {
            args.putStringArrayList(G.ARG_DIRECTORIES,
                    intent.getStringArrayListExtra(G.ARG_DIRECTORIES));
        }
        header.fragmentArguments = args;

        return header;
    }

    private Header getAppPreferencesHeader() {
        // Set up fixed header for general settings
        if (appPreferencesHeader == null) {
            appPreferencesHeader = new Header();
            appPreferencesHeader.id = R.id.general_preferences;
            appPreferencesHeader.title = getText(R.string.header_label_general_preferences);
            appPreferencesHeader.summary = null;
            appPreferencesHeader.iconRes = 0;
            appPreferencesHeader.fragment = GeneralSettingsFragment.class.getCanonicalName();
            appPreferencesHeader.fragmentArguments = null;
        }
        return appPreferencesHeader;
    }

    private Header getFiltersHeader() {
        if (filtersHeader == null) {
            filtersHeader = new Header();
            filtersHeader.id = R.id.filters_preferences;
            filtersHeader.title = getText(R.string.header_label_filters);
            filtersHeader.summary = null;
            filtersHeader.iconRes = 0;
            filtersHeader.fragment = FiltersSettingsFragment.class.getCanonicalName();
            filtersHeader.fragmentArguments = null;
        }

        return filtersHeader;
    }

    private Header getSortHeader() {
        if (sortHeader == null) {
            sortHeader = new Header();
            sortHeader.id = R.id.sort_preferences;
            sortHeader.title = getText(R.string.header_label_sort);
            sortHeader.summary = null;
            sortHeader.iconRes = 0;
            sortHeader.fragment = SortSettingsFragment.class.getCanonicalName();
            sortHeader.fragmentArguments = null;
        }

        return sortHeader;
    }

    private static class HeaderAdapter extends ArrayAdapter<Header> {
        static final int HEADER_TYPE_CATEGORY = 0;
        static final int HEADER_TYPE_NORMAL = 1;
        private static final int HEADER_TYPE_COUNT = HEADER_TYPE_NORMAL + 1;

        private static class HeaderViewHolder {
            TextView title;
            TextView summary;
        }

        private LayoutInflater inflater;

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

            inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
                        view = inflater.inflate(
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
