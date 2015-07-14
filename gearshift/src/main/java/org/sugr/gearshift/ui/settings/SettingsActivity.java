package org.sugr.gearshift.ui.settings;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.NavUtils;
import android.support.v4.content.Loader;
import android.support.v4.widget.SlidingPaneLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.ui.SelectableRecyclerViewAdapter;
import org.sugr.gearshift.ui.TorrentListActivity;
import org.sugr.gearshift.ui.loader.TransmissionProfileSupportLoader;
import org.sugr.gearshift.ui.util.Colorizer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {
    enum Type {
        PROFILE_HEADER, PREFERENCES, PROFILE, PROFILE_DIRECTORIES
    }

    private SlidingPaneLayout slidingPane;
    private RecyclerView profileList;

    private ProfileAdapter profileAdapter;

    private static final int PREFERENCE_GROUP_COUNT = 3;
    private static final String PREFERENCE_FRAGMENT_TAG = "preference-fragment";
    private static final String DIRECTORIES_FRAGMENT_TAG = "directories-fragment";

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
                Iterator<ProfileItem> iter = profileAdapter.itemData.iterator();
                int count = 0;
                while (iter.hasNext()) {
                    ProfileItem item = iter.next();
                    if (item.getType() == Type.PROFILE_HEADER || item.getType() == Type.PROFILE) {
                        iter.remove();
                        count++;
                    }
                }
                profileAdapter.notifyItemRangeRemoved(PREFERENCE_GROUP_COUNT, count);
            } else if (items.size() > 0 && profileAdapter.itemData.size() == PREFERENCE_GROUP_COUNT) {
                ProfileItem header = new ProfileItem("profile-header", Type.PROFILE_HEADER,
                    getString(R.string.header_label_profiles), null);

                profileAdapter.itemData.add(header);
                profileAdapter.notifyItemInserted(PREFERENCE_GROUP_COUNT);
            }

            Iterator<ProfileItem> iter = profileAdapter.itemData.iterator();
            int index = 0;
            while (iter.hasNext()) {
                ProfileItem item = iter.next();

                if (item.getType() == Type.PROFILE) {
                    int idx = items.indexOf(item);

                    if (idx != -1) {
                        if (items.get(idx).differs(item)) {
                            profileAdapter.itemData.set(index, items.get(idx));
                            profileAdapter.notifyItemChanged(index);
                        }
                        items.remove(item);
                    } else {
                        iter.remove();
                        profileAdapter.notifyItemRemoved(index--);
                    }
                }
                index++;
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

                if (item.getType() == Type.PROFILE_HEADER || item.getType() == Type.PROFILE) {
                    iter.remove();
                    count++;
                }
            }


            profileAdapter.notifyItemRangeRemoved(PREFERENCE_GROUP_COUNT, count);
        }

    };

    public boolean isPreferencesOpen() {
        Fragment f = getFragmentManager().findFragmentById(R.id.preference_panel);
        return f != null && f.isAdded();
    }

    public boolean isProfileOpen() {
        Fragment f = getFragmentManager().findFragmentById(R.id.preference_panel);
        return f != null && f.isAdded() && (
            f instanceof TransmissionProfileSettingsFragment
            || f instanceof  TransmissionProfileDirectoriesSettingsFragment
        );
    }

    public boolean isPreferencesAlwaysVisible() {
        return !slidingPane.isSlideable();
    }

    public void closePreferences() {
        if (!isPreferencesOpen()) {
            return;
        }

        profileAdapter.clearSelections();
        resetPreferencePane();

        if (!isPreferencesAlwaysVisible()) {
            slidingPane.openPane();
        }
    }

    @Override public void onBackPressed() {
        Fragment f = getFragmentManager().findFragmentById(R.id.preference_panel);

        if (isPreferencesOpen()) {
            if (f instanceof TransmissionProfileDirectoriesSettingsFragment) {
                String id = f.getArguments().getString(G.ARG_PROFILE_ID);

                if (id == null) {
                    id = "new-profile";
                }
                addFragment(id, Type.PROFILE, null);
            } else {
                closePreferences();
            }

            return;
        }

        super.onBackPressed();

        overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_top);
    }

    public void addFragment(String id, Type type, Bundle args) {
        Fragment fragment = null;

        switch (id) {
            case "general-preferences":
                fragment = new GeneralSettingsFragment();
                break;
            case "filter-preferences":
                fragment = new FiltersSettingsFragment();
                break;
            case "sort-preferences":
                fragment = new SortSettingsFragment();
                break;
            case "new-profile":
                fragment = new TransmissionProfileSettingsFragment();
                if (args == null) {
                    args = new Bundle();
                }
                break;
            default:
                switch (type) {
                    case PROFILE:
                        fragment = new TransmissionProfileSettingsFragment();
                        if (args == null) {
                            args = new Bundle();
                        }

                        args.putString(G.ARG_PROFILE_ID, id);

                        Intent intent = getIntent();
                        if (intent.hasExtra(G.ARG_PROFILE_ID)
                            && id.equals(intent.getStringExtra(G.ARG_PROFILE_ID))) {

                            args.putStringArrayList(G.ARG_DIRECTORIES,
                                intent.getStringArrayListExtra(G.ARG_DIRECTORIES));
                        }
                        break;
                    case PROFILE_DIRECTORIES:
                        fragment = new TransmissionProfileDirectoriesSettingsFragment();
                        if (args == null) {
                            args = new Bundle();
                        }
                        break;
                }
        }

        if (fragment != null) {
            if (args != null) {
                fragment.setArguments(args);
            }
        }

        if (fragment == null) {
            return;
        }

        String tag = type == Type.PROFILE_DIRECTORIES
            ? DIRECTORIES_FRAGMENT_TAG : PREFERENCE_FRAGMENT_TAG;

        findViewById(R.id.watermark).setVisibility(View.GONE);

        FragmentManager fm = getFragmentManager();
        Fragment previous = fm.findFragmentById(R.id.preference_panel);
        FragmentTransaction tx = fm.beginTransaction();

        if (previous != null) {
            tx.remove(previous);
        }


        tx.replace(R.id.preference_panel, fragment, tag).addToBackStack(null).commit();

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
        slidingPane.setSliderFadeColor(getResources().getColor(R.color.preference_background));
        slidingPane.setShadowResourceLeft(R.drawable.pane_shadow);
        slidingPane.setPanelSlideListener(new SlidingPaneLayout.SimplePanelSlideListener() {
            @Override
            public void onPanelOpened(View panel) {
                if (isPreferencesOpen()) {
                    resetPreferencePane();
                }
            }
        });

        slidingPane.openPane();

        profileAdapter = new ProfileAdapter(this);

        profileList = (RecyclerView) findViewById(R.id.profile_list);

        profileList.setLayoutManager(new LinearLayoutManager(this));
        profileList.setAdapter(profileAdapter);

        fillPreferences();

        getSupportLoaderManager().initLoader(G.PROFILES_LOADER_ID, null, profileLoaderCallbacks);

        boolean showNewProfile = getIntent().getBooleanExtra(G.ARG_NEW_PROFILE, false);

        if (state == null) {
            TransmissionProfile.cleanTemporaryPreferences(this);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.registerOnSharedPreferenceChangeListener(defaultPrefListener);

            prefs = getSharedPreferences(TransmissionProfile.getPreferencesName(),
                Activity.MODE_PRIVATE);
            prefs.registerOnSharedPreferenceChangeListener(profilesPrefListener);
        } else {
            showNewProfile = false;
        }

        if (showNewProfile) {
            addFragment("new-profile", Type.PROFILE, null);

            slidingPane.closePane();
        }
    }

    @Override protected void onResume() {
        super.onResume();

        FragmentManager fm = getFragmentManager();
        View watermark = findViewById(R.id.watermark);
        watermark.setVisibility(fm.findFragmentById(R.id.preference_panel) == null
            ? View.VISIBLE : View.GONE);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.add_profile_option, menu);

        return super.onCreateOptionsMenu(menu);
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                if (isProfileOpen()) {
                    Fragment f = getFragmentManager().findFragmentById(R.id.preference_panel);
                    if (f instanceof TransmissionProfileDirectoriesSettingsFragment) {
                        String id = f.getArguments().getString(G.ARG_PROFILE_ID);

                        if (id != null) {
                            addFragment(id, Type.PROFILE, null);

                            return true;
                        }
                    } else {
                        return false;
                    }
                }

                if (isPreferencesOpen()) {
                    closePreferences();
                    return true;
                }

                NavUtils.navigateUpTo(this, new Intent(this, TorrentListActivity.class));
                overridePendingTransition(android.R.anim.fade_in, R.anim.slide_out_top);
                return true;
            case R.id.add_profile:
                addFragment("new-profile", Type.PROFILE, null);

                slidingPane.closePane();

                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setSelectedItem(ProfileItem item) {
        if (item.getType() == Type.PROFILE_HEADER) {
            return;
        }

        addFragment(item.getId(), item.getType(), null);

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

        profileAdapter.itemData.add(2, item);

        profileAdapter.notifyItemRangeInserted(0, profileAdapter.itemData.size());
    }

    private ProfileItem createProfileItem(TransmissionProfile profile) {
        String sublabel = (profile.getUsername().length() > 0 ? profile.getUsername() + "@" : "")
                + profile.getHost() + ":" + profile.getPort();

        ProfileItem item = new ProfileItem(profile.getId(), Type.PROFILE,
            profile.getName(), sublabel);
        item.setColor(profile.getColor());

        return item;
    }

    private void resetPreferencePane() {
        findViewById(R.id.watermark).setVisibility(View.VISIBLE);

        FragmentManager fm = getFragmentManager();
        FragmentTransaction transaction = fm.beginTransaction();
        transaction.remove(fm.findFragmentById(R.id.preference_panel));
        transaction.commit();
        fm.executePendingTransactions();

    }

    private static class ProfileAdapter extends SelectableRecyclerViewAdapter<ProfileAdapter.ViewHolder, ProfileItem> {
        private SettingsActivity context;

        public ProfileAdapter(SettingsActivity context) {
            super();
            this.context = context;
        }

        @Override public boolean isItemSelectable(int position) {
            if (itemData.size() <= position) {
                return false;
            }

            ProfileItem item = itemData.get(position);
            if (item.getType() == Type.PROFILE_HEADER) {
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
                    clearSelections();
                    setItemSelected(position, true);
                    context.setSelectedItem(item);
                }
            });

            holder.label.setText(item.getLabel());
            /* Enable the marquee animation */
            holder.label.setSelected(true);
            if (holder.sublabel != null) {
                holder.sublabel.setText(item.getSublabel());
                holder.sublabel.setSelected(true);
            }

            if (holder.color != null) {
                Colorizer.colorizeView(holder.color, item.getColor() == null ?
                                Colorizer.defaultColor(context) :
                                item.getColor(),
                        GradientDrawable.OVAL
                );
            }
        }

        @Override public long getItemId(int position) {
            return this.itemData.get(position).hashCode();
        }

        @Override public int getItemViewType(int position) {
            ProfileItem item = itemData.get(position);
            switch (item.getType()) {
                case PROFILE_HEADER:
                    return R.layout.settings_profile_header;
                case PREFERENCES:
                    return R.layout.settings_preference_item;
                default:
                    return R.layout.settings_profile_item;
            }
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            public TextView label;
            public TextView sublabel;
            public ImageView color;

            public ViewHolder(View itemView, int type) {
                super(itemView);

                label = (TextView) itemView.findViewById(android.R.id.text1);
                sublabel = (TextView) itemView.findViewById(android.R.id.text2);
                color = (ImageView) itemView.findViewById(R.id.profile_color);
            }
        }
    }

    private class ProfileItem {
        private String id;
        private Type type;
        private String label;
        private String sublabel;
        private Integer color = null;

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

        public Integer getColor() {
            return color;
        }

        public void setColor(int color) {
            this.color = color;
        }

        public boolean differs(ProfileItem o) {
            if (!this.equals(o)) {
                return false;
            }

            if (!label.equals(o.label) || !sublabel.equals(o.sublabel)) {
                return true;
            }

            return false;
        }

        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ProfileItem that = (ProfileItem) o;

            if (!id.equals(that.id)) return false;
            if (type != that.type) return false;

            return true;
        }

        @Override public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + type.hashCode();
            result = 31 * result + label.hashCode();
            result = 31 * result + (sublabel != null ? sublabel.hashCode() : 0);
            return result;
        }
    }
}
