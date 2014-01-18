package org.sugr.gearshift;

import android.database.Cursor;

public class TransmissionData {
    public TransmissionSession session = null;
    public Cursor cursor;
    public int error = 0;
    public int errorCode = 0;
    public String errorMessage;
    public boolean hasRemoved = false;
    public boolean hasAdded = false;
    public boolean hasStatusChanged = false;
    public boolean hasMetadataNeeded = false;
    public boolean queryOnly = false;

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

    public TransmissionData(TransmissionSession session, int error, int errorCode) {
        this.session = session;
        this.error = error;
        this.errorCode = errorCode;
    }

    public TransmissionData(TransmissionSession session, Cursor cursor, boolean hasRemoved,
                            boolean hasAdded, boolean hasStatusChanged, boolean hasMetadataNeeded,
                            boolean queryOnly) {
        this.session = session;
        this.cursor = cursor;

        this.hasRemoved = hasRemoved;
        this.hasAdded = hasAdded;
        this.hasStatusChanged = hasStatusChanged;
        this.hasMetadataNeeded = hasMetadataNeeded;
        this.queryOnly = queryOnly;
    }
}
