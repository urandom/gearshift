package org.sugr.gearshift;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.datasource.TorrentStatus;

import java.io.File;
import java.net.ConnectException;

public class DataService extends IntentService {
    public static final class Requests {
        public static final String GET_SESSION = "get_session";
        public static final String SET_SESSION = "set_session";

        public static final String GET_ALL_TORRENTS = "get_all_torrents";
        public static final String GET_ACTIVE_TORRENTS = "get_active_torrents";
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
        public static final String SESSION = "session";
        public static final String SESSION_FIELDS = "session_fields";
        public static final String DETAIL_FIELDS = "detail_fields";
        public static final String TORRENTS_TO_UPDATE = "torrents_to_update";
        public static final String REMOVE_OBSOLETE = "remove_obsolete";
        public static final String MAGNET_URI = "magnet_uri";
        public static final String TORRENT_DATA = "torrent_data";
        public static final String LOCATION = "location";
        public static final String MOVE_DATA = "move_data";
        public static final String ADD_PAUSED = "add_paused";
        public static final String TEMPORARY_FILE = "temporary_file";
        public static final String HASH_STRINGS = "hash_strings";
        public static final String DELETE_DATA = "delete_data";
        public static final String TORRENT_FIELD = "torrent_field";
        public static final String TORRENT_FIELD_VALUE = "torrent_field_value";
        public static final String TORRENT_ACTION = "torrent_action";
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
        Intent response = null;

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
                throw new ConnectException();
            }

