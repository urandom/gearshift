package org.sugr.gearshift;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;

import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.datasource.TorrentStatus;

public class DataService extends IntentService {
    public static final class Requests {
        public static final String GET_SESSION = "get_session";
        public static final String SET_SESSION = "set_session";

        public static final String ALL_TORRENTS = "all_torrents";
        public static final String ACTIVE_TORRENTS = "current_torrents";
        public static final String ADD_TORRENT = "add_torrent";
        public static final String REMOVE_TORRENTS = "remove_torrents";
        public static final String SET_TORRENT = "set_torrent";
        public static final String SET_TORRENT_LOCATION = "set_torrent_location";
        public static final String SET_TORRENT_ACTION = "set_torrent_action";
        public static final String CLEAR_TORRENTS_FOR_PROFILE = "clear_torrents_for_profile";

        public static final String GET_FREE_SPACE = "get_free_space";
        public static final String TEST_PORT = "test_port";
        public static final String BLOCKLIST_UPDATE = "blocklist_update";
    }

    public static final class Args {
        public static final String DETAIL_FIELDS = "detail_fields";
        public static final String TORRENTS_TO_UPDATE = "torrents_to_update";
        public static final String REMOVE_OBSOLETE = "remove_obsolete";
    }

    private DataSource dataSource;
    private TransmissionSessionManager manager;
    private TransmissionProfile profile;

    public DataService() {
        super("DataService");

        dataSource = new DataSource(this);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String packageName = getPackageName();
        String profileId = intent.getStringExtra(packageName + G.ARG_PROFILE_ID);
        String requestType = intent.getStringExtra(packageName + G.ARG_REQUEST_TYPE);
        Bundle args = intent.getBundleExtra(packageName + G.ARG_REQUEST_ARGS);

        dataSource.open();
        try {
            if (requestType == null) {
                throw new IllegalArgumentException("Invalid request type");
            }
            if (profileId == null) {
                throw new IllegalArgumentException("No profile specified");
            } else if (profile == null || !profile.getId().equals(profileId)) {
                profile = new TransmissionProfile(profileId, this);
                if (profile.getHost().equals("")) {
                    throw new IllegalArgumentException("No profile specified");
                }
                if (manager != null) {
                    manager.setProfile(profile);
                }
            }
            if (args == null) {
                throw new IllegalArgumentException("No arguments bundle");
            }

            if (manager == null) {
                manager = new TransmissionSessionManager(this, profile, dataSource);
            }

            if (!manager.hasConnectivity()) {
                /* TODO: send and 'error' broadcast */
            }

            Intent response;
            if (requestType.equals(Requests.ALL_TORRENTS) || requestType.equals(Requests.ACTIVE_TORRENTS)) {
                TorrentStatus status;
                String[] fields = Torrent.Fields.STATS;

                if (!dataSource.hasCompleteMetadata()) {
                    fields = G.concat(Torrent.Fields.METADATA, fields);
                }

                if (args.getBoolean(Args.DETAIL_FIELDS, false)) {
                    fields = G.concat(fields, Torrent.Fields.STATS_EXTRA);
                    if (!dataSource.hasExtraInfo()) {
                        fields = G.concat(fields, Torrent.Fields.INFO_EXTRA);
                    }
                }

                String[] hashStrings = args.getStringArray(Args.TORRENTS_TO_UPDATE);
                if (hashStrings != null) {
                    status = manager.getTorrents(fields, hashStrings, false);
                } else if (requestType.equals(Requests.ACTIVE_TORRENTS) && !args.getBoolean(Args.DETAIL_FIELDS, false)) {
                    status = manager.getActiveTorrents(fields);
                } else {
                    status = manager.getTorrents(fields, null, args.getBoolean(Args.REMOVE_OBSOLETE, false));
                }

                String[] unnamed = dataSource.getUnnamedTorrentHashStrings();
                if (unnamed != null && unnamed.length > 0) {
                    manager.getTorrents(
                        G.concat(new String[] {Torrent.Fields.hashString}, Torrent.Fields.METADATA),
                        unnamed, false);
                }

                response = createResponse(requestType, profileId)
                    .putExtra(G.ARG_ADDED, status.hasAdded)
                    .putExtra(G.ARG_REMOVED, status.hasRemoved)
                    .putExtra(G.ARG_STATUS_CHANGED, status.hasStatusChanged)
                    .putExtra(G.ARG_INCOMPLETE_METADATA, status.hasIncompleteMetadata);
            }  else if (requestType.equals(Requests.CLEAR_TORRENTS_FOR_PROFILE)) {
                manager.clearTorrents();
                response = createResponse(requestType, profileId);
            } else {
                throw new IllegalArgumentException("Invalid request type");
            }

            LocalBroadcastManager.getInstance(this).sendBroadcast(response);
        } catch (IllegalArgumentException e) {
            G.logE("Error while processing service request!", e);
            /* TODO send an 'error' broadcast */
        } catch (TransmissionSessionManager.ManagerException e) {
            G.logE("Error while processing service request!", e);
            /* TODO send an 'error' broadcast */
        } finally {
            dataSource.close();
        }
    }

    private Intent createResponse(String type, String profileId) {
        return new Intent(G.INTENT_SERVICE_ACTION_COMPLETE)
            .putExtra(G.ARG_REQUEST_TYPE, type)
            .putExtra(G.ARG_PROFILE_ID, profileId);
    }
}
