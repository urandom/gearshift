package org.sugr.gearshift.service;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import org.sugr.gearshift.G;
import org.sugr.gearshift.core.Torrent;
import org.sugr.gearshift.core.TransmissionProfile;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.datasource.TorrentStatus;

import java.io.File;
import java.net.ConnectException;

public class DataService extends IntentService {
    public static final class Requests {
        public static final String REMOVE_PROFILE = "remove_profile";

        public static final String GET_SESSION = "get_session";
        public static final String SET_SESSION = "set_session";

        public static final String GET_TORRENTS = "get_torrents";
        public static final String ADD_TORRENT = "add_torrent";
        public static final String REMOVE_TORRENT = "remove_torrents";
        public static final String SET_TORRENT = "set_torrent";
        public static final String SET_TORRENT_LOCATION = "set_torrent_location";
        public static final String SET_TORRENT_ACTION = "set_torrent_action";

        public static final String GET_FREE_SPACE = "get_free_space";
        public static final String TEST_PORT = "test_port";
        public static final String UPDATE_BLOCKLIST = "blocklist_update";
    }

    public static final class Args {
        public static final String SESSION = "session";
        public static final String SESSION_FIELDS = "session_fields";
        public static final String ALL_TORRENT_FIELDS = "all_torrent_fields";
        public static final String DETAIL_FIELDS = "detail_fields";
        public static final String UPDATE_ACTIVE = "update_active";
        public static final String TORRENTS_TO_UPDATE = "torrents_to_update";
        public static final String REMOVE_OBSOLETE = "remove_obsolete";
        public static final String MAGNET_URI = "magnet_uri";
        public static final String TORRENT_DATA = "torrent_data";
        public static final String LOCATION = "location";
        public static final String MOVE_DATA = "move_data";
        public static final String ADD_PAUSED = "add_paused";
        public static final String TEMPORARY_FILE = "temporary_file";
        public static final String DOCUMENT_URI = "document_uri";
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
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    protected void onHandleIntent(Intent intent) {
        String profileId = intent.getStringExtra(G.ARG_PROFILE_ID);
        String requestType = intent.getStringExtra(G.ARG_REQUEST_TYPE);
        Bundle args = intent.getBundleExtra(G.ARG_REQUEST_ARGS);
        Intent response = null;

        if (dataSource == null) {
            dataSource = new DataSource(this);
        }

        dataSource.open();
        try {
            if (requestType == null) {
                throw new IllegalArgumentException("Invalid request type");
            }
            if (profileId == null) {
                throw new IllegalArgumentException("No profile specified");
            }
            if (args == null) {
                throw new IllegalArgumentException("No arguments bundle");
            }
            if (profile == null || !profile.getId().equals(profileId)) {
                profile = new TransmissionProfile(profileId, this,
                    PreferenceManager.getDefaultSharedPreferences(this));
            }

            if (requestType.equals(Requests.REMOVE_PROFILE)) {
                profile.delete();
                dataSource.clearTorrentsForProfile(profileId);
                response = createResponse(requestType, profileId);
            } else {
                if (profile.getHost().equals("")) {
                    throw new IllegalArgumentException("No profile specified");
                }
                if (manager != null) {
                    manager.setProfile(profile);
                }
                if (manager == null) {
                    manager = new TransmissionSessionManager(this, profile, dataSource);
                }

                if (!manager.hasConnectivity()) {
                    throw new ConnectException();
                }

                switch (requestType) {
                    case Requests.GET_SESSION:
                        manager.updateSession();
                        response = createResponse(requestType, profileId);
                        break;
                    case Requests.SET_SESSION:
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
                        break;
                    case Requests.GET_TORRENTS: {
                        TorrentStatus status;
                        String[] fields = Torrent.Fields.STATS;

                        if (args.getBoolean(Args.ALL_TORRENT_FIELDS, false)) {
                            fields = G.concat(fields, Torrent.Fields.METADATA,
                                Torrent.Fields.STATS_EXTRA,
                                Torrent.Fields.INFO_EXTRA);
                        } else {
                            if (!dataSource.hasCompleteMetadata(profileId)) {
                                fields = G.concat(Torrent.Fields.METADATA, fields);
                            }

                            if (args.getBoolean(Args.DETAIL_FIELDS, false)) {
                                fields = G.concat(fields, Torrent.Fields.STATS_EXTRA);
                                if (!dataSource.hasExtraInfo(profileId)) {
                                    fields = G.concat(fields, Torrent.Fields.INFO_EXTRA);
                                }
                            }
                        }

                        String[] hashStrings = args.getStringArray(Args.TORRENTS_TO_UPDATE);
                        if (hashStrings != null) {
                            status = manager.getTorrents(fields, hashStrings, false);
                        } else if (args.getBoolean(Args.UPDATE_ACTIVE, false)
                            && !args.getBoolean(Args.DETAIL_FIELDS, false)) {
                            status = manager.getActiveTorrents(fields);
                        } else {
                            status = manager.getTorrents(fields, null,
                                args.getBoolean(Args.REMOVE_OBSOLETE, false));
                        }

                        String[] unnamed = dataSource.getUnnamedTorrentHashStrings(profileId);
                        if (unnamed != null && unnamed.length > 0) {
                            manager.getTorrents(
                                G.concat(new String[]{Torrent.Fields.hashString}, Torrent.Fields.METADATA),
                                unnamed, false);
                        }

                        response = createResponse(requestType, profileId)
                            .putExtra(G.ARG_ADDED, status.hasAdded)
                            .putExtra(G.ARG_REMOVED, status.hasRemoved)
                            .putExtra(G.ARG_STATUS_CHANGED, status.hasStatusChanged)
                            .putExtra(G.ARG_INCOMPLETE_METADATA, status.hasIncompleteMetadata);
                        break;
                    }
                    case Requests.ADD_TORRENT: {
                        String uri = args.getString(Args.MAGNET_URI);
                        String data = args.getString(Args.TORRENT_DATA);
                        String location = args.getString(Args.LOCATION);
                        boolean paused = args.getBoolean(Args.ADD_PAUSED, false);
                        String temporary = args.getString(Args.TEMPORARY_FILE);
                        Uri document = args.getParcelable(Args.DOCUMENT_URI);

                        if (TextUtils.isEmpty(uri) && TextUtils.isEmpty(data)) {
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
                        if (document != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            if (!DocumentsContract.deleteDocument(getContentResolver(), document)) {
                                G.logD("Couldn't remove torrent " + document.toString());
                            }
                        }

                        profile.setLastDownloadDirectory(location);
                        profile.setDeleteLocal(temporary != null || document != null);
                        profile.setStartPaused(paused);

                        response = createResponse(requestType, profileId)
                            .putExtra(G.ARG_ADDED_HASH, addedHash);
                        break;
                    }
                    case Requests.REMOVE_TORRENT: {
                        String[] hashStrings = args.getStringArray(Args.HASH_STRINGS);
                        boolean delete = args.getBoolean(Args.DELETE_DATA, false);

                        if (hashStrings == null || hashStrings.length == 0) {
                            throw new IllegalArgumentException("No hash strings provided");
                        }

                        manager.removeTorrent(hashStrings, delete);
                        response = createResponse(requestType, profileId);
                        break;
                    }
                    case Requests.SET_TORRENT: {
                        String[] hashStrings = args.getStringArray(Args.HASH_STRINGS);
                        String field = args.getString(Args.TORRENT_FIELD);
                        Object value = args.get(Args.TORRENT_FIELD_VALUE);

                        if (hashStrings == null || hashStrings.length == 0) {
                            throw new IllegalArgumentException("No hash strings provided");
                        }

                        if (TextUtils.isEmpty(field)) {
                            throw new IllegalArgumentException("No torrent field provided");
                        }

                        manager.setTorrentProperty(hashStrings, field, value);
                        response = createResponse(requestType, profileId)
                            .putExtra(G.ARG_TORRENT_FIELD, field);
                        break;
                    }
                    case Requests.SET_TORRENT_LOCATION: {
                        String[] hashStrings = args.getStringArray(Args.HASH_STRINGS);
                        String location = args.getString(Args.LOCATION);
                        boolean move = args.getBoolean(Args.MOVE_DATA, false);

                        if (hashStrings == null || hashStrings.length == 0) {
                            throw new IllegalArgumentException("No hash strings provided");
                        }

                        if (TextUtils.isEmpty(location)) {
                            throw new IllegalArgumentException("No torrent location provided");
                        }

                        manager.setTorrentLocation(hashStrings, location, move);

                        profile.setLastDownloadDirectory(location);
                        profile.setMoveData(move);

                        response = createResponse(requestType, profileId);
                        break;
                    }
                    case Requests.SET_TORRENT_ACTION: {
                        String[] hashStrings = args.getStringArray(Args.HASH_STRINGS);
                        String action = args.getString(Args.TORRENT_ACTION);

                        if (hashStrings == null || hashStrings.length == 0) {
                            throw new IllegalArgumentException("No hash strings provided");
                        }

                        if (TextUtils.isEmpty(action)) {
                            throw new IllegalArgumentException("No action provided");
                        }

                        manager.setTorrentAction(hashStrings, action);
                        response = createResponse(requestType, profileId);
                        break;
                    }
                    case Requests.GET_FREE_SPACE: {
                        String location = args.getString(Args.LOCATION);

                        if (TextUtils.isEmpty(location)) {
                            throw new IllegalArgumentException("No torrent location provided");
                        }

                        long freeSpace = manager.getFreeSpace(location);
                        response = createResponse(requestType, profileId)
                            .putExtra(G.ARG_FREE_SPACE, freeSpace);
                        break;
                    }
                    case Requests.TEST_PORT:
                        boolean isOpen = manager.testPort();
                        response = createResponse(requestType, profileId)
                            .putExtra(G.ARG_PORT_IS_OPEN, isOpen);
                        break;
                    case Requests.UPDATE_BLOCKLIST:
                        long size = manager.updateBlocklist();
                        response = createResponse(requestType, profileId)
                            .putExtra(G.ARG_BLOCKLIST_SIZE, size);
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid request type");
                }
            }
        } catch (IllegalArgumentException e) {
            G.logE("Error while processing service request!", e);
        } catch (TransmissionSessionManager.ManagerException e) {
            G.logE("Error while processing service request!", e);
            response = handleError(e, requestType, profileId);
        } catch (ConnectException e) {
            response = createResponse(requestType, profileId)
                .putExtra(G.ARG_ERROR, Errors.NO_CONNECTIVITY);
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
                error = Errors.ACCESS_DENIED;
                break;
            case 200:
                if (e.getMessage().equals("no-json")) {
                    error = Errors.NO_JSON;
                } else {
                    return null;
                }
                break;
            case -1:
                if (e.getMessage().equals("timeout")) {
                    error = Errors.TIMEOUT;
                } else {
                    error = Errors.NO_CONNECTION;
                }
                break;
            case -2:
                if (e.getMessage().equals("duplicate torrent")) {
                    error = Errors.DUPLICATE_TORRENT;
                } else if (e.getMessage().equals("invalid or corrupt torrent file")) {
                    error = Errors.INVALID_TORRENT;
                } else {
                    error = Errors.RESPONSE_ERROR;
                    G.logE("Transmission Daemon Error!", e);
                }
                break;
            case -3:
                error = Errors.OUT_OF_MEMORY;
                break;
            case -4:
                error = Errors.JSON_PARSE_ERROR;
                G.logE("JSON parse error!", e);
                break;
            default:
                error = Errors.GENERIC_HTTP;
                break;
        }

        return intent.putExtra(G.ARG_ERROR, error)
            .putExtra(G.ARG_ERROR_CODE, e.getCode())
            .putExtra(G.ARG_ERROR_STRING, e.getMessage());
    }

    public static class Errors {
        public static final int NO_CONNECTIVITY = 1;
        public static final int ACCESS_DENIED = 1 << 1;
        public static final int NO_JSON = 1 << 2;
        public static final int NO_CONNECTION = 1 << 3;
        public static final int GENERIC_HTTP = 1 << 4;
        public static final int THREAD_ERROR = 1 << 5;
        public static final int RESPONSE_ERROR = 1 << 6;
        public static final int DUPLICATE_TORRENT = 1 << 7;
        public static final int INVALID_TORRENT = 1 << 8;
        public static final int TIMEOUT = 1 << 9;
        public static final int OUT_OF_MEMORY = 1 << 10;
        public static final int JSON_PARSE_ERROR = 1 << 11;
    }
}
