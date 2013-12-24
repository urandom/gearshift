package org.sugr.gearshift;

import android.app.backup.BackupManager;
import android.content.Context;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.DecimalFormat;
import java.util.Comparator;

public final class G {
    public static final String PREF_DEBUG = "debug";

    public static final String PREF_UPDATE_ACTIVE = "update_active_torrents";
    public static final String PREF_FULL_UPDATE = "full_update";
    public static final String PREF_UPDATE_INTERVAL = "update_interval";
    public static final String PREF_SHOW_STATUS = "show_status";

    public static final String PREF_PROFILES = "profiles";
    public static final String PREF_CURRENT_PROFILE = "default_profile";

    public static final String PREF_NAME = "profile_name";
    public static final String PREF_HOST = "profile_host";
    public static final String PREF_PORT = "profile_port";
    public static final String PREF_PATH = "profile_path";
    public static final String PREF_USER = "profile_username";
    public static final String PREF_PASS = "profile_password";
    public static final String PREF_SSL = "profile_use_ssl";
    public static final String PREF_TIMEOUT = "profile_timeout";
    public static final String PREF_RETRIES = "profile_retries";
    public static final String PREF_DIRECTORIES = "profile_directories";
    public static final String PREF_LAST_DIRECTORY = "profile_last_directory";
    public static final String PREF_MOVE_DATA = "profile_move_data";
    public static final String PREF_DELETE_LOCAL = "profile_delete_local";
    public static final String PREF_START_PAUSED = "profile_start_paused";
    public static final String PREF_PREFIX = "profile_";

    public static final String PREF_LIST_SORT_BY = "torrents_sort_by";
    public static final String PREF_LIST_SORT_ORDER = "torrents_sort_order";
    public static final String PREF_LIST_FILTER = "torrents_filter";
    public static final String PREF_LIST_DIRECTORY = "torrents_directory";
    public static final String PREF_LIST_TRACKER = "torrents_tracker";
    public static final String PREF_LIST_SEARCH = "torrents_search";

    public static final String PREF_FILTER_ALL = "filter_all";

    public static final String PREF_FILTER_DOWNLOADING = "filter_downloading";
    public static final String PREF_FILTER_SEEDING = "filter_seeding";
    public static final String PREF_FILTER_PAUSED = "filter_paused";
    public static final String PREF_FILTER_COMPLETE = "filter_complete";
    public static final String PREF_FILTER_INCOMPLETE = "filter_incomplete";
    public static final String PREF_FILTER_ACTIVE = "filter_active";
    public static final String PREF_FILTER_CHECKING = "filter_checking";
    public static final String PREF_FILTER_DIRECTORIES = "filter_directories";
    public static final String PREF_FILTER_TRACKERS = "filter_trackers";

    public static final String PREF_SORT_NAME = "sort_name";
    public static final String PREF_SORT_SIZE = "sort_size";
    public static final String PREF_SORT_STATUS = "sort_status";
    public static final String PREF_SORT_ACTIVITY = "sort_activity";
    public static final String PREF_SORT_AGE = "sort_age";
    public static final String PREF_SORT_PROGRESS = "sort_progress";
    public static final String PREF_SORT_RATIO = "sort_ratio";
    public static final String PREF_SORT_LOCATION = "sort_location";
    public static final String PREF_SORT_PEERS = "sort_peers";
    public static final String PREF_SORT_RATE_DOWNLOAD = "sort_rate_download";
    public static final String PREF_SORT_RATE_UPLOAD = "sort_rate_upload";
    public static final String PREF_SORT_QUEUE = "sort_queue";

    public static final String PREF_BASE_SORT = "base_sort";
    public static final String PREF_BASE_SORT_ORDER = "base_sort_order";

    public static final String PREF_FILTER_MATCH_TEST = "^(?:filter_|sort_).+$";

    public static final String ARG_SESSION = "session";
    public static final String ARG_PROFILE = "profile";
    public static final String ARG_PROFILE_ID = "profile_id";
    public static final String ARG_DIRECTORIES = "directories";
    public static final String ARG_PAGE_POSITION = "page_position";
    public static final String ARG_TORRENT_INDEX = "torrent_index";

    public static final String DETAIL_FRAGMENT_TAG = "detail_fragment";

    public static final String PROFILES_PREF_NAME = "profiles";

    public static final String INTENT_TORRENT_UPDATE = "org.sugr.gearshift.TORRENT_UPDATE";
    public static final String INTENT_PAGE_UNSELECTED = "org.sugr.gearshift.PAGE_UNSELECTED";