            if (requestType.equals(Requests.GET_SESSION)) {
                manager.updateSession();
                response = createResponse(requestType, profileId);
            } else if (requestType.equals(Requests.SET_SESSION)) {
                TransmissionSession session = args.getParcelable(Args.SESSION);
                String[] keys = args.getStringArray(Args.SESSION_FIELDS);

                if (session == null) {
                    throw new IllegalArgumentException("No session object given");
                }
                if (keys == null || keys.length == 0) {
                    throw new IllegalArgumentException("No session fields given");
                }

                manager.setSession(session, keys);
                response = createResponse(requestType, profileId);
            } else if (requestType.equals(Requests.GET_ALL_TORRENTS)
                    || requestType.equals(Requests.GET_ACTIVE_TORRENTS)) {
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
                } else if (requestType.equals(Requests.GET_ACTIVE_TORRENTS)
                        && !args.getBoolean(Args.DETAIL_FIELDS, false)) {
                    status = manager.getActiveTorrents(fields);
                } else {
                    status = manager.getTorrents(fields, null,
                        args.getBoolean(Args.REMOVE_OBSOLETE, false));
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
            } else if (requestType.equals(Requests.ADD_TORRENT)) {
                String uri = args.getString(Args.MAGNET_URI);
                String data = args.getString(Args.TORRENT_DATA);
                String location = args.getString(Args.LOCATION);
                boolean paused = args.getBoolean(Args.ADD_PAUSED, false);
                String temporary = args.getString(Args.TEMPORARY_FILE);

                if (TextUtils.isEmpty(uri) || TextUtils.isEmpty(data)) {
                    throw new IllegalArgumentException(
                        "Either a uri or the torrent data needs to be specified");
                }

                String addedHash = manager.addTorrent(uri, data, location, paused);

                if (!TextUtils.isEmpty(temporary)) {
                    File file = new File(temporary);
                    if (!file.delete()) {
                        G.logD("Couldn't remove torrent " + file.getName());
                    }
                }

                response = createResponse(requestType, profileId)
                    .putExtra(G.ARG_ADDED_HASH, addedHash);
            } else if (requestType.equals(Requests.REMOVE_TORRENTS)) {
                String[] hashStrings = args.getStringArray(Args.HASH_STRINGS);
                boolean delete = args.getBoolean(Args.DELETE_DATA, false);

                if (hashStrings == null || hashStrings.length == 0) {
                    throw new IllegalArgumentException("No hash strings provided");
                }

                manager.setTorrentsRemove(hashStrings, delete);
                response = createResponse(requestType, profileId);
            } else if (requestType.equals(Requests.SET_TORRENT)) {
                String[] hashStrings = args.getStringArray(Args.HASH_STRINGS);
                String field = args.getString(Args.TORRENT_FIELD);
                Object value = args.get(Args.TORRENT_FIELD_VALUE);

                if (hashStrings == null || hashStrings.length == 0) {
                    throw new IllegalArgumentException("No hash strings provided");
                }

                if (TextUtils.isEmpty(field)) {
                    throw new IllegalArgumentException("No torrent field provided");
                }

                manager.setTorrentsProperty(hashStrings, field, value);
                response = createResponse(requestType, profileId);
            } else if (requestType.equals(Requests.SET_TORRENT_LOCATION)) {
                String[] hashStrings = args.getStringArray(Args.HASH_STRINGS);
                String location = args.getString(Args.LOCATION);
                boolean move = args.getBoolean(Args.MOVE_DATA, false);

                if (hashStrings == null || hashStrings.length == 0) {
                    throw new IllegalArgumentException("No hash strings provided");
                }

                if (TextUtils.isEmpty(location)) {
                    throw new IllegalArgumentException("No torrent location provided");
                }

                manager.setTorrentsLocation(hashStrings, location, move);
                response = createResponse(requestType, profileId);
            } else if (requestType.equals(Requests.SET_TORRENT_ACTION)) {
                String[] hashStrings = args.getStringArray(Args.HASH_STRINGS);
                String action = args.getString(Args.TORRENT_ACTION);

                if (hashStrings == null || hashStrings.length == 0) {
                    throw new IllegalArgumentException("No hash strings provided");
                }

                if (TextUtils.isEmpty(action)) {
                    throw new IllegalArgumentException("No action provided");
                }

                manager.setTorrentsAction(hashStrings, action);
                response = createResponse(requestType, profileId);
            } else if (requestType.equals(Requests.CLEAR_TORRENTS_FOR_PROFILE)) {
                manager.clearTorrents();
                response = createResponse(requestType, profileId);
            } else if (requestType.equals(Requests.GET_FREE_SPACE)) {
                String location = args.getString(Args.LOCATION);

                if (TextUtils.isEmpty(location)) {
                    throw new IllegalArgumentException("No torrent location provided");
                }

                long freeSpace = manager.getFreeSpace(location);
                response = createResponse(requestType, profileId)
                    .putExtra(G.ARG_FREE_SPACE, freeSpace);
            } else if (requestType.equals(Requests.TEST_PORT)) {
                boolean isOpen = manager.testPort();
                response = createResponse(requestType, profileId)
                    .putExtra(G.ARG_PORT_IS_OPEN, isOpen);
            } else if (requestType.equals(Requests.BLOCKLIST_UPDATE)) {
                long size = manager.blocklistUpdate();
                response = createResponse(requestType, profileId)
                    .putExtra(G.ARG_BLOCKLIST_SIZE, size);
            } else {
                throw new IllegalArgumentException("Invalid request type");
            }

        } catch (IllegalArgumentException e) {
            G.logE("Error while processing service request!", e);
            /* TODO send an 'error' broadcast */
        } catch (TransmissionSessionManager.ManagerException e) {
            G.logE("Error while processing service request!", e);
            response = handleError(e, requestType, profileId);
        } catch (ConnectException e) {
            response = createResponse(requestType, profileId)
                .putExtra(G.ARG_ERROR, TransmissionData.Errors.NO_CONNECTIVITY);
        } finally {
            dataSource.close();

            if (response != null) {
                LocalBroadcastManager.getInstance(this).sendBroadcast(response);
            }
        }
    }

    private Intent createResponse(String requestType, String profileId) {
        return new Intent(G.INTENT_SERVICE_ACTION_COMPLETE)
            .putExtra(G.ARG_REQUEST_TYPE, requestType)
            .putExtra(G.ARG_PROFILE_ID, profileId);
    }

    private Intent handleError(TransmissionSessionManager.ManagerException e,
                                         String requestType, String profileId) {

        Intent intent = createResponse(requestType, profileId);
        G.logD("Got an error while fetching data: " + e.getMessage() + " and this code: " + e.getCode());

        int error;
        switch(e.getCode()) {
            case 401:
            case 403:
                error = TransmissionData.Errors.ACCESS_DENIED;
                break;
            case 200:
                if (e.getMessage().equals("no-json")) {
                    error = TransmissionData.Errors.NO_JSON;
                } else {
                    return null;
                }
                break;
            case -1:
                if (e.getMessage().equals("timeout")) {
                    error = TransmissionData.Errors.TIMEOUT;
                } else {
                    error = TransmissionData.Errors.NO_CONNECTION;
                }
                break;
            case -2:
                if (e.getMessage().equals("duplicate torrent")) {
                    error = TransmissionData.Errors.DUPLICATE_TORRENT;
                } else if (e.getMessage().equals("invalid or corrupt torrent file")) {
                    error = TransmissionData.Errors.INVALID_TORRENT;
                } else {
                    error = TransmissionData.Errors.RESPONSE_ERROR;
                    G.logE("Transmission Daemon Error!", e);
                }
                break;
            case -3:
                error = TransmissionData.Errors.OUT_OF_MEMORY;
                break;
            case -4:
                error = TransmissionData.Errors.JSON_PARSE_ERROR;
                G.logE("JSON parse error!", e);
                break;
            default:
                error = TransmissionData.Errors.GENERIC_HTTP;
                break;
        }

        return intent.putExtra(G.ARG_ERROR, error).putExtra(G.ARG_ERROR_CODE, e.getCode());
    }
}
