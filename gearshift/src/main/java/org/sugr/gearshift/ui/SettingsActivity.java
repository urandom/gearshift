package org.sugr.gearshift.ui;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.ui.loader.TransmissionProfileSupportLoader;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SettingsActivity extends ActionBarActivity {
    private SlidingPaneLayout slidingPane;
    private RecyclerView profileList;

    private ProfileAdapter profileAdapter;

    private enum Type {
        HEADER, PREFERENCES, PROFILE
    }

    private static final int PREFERENCE_GROUP_COUNT = 3;
    private static final String PREFERENCE_FRAGMENT_TAG = "preference-fragment";

    private SharedPreferences.OnSharedPreferenceChangeListener defaultPrefListener
            = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(G.PREF_PROFILES)) {
                Loader loader = getSupportLoaderManager().getLoader(G.PROFILES_LOADER_ID);

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
            Loader loader = getSupportLoaderManager().getLoader(G.PROFILES_LOADER_ID);

            if (loader != null) {
                loader.onContentChanged();
            }
        }
    };


    private LoaderManager.LoaderCallbacks<TransmissionProfile[]> profileLoaderCallbacks = new LoaderManager.LoaderCallbacks<TransmissionProfile[]>() {
        @Override
        public android.support.v4.content.Loader<TransmissionProfile[]> onCreateLoader(
            int id, Bundle args) {
            return new TransmissionProfileSupportLoader(SettingsActivity.this);
        }

        @Override
        public void onLoadFinished(
            android.support.v4.content.Loader<TransmissionProfile[]> loader,
            TransmissionProfile[] profiles) {

            SettingsActivity context = SettingsActivity.this;
            List<ProfileItem> items = new ArrayList<>(profiles.length);

            for (TransmissionProfile profile : profiles) {
                items.add(createProfileItem(profile));
            }

            if (items.size() == 0 && profileAdapter.itemData.size() > PREFERENCE_GROUP_COUNT) {
                int count = profileAdapter.itemData.size() - PREFERENCE_GROUP_COUNT;
                for (int i = 0; i < count; i++) {
                    profileAdapter.itemData.remove(PREFERENCE_GROUP_COUNT + i);
                }
                profileAdapter.notifyItemRangeRemoved(PREFERENCE_GROUP_COUNT, count);
            } else if (items.size() > 0 && profileAdapter.itemData.size() == PREFERENCE_GROUP_COUNT) {
                ProfileItem header = new ProfileItem("profile-header", Type.HEADER,
                    getString(R.string.header_label_profiles), null);

                profileAdapter.itemData.add(header);
                profileAdapter.notifyItemInserted(PREFERENCE_GROUP_COUNT);
            }

            Iterator<ProfileItem> iter = profileAdapter.itemData.iterator();
            int index = 0;
            while (iter.hasNext()) {
                ProfileItem item = iter.next();

                if (item.getType() == Type.PROFILE) {
                    if (items.contains(item)) {
                        items.remove(item);
                        index++;
                    } else {
                        iter.remove();
                        profileAdapter.notifyItemRemoved(index);
                    }
                }
            }

            if (items.size() > 0) {
                int start = profileAdapter.itemData.size();
                for (ProfileItem item : items) {
                    profileAdapter.itemData.add(item);
                }
                profileAdapter.notifyItemRangeInserted(start, items.size());
            }
        }

        @Override
        public void onLoaderReset(
            android.support.v4.content.Loader<TransmissionProfile[]> loader) {

            Iterator<ProfileItem> iter = profileAdapter.itemData.iterator();
            int count = 0;
            while (iter.hasNext()) {
                ProfileItem item = iter.next();

                if (item.getType() == Type.HEADER || item.getType() == Type.PROFILE) {
                    iter.remove();
                    count++;
                }
            }


            profileAdapter.notifyItemRangeRemoved(PREFERENCE_GROUP_COUNT, count);
        }

    };

    public boolean isPreferencesOpen() {
        return getFragmentManager().findFragmentByTag(PREFERENCE_FRAGMENT_TAG) == null;
    }

    public boolean isPreferencesAlwaysVisible() {
        return !slidingPane.isSlideable();
    }

    public void closePreferences() {
        if (!isPreferencesOpen()) {
            return;
        }

        findViewById(R.id.watermark).setVisibility(View.VISIBLE);

        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.remove(fm.findFragmentByTag(PREFERENCE_FRAGMENT_TAG));
        transaction.commit();
        fm.executePendingTransactions();
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);

        setContentView(R.layout.activity_settings);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        slidingPane = (SlidingPaneLayout) findViewById(R.id.sliding_pane);
        slidingPane.openPane();
        slidingPane.setSliderFadeColor(getResources().getColor(R.color.preference_background));
        slidingPane.setShadowResourceLeft(R.drawable.pane_shadow);

        profileAdapter = new ProfileAdapter(this);

        profileList = (RecyclerView) findViewById(R.id.profile_list);

        profileList.setLayoutManager(new LinearLayoutManager(this));
        profileList.setAdapter(profileAdapter);

        fillPreferences();

        getSupportLoaderManager().initLoader(G.PROFILES_LOADER_ID, null, profileLoaderCallbacks);
        if (state == null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.registerOnSharedPreferenceChangeListener(defaultPrefListener);

            prefs = getSharedPreferences(TransmissionProfile.getPreferencesName(),
                Activity.MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(profilesPrefListener);

        }
    }

    @Override protected void onResume() {
        super.onResume();

        FragmentManager fm = getFragmentManager();
        View watermark = findViewById(R.id.watermark);
        watermark.setVisibility(fm.findFragmentByTag(PREFERENCE_FRAGMENT_TAG) == null
            ? View.VISIBLE : View.GONE);
    }

    private void setSelectedItem(ProfileItem item) {
        if (item.getType() == Type.HEADER) {
            return;
        }

        Fragment fragment = null;
        switch (item.getId()) {
            case "general-preferences":
                fragment = new GeneralSettingsFragment();
                break;
            case "filter-preferences":
                fragment = new FiltersSettingsFragment();
                break;
            case "sort-preferences":
                fragment = new SortSettingsFragment();
                break;
        }

        if (fragment == null) {
            return;
        }

        findViewById(R.id.watermark).setVisibility(View.GONE);

        FragmentManager fm = getFragmentManager();
        fm.beginTransaction().replace(R.id.preference_panel, fragment, PREFERENCE_FRAGMENT_TAG)
            .commit();

        fm.executePendingTransactions();

        slidingPane.closePane();
    }

    private void fillPreferences() {
        ProfileItem item = new ProfileItem("general-preferences", Type.PREFERENCES,
            getString(R.string.header_label_general_preferences), null);

        profileAdapter.itemData.add(0, item);

       item = new ProfileItem("filter-preferences", Type.PREFERENCES,
            getString(R.string.header_label_filters), null);

        profileAdapter.itemData.add(1, item);

        item = new ProfileItem("sort-preferences", Type.PREFERENCES,
            getString(R.string.header_label_sort), null);

        profileAdapter.itemData.add(1, item);

        profileAdapter.notifyItemRangeInserted(0, profileAdapter.itemData.size());
    }

    private ProfileItem createProfileItem(TransmissionProfile profile) {
        String sublabel = (profile.getUsername().length() > 0 ? profile.getUsername() + "@" : "")
                + profile.getHost() + ":" + profile.getPort();

        ProfileItem item = new ProfileItem(profile.getId(), Type.PROFILE,
            profile.getName(), sublabel);

        return item;
    }

    private static class ProfileAdapter extends SelectableRecyclerViewAdapter<ProfileAdapter.ViewHolder, ProfileItem> {
        private SettingsActivity context;

        public ProfileAdapter(SettingsActivity context) {
            this.context = context;
        }

        @Override public boolean isItemSelectable(int position) {
            if (position == -1 || itemData.size() <= position) {
                return false;
            }

            ProfileItem item = itemData.get(position);
            if (item.getType() == Type.HEADER) {
                return false;
            }

            return true;
        }

        @Override public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemLayoutView = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);

            ViewHolder holder = new ViewHolder(itemLayoutView, viewType);
            return holder;
        }

        @Override public void onBindViewHolder(ViewHolder holder, final int position) {
            super.onBindViewHolder(holder, position);

            final ProfileItem item = itemData.get(position);

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    context.setSelectedItem(item);
                }
            });

            holder.label.setText(item.getLabel());
            if (holder.sublabel != null) {
                holder.sublabel.setText(item.getSublabel());
            }
        }

        @Override public long getItemId(int position) {
            return this.itemData.get(position).hashCode();
        }

        @Override public int getItemViewType(int position) {
            ProfileItem item = itemData.get(position);
            switch (item.getType()) {
                case HEADER:
                    return R.layout.settings_profile_header;
                default:
                    return R.layout.settings_profile_item;
            }
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public TextView label;
            public TextView sublabel;

            public ViewHolder(View itemView, int type) {
                super(itemView);

                label = (TextView) itemView.findViewById(android.R.id.text1);
                sublabel = (TextView) itemView.findViewById(android.R.id.text2);
            }
        }
    }

    private class ProfileItem {
        private String id;
        private Type type;
        private String label;
        private String sublabel;

        private ProfileItem(String id, Type type, String label, String sublabel) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.sublabel = sublabel;
        }

        public String getId() {
            return id;
        }

        public Type getType() {
            return type;
        }

        public String getLabel() {
            return label;
        }

        public String getSublabel() {
            return sublabel;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ProfileItem that = (ProfileItem) o;

            return id.equals(that.id);
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + type.hashCode();
            result = 31 * result + label.hashCode();
            result = 31 * result + (sublabel != null ? sublabel.hashCode() : 0);
            return result;
        }
    }
}