    public static final int PROFILES_LOADER_ID = 1;
    public static final int TORRENTS_LOADER_ID = 2;
    public static final int SESSION_LOADER_ID = 3;
    public static final int FILTER_MENU_LOADER_ID = 3;

    private static final String LogTag = "GearShift";

    public static enum FilterBy {
        ALL, DOWNLOADING, SEEDING, PAUSED, COMPLETE, INCOMPLETE,
        ACTIVE, CHECKING
    }

    public static enum SortBy {
        NAME, SIZE, STATUS, RATE_DOWNLOAD, RATE_UPLOAD, AGE,
        PROGRESS, RATIO, ACTIVITY, LOCATION, PEERS, QUEUE
    }

    public static enum SortOrder {
        ASCENDING, DESCENDING
    }

    public static Comparator<String> SIMPLE_STRING_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String lhs, String rhs) {
            if (lhs == null && rhs == null) {
                return 0;
            } else if (lhs == null) {
                return -1;
            } else if (rhs == null) {
                return 1;
            } else {
                return lhs.compareToIgnoreCase(rhs);
            }
        }

    };

    public static boolean DEBUG = false;

    public static void logE(String message, Object[] args, Exception e) {
        Log.e(LogTag, String.format(message, args), e);
    }

    public static void logE(String message, Exception e) {
        Log.e(LogTag, message, e);
    }

    public static void logD(String message, Object[] args) {
        if (!DEBUG) return;

        Log.d(LogTag, String.format(message, args));
    }

    public static void logD(String message) {
        if (!DEBUG) return;

        Log.d(LogTag, message);
    }

    public static void logDTrace() {
        if (!DEBUG) return;

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        Throwable t = new Throwable();

        t.printStackTrace(pw);
        Log.d(LogTag, sw.toString());
    }


    public static String readableFileSize(long size) {
        if(size <= 0) return "0 B";
        final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
        int digitGroups = (int) (Math.log10(size)/Math.log10(1024));
        float scaledSize = size / (float) Math.pow(1024, digitGroups);
        if (scaledSize < 100) {
            return new DecimalFormat("#,##0.##").format(scaledSize) + " " + units[digitGroups];
        } else {
            return new DecimalFormat("#,##0.#").format(scaledSize) + " " + units[digitGroups];
        }
    }

    public static String readablePercent(float percent) {
        if (percent < 10.0) {
            return new DecimalFormat("#.##").format(percent);
        } else if (percent < 100.0) {
            return new DecimalFormat("#.#").format(percent);
        } else {
            return new DecimalFormat("#").format(percent);
        }
    }

    public static String readableRemainingTime(long eta, Context context) {
        if (eta < 0) {
            return context.getString(R.string.traffic_remaining_time_unknown);
        }
        int days = (int) Math.floor(eta / 86400);
        int hours = (int) Math.floor((eta % 86400) / 3600);
        int minutes = (int) Math.floor((eta % 3600) / 60);
        int seconds = (int) Math.floor(eta % 60);
        String d = Integer.toString(days) + ' ' + context.getString(days > 1 ? R.string.time_days : R.string.time_day);
        String h = Integer.toString(hours) + ' ' + context.getString(hours > 1 ? R.string.time_hours : R.string.time_hour);
        String m = Integer.toString(minutes) + ' ' + context.getString(minutes > 1 ? R.string.time_minutes : R.string.time_minute);
        String s = Integer.toString(seconds) + ' ' + context.getString(seconds > 1 ? R.string.time_seconds : R.string.time_second);

        if (days > 0) {
            if (days >= 4 || hours == 0)
                return d;
            return d + ", " + h;
        }

        if (hours > 0) {
            if (hours >= 4 || minutes == 0)
                return h;
            return h + ", " + m;
        }

        if (minutes > 0) {
            if (minutes >= 4 || seconds == 0)
                return m;
            return m + ", " + s;
        }

        return s;
    }

    public static void requestBackup(Context context) {
        BackupManager bm = new BackupManager(context);
        bm.dataChanged();
    }

    public static String[] concat(String[]... arrays) {
        int len = 0;
        for (final String[] array : arrays) {
            len += array.length;
        }

        final String[] result = new String[len];

        int currentPos = 0;
        for (final String[] array : arrays) {
            System.arraycopy(array, 0, result, currentPos, array.length);
            currentPos += array.length;
        }

        return result;
    }
}
