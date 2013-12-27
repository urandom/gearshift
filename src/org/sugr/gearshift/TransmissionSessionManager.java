package org.sugr.gearshift;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.preference.PreferenceManager;
import android.util.Base64;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.sugr.gearshift.datasource.DataSource;
import org.sugr.gearshift.datasource.TorrentStatus;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class TransmissionSessionManager {
    public class ManagerException extends Exception {
        private static final long serialVersionUID = 6477491498169428449L;
        int mCode;
        public ManagerException(String message, int code) {
            super(message == null ? "" : message);
            mCode = code;
        }

        public int getCode() {
            return mCode;
        }
    }

    public final static String PREF_LAST_SESSION_ID = "last_session_id";

    private TransmissionProfile mProfile;

    private ConnectivityManager mConnManager;

    private String mSessionId;

    private int mInvalidSessionRetries = 0;
    private SharedPreferences mDefaultPrefs;

    private DataSource dataSource;

    public TransmissionSessionManager(Context context, TransmissionProfile profile, DataSource dataSource) {
        mProfile = profile;

        this.dataSource = dataSource;
        mConnManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        mDefaultPrefs = PreferenceManager.getDefaultSharedPreferences(context);

        mSessionId = mDefaultPrefs.getString(PREF_LAST_SESSION_ID, null);
    }

    public boolean hasConnectivity() {
        NetworkInfo info = mConnManager.getActiveNetworkInfo();
        return (info != null);
    }

    public void setProfile(TransmissionProfile profile) {
        mProfile = profile;
        mSessionId = null;
    }

    public void updateSession() throws ManagerException {
        ObjectNode request = createRequest("session-get");

        SessionGetResponse response = (SessionGetResponse) requestData(request, SessionGetResponse.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }
    }

    public TorrentStatus getActiveTorrents(String[] fields) throws ManagerException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = createRequest("torrent-get", true);
        ObjectNode arguments = (ObjectNode) request.path("arguments");
        arguments.put("ids", "recently-active");
        arguments.put("fields", mapper.valueToTree(fields));

        TorrentGetResponse response = (TorrentGetResponse) requestData(request, TorrentGetResponse.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }

        return response.getTorrentStatus();
    }

    public TorrentStatus getTorrents(String[] fields, int[] ids) throws ManagerException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = createRequest("torrent-get", true);
        ObjectNode arguments = (ObjectNode) request.path("arguments");
        arguments.put("fields", mapper.valueToTree(fields));
        if (ids != null && ids.length > 0) {
            arguments.put("ids", mapper.valueToTree(ids));
        }

        TorrentGetResponse response = (TorrentGetResponse) requestData(request, TorrentGetResponse.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }

        return response.getTorrentStatus();
    }

    public void setSession(TransmissionSession session, String... keys) throws ManagerException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = createRequest("session-set");
        ObjectNode arguments = mapper.valueToTree(session);

        arguments.retain(keys);
        request.put("arguments", arguments);

        Response response = requestData(request, Response.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }
    }

    public void setTorrentsRemove(int[] ids, boolean delete) throws ManagerException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = createRequest("torrent-remove", true);
        ObjectNode arguments = (ObjectNode) request.path("arguments");
        arguments.put("ids", mapper.valueToTree(ids));
        arguments.put("delete-local-data", delete);

        Response response = requestData(request, Response.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }
    }

    public void setTorrentsAction(String action, int[] ids) throws ManagerException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = createRequest(action, true);
        ObjectNode arguments = (ObjectNode) request.path("arguments");
        arguments.put("ids", mapper.valueToTree(ids));

        Response response = requestData(request, Response.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }
    }

    public void setTorrentsLocation(int[] ids, String location, boolean move) throws ManagerException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = createRequest("torrent-set-location", true);
        ObjectNode arguments = (ObjectNode) request.path("arguments");
        arguments.put("ids", mapper.valueToTree(ids));
        arguments.put("location", location);
        arguments.put("move", move);

        Response response = requestData(request, Response.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }
    }

    @SuppressWarnings("unchecked")
    public void setTorrentsProperty(int[] ids, String key, Object value) throws ManagerException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = createRequest("torrent-set", true);
        ObjectNode arguments = (ObjectNode) request.path("arguments");
        arguments.put("ids", mapper.valueToTree(ids));

        if (key.equals(Torrent.SetterFields.TRACKER_REPLACE)) {
            List<Object> tuple = (List<Object>) value;
            ArrayNode list = JsonNodeFactory.instance.arrayNode();

            for (int i = 0; i < tuple.size(); i += 2) {
                list.add((Integer) tuple.get(i)).add((String) tuple.get(i + 1));
            }

            arguments.put(key, list);
        } else {
            if (   key.equals(Torrent.SetterFields.DOWNLOAD_LIMITED)
                || key.equals(Torrent.SetterFields.SESSION_LIMITS)
                || key.equals(Torrent.SetterFields.UPLOAD_LIMITED)) {
                arguments.put(key, (Boolean) value);
            } else if (   key.equals(Torrent.SetterFields.TORRENT_PRIORITY)
                       || key.equals(Torrent.SetterFields.QUEUE_POSITION)
                       || key.equals(Torrent.SetterFields.PEER_LIMIT)
                       || key.equals(Torrent.SetterFields.SEED_RATIO_MODE)) {
                arguments.put(key, (Integer) value);
            } else if (   key.equals(Torrent.SetterFields.FILES_WANTED)
                       || key.equals(Torrent.SetterFields.FILES_UNWANTED)) {
                if (value instanceof Integer) {
                    arguments.put(key, mapper.valueToTree(new int[] { (Integer) value }));
                } else {
                    arguments.put(key, mapper.valueToTree(value));
                }
            } else if (   key.equals(Torrent.SetterFields.DOWNLOAD_LIMIT)
                       || key.equals(Torrent.SetterFields.UPLOAD_LIMIT)) {
                arguments.put(key, (Long) value);
            } else if (key.equals(Torrent.SetterFields.SEED_RATIO_LIMIT)) {
                arguments.put(key, (Float) value);
            } else if (   key.equals(Torrent.SetterFields.FILES_HIGH)
                       || key.equals(Torrent.SetterFields.FILES_NORMAL)
                       || key.equals(Torrent.SetterFields.FILES_LOW)
                       || key.equals(Torrent.SetterFields.TRACKER_REMOVE)) {
                arguments.put(key, mapper.valueToTree(value));
            } else if (key.equals(Torrent.SetterFields.TRACKER_ADD)) {
                arguments.put(key, mapper.valueToTree(value));
            }
        }

        Response response = requestData(request, Response.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }
    }

    public int addTorrent(String uri, String meta, String location, boolean paused)
            throws ManagerException {
        ObjectNode request = createRequest("torrent-add", true);
        ObjectNode arguments = (ObjectNode) request.path("arguments");

        if (uri == null) {
            arguments.put(Torrent.AddFields.META, meta);
        } else {
            arguments.put(Torrent.AddFields.URI, uri);
        }
        arguments.put(Torrent.AddFields.LOCATION, location);
        arguments.put(Torrent.AddFields.PAUSED, paused);

        AddTorrentResponse response = (AddTorrentResponse) requestData(request, AddTorrentResponse.class);
        if (response.getResult().equals("success")) {
            return response.getAddedId();
        } else if (response.isDuplicate()) {
            throw new ManagerException("duplicate torrent", -2);
        } else {
            throw new ManagerException(response.getResult(), -2);
        }
    }

    public boolean testPort() throws ManagerException {
        ObjectNode request = createRequest("port-test");

        PortTestResponse response = (PortTestResponse) requestData(request, PortTestResponse.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }

        return response.isPortOpen();
    }

    public long blocklistUpdate() throws ManagerException {
        ObjectNode request = createRequest("blocklist-update");

        BlocklistUpdateResponse response = (BlocklistUpdateResponse) requestData(request, BlocklistUpdateResponse.class);
        if (!response.getResult().equals("success")) {
            throw new ManagerException(response.getResult(), -2);
        }

        return response.getBlocklistSize();
    }

    public long getFreeSpace(String defaultPath) throws ManagerException {
        ObjectNode request = createRequest("free-space", true);
        ObjectNode arguments = (ObjectNode) request.path("arguments");

        String path = mDefaultPrefs.getString(G.PREF_LIST_DIRECTORY, null);
        if (path == null) {
            path = defaultPath;
        }
        arguments.put("path", path);

        FreeSpaceResponse response = (FreeSpaceResponse) requestData(request, FreeSpaceResponse.class);
        if (response.getResult().equals("success")) {
            return response.getFreeSpace();
        } else if (response.getResult().equals("method name not recognized")) {
            return -1;
        } else {
            G.logE("Transmission Daemon Error!",
                    new Exception(response.getResult()));
            return -1;
        }
    }

    private Response requestData(ObjectNode data, Class klass) throws ManagerException {
        OutputStream os = null;
        InputStream is = null;
        HttpURLConnection conn = null;
        try {
            URL url = new URL(
                  (mProfile.isUseSSL() ? "https://" : "http://")
                + mProfile.getHost() + ":" + mProfile.getPort()
                + mProfile.getPath());
            conn = (HttpURLConnection) url.openConnection();
            if (mProfile.isUseSSL()) {
                try {
                    SSLContext sc = SSLContext.getInstance("TLS");
                    sc.init(null, new TrustManager[] {
                        new X509TrustManager() {
                            @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return null;
                            }
                            @Override public void checkClientTrusted(
                                    java.security.cert.X509Certificate[] certs,
                                    String authType) {}
                            @Override public void checkServerTrusted(
                                    java.security.cert.X509Certificate[] certs,
                                    String authType) {}
                        }
                    }, new java.security.SecureRandom());
                    ((HttpsURLConnection) conn).setSSLSocketFactory(sc.getSocketFactory());
                    ((HttpsURLConnection) conn).setHostnameVerifier(new HostnameVerifier() {
                        @Override public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }

                    });
                } catch (NoSuchAlgorithmException e) {
                    G.logE("Error creating an SSL context", e);
                    throw new ManagerException("ssl", -1);
                } catch (KeyManagementException e) {
                    G.logE("Error initializing the SSL context", e);
                    throw new ManagerException("ssl", -1);
                }
            }
            int timeout = mProfile.getTimeout() > 0
                ? mProfile.getTimeout() * 1000
                : 10000;
            conn.setReadTimeout(timeout);
            conn.setConnectTimeout(timeout);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
            conn.setUseCaches(false);
            conn.setAllowUserInteraction(false);

            conn.setRequestMethod("POST");
            conn.setDoInput(true);
            conn.setDoOutput(true);

            if (mSessionId != null) {
                conn.setRequestProperty("X-Transmission-Session-Id", mSessionId);
            }

            String user = mProfile.getUsername();
            if (user != null && user.length() > 0) {
                conn.setRequestProperty("Authorization",
                        "Basic " + Base64.encodeToString(
                                (user + ":" + mProfile.getPassword()).getBytes(), Base64.DEFAULT));
            }

            String json = data.toString();

            os = conn.getOutputStream();
            os.write(json.getBytes());
            os.flush();
            os.close();

            G.logD("The request is " + json);

            // Starts the query
            conn.connect();

            int code = conn.getResponseCode();

            // TorrentListActivity.logD("Got a response code " + code);
            if (code == 409) {
                mSessionId = getSessionId(conn);
                if (mInvalidSessionRetries < 3 && mSessionId != null) {
                    ++mInvalidSessionRetries;
                    return requestData(data, klass);
                } else {
                    mInvalidSessionRetries = 0;
                }
            } else {
                mInvalidSessionRetries = 0;
            }

            Response response;
            switch(code) {
                case 200:
                case 201:
                    if (conn.getHeaderField("Content-Type").startsWith("text/html")) {
                        throw new ManagerException("no-json", code);
                    }
                    is = conn.getInputStream();

                    // Convert the InputStream into a string
                    String encoding = conn.getContentEncoding();

                    if (encoding != null && encoding.equalsIgnoreCase("gzip")) {
                        is = new GZIPInputStream(is);
                    } else if (encoding != null && encoding.equalsIgnoreCase("deflate")) {
                        is = new InflaterInputStream(is);
                    }
                    response = buildResponse(is, klass);
                    G.logD("Torrent response is '" + response.getResult() + "'");
                    break;
                default:
                    throw new ManagerException(conn.getResponseMessage(), code);
            }

            return response;
        } catch (java.net.SocketTimeoutException e) {
            throw new ManagerException("timeout", -1);
        } catch (JsonParseException e) {
            G.logE("Error parsing JSON", e);
            throw new ManagerException(e.getMessage(), -4);
        } catch (JsonMappingException e) {
            G.logE("Error parsing JSON", e);
            throw new ManagerException(e.getMessage(), -4);
        } catch (IOException e) {
            G.logE("Error reading stream", e);
            throw new ManagerException(e.getMessage(), -1);
        } catch (OutOfMemoryError e) {
            e.printStackTrace();
            throw new ManagerException("out of memory", -3);
        } finally {
            try {
                if (os != null)
                    os.close();
                if (is != null)
                    is.close();
            } catch(IOException e) {
                e.printStackTrace();
            }

            if (conn != null)
                conn.disconnect();
        }
    }

    private String getSessionId(HttpURLConnection conn) {
        String id = conn.getHeaderField("X-Transmission-Session-Id");

        if (id != null && !id.equals("")) {
            Editor e = mDefaultPrefs.edit();
            e.putString(PREF_LAST_SESSION_ID, id);
            e.commit();
        }
        return id;
    }

    private ObjectNode createRequest(String method, boolean hasArguments) {
        JsonNodeFactory factory = JsonNodeFactory.instance;
        ObjectNode root = factory.objectNode();

        root.put("method", method);

        if (hasArguments) {
            ObjectNode arguments = factory.objectNode();
            root.put("arguments", arguments);
        }

        return root;
    }

    private ObjectNode createRequest(String method) {
        return createRequest(method, false);
    }

    private Response buildResponse(InputStream stream, Class klass) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        JsonFactory factory = mapper.getFactory();
        JsonParser parser = factory.createParser(stream);

        String result = null;
        Response response = null;

        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new IOException("The server data is expected to be an object");
        }
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            String name = parser.getCurrentName();

            if (name.equals("result")) {
                result = parser.nextTextValue();
            } else if (name.equals("arguments")) {
                if (klass == SessionGetResponse.class) {
                    parser.nextValue();

                    response = new SessionGetResponse();
                    dataSource.updateSession(parser);
                } else if (klass == TorrentGetResponse.class) {
                    response = new TorrentGetResponse();
                    int[] removed = null;

                    parser.nextToken();
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String argname = parser.getCurrentName();

                        parser.nextToken();
                        if (argname.equals("torrents")) {
                            ((TorrentGetResponse) response).setTorrentStatus(dataSource.updateTorrents(parser));
                        } else if (argname.equals("removed")) {
                            removed = mapper.readValue(parser, int[].class);
                        }
                    }

                    if (removed != null && removed.length > 0) {
                        if (dataSource.removeTorrents(removed)) {
                            ((TorrentGetResponse) response).getTorrentStatus().hasRemoved = true;
                        }
                    }
                } else if (klass == AddTorrentResponse.class) {
                    response = new AddTorrentResponse();

                    parser.nextToken();
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String argname = parser.getCurrentName();

                        parser.nextValue();
                        if (argname.equals("torrent-added")) {
                            while (parser.nextToken() != JsonToken.END_OBJECT) {
                                String key = parser.getCurrentName();

                                parser.nextValue();
                                if (key.equals("id")) {
                                    ((AddTorrentResponse) response).setAddedId(parser.getIntValue());
                                }
                            }
                        } else if (argname.equals("torrent-duplicate")) {
                            while (parser.nextToken() != JsonToken.END_OBJECT) {
                                String key = parser.getCurrentName();

                                parser.nextValue();
                                if (key.equals("id")) {
                                    ((AddTorrentResponse) response).setDuplicateId(parser.getIntValue());
                                }
                            }
                        }
                    }
                } else if (klass == PortTestResponse.class) {
                    response = new PortTestResponse();

                    parser.nextToken();
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String argname = parser.getCurrentName();
                        if (argname.equals("port-is-open")) {
                            boolean isOpen = parser.nextBooleanValue();
                            ((PortTestResponse) response).setPortOpen(isOpen);
                        } else {
                            parser.nextToken();
                        }
                    }
                } else if (klass == BlocklistUpdateResponse.class) {
                    response = new BlocklistUpdateResponse();

                    parser.nextToken();
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String argname = parser.getCurrentName();
                        if (argname.equals("blocklist-size")) {
                            long size = parser.nextLongValue(0);
                            ((BlocklistUpdateResponse) response).setBlocklistSize(size);
                        } else {
                            parser.nextToken();
                        }
                    }
                } else if (klass == FreeSpaceResponse.class) {
                    response = new FreeSpaceResponse();

                    parser.nextToken();
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        String argname = parser.getCurrentName();
                        if (argname.equals("size-bytes")) {
                            long size = parser.nextLongValue(0);
                            ((FreeSpaceResponse) response).setFreeSpace(size);
                        } else if (argname.equals("path")) {
                            String path = parser.nextTextValue();
                            ((FreeSpaceResponse) response).setPath(path);
                        } else {
                            parser.nextToken();
                        }
                    }
                } else {
                    response = new Response();

                    parser.skipChildren();
                }
            } else {
                parser.nextToken();
            }
        }

        if (response != null) {
            response.setResult(result);
        }

        return response;
    }

    public static class Response {
        protected String mResult = null;

        public String getResult() {
            return mResult;
        }
        public void setResult(String result) {
            mResult = result;
        }
    }

    public static class SessionGetResponse extends Response {}

    public static class TorrentGetResponse extends Response {
        private TorrentStatus status;

        public TorrentStatus getTorrentStatus() {
            return status;
        }

        public void setTorrentStatus(TorrentStatus status) {
            this.status = status;
        }
    }

    public static class AddTorrentResponse extends Response {
        private int addedId = -1;

        private int duplicateId = -1;

        public int getAddedId() {
            return addedId;
        }
        public void setAddedId(int id) {
            addedId = id;
        }

        public boolean isDuplicate() {
            return duplicateId != -1;
        }
        public void setDuplicateId(int id) {
            duplicateId = id;
        }
    }

    public static class PortTestResponse extends Response {
        public boolean open;

        public boolean isPortOpen() {
            return open;
        }
        public void setPortOpen(boolean isOpen) {
            this.open = isOpen;
        }
    }

    public static class BlocklistUpdateResponse extends Response {
        public long size;

        public long getBlocklistSize() {
            return size;
        }
        public void setBlocklistSize(long size) {
            this.size = size;
        }
    }

    public static class FreeSpaceResponse extends Response {
        public long freeSpace;
        public String path;

        public long getFreeSpace() {
            return freeSpace;
        }
        public void setFreeSpace(long size) {
            this.freeSpace = size;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }

    public static int[] convertIntegerList(List<Integer> list) {
        int[] ret = new int[list.size()];
        Iterator<Integer> iterator = list.iterator();
        for (int i = 0; i < ret.length; i++) {
            ret[i] = iterator.next();
        }

        return ret;
    }

    public static String[] convertStringList(List<String> list) {
        String[] ret = new String[list.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = list.get(i);
        }

        return ret;
    }
}
