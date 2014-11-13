package org.sugr.gearshift.ui;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBarActivity;
import android.widget.Toast;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.util.Base64;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;

public class TorrentFileReadActivity extends ActionBarActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            setContentView(R.layout.activity_torrent_file_read);

            new ReadTorrentDataTask().execute(intent.getData());
        } else {
            finish();
        }
    }

    private class ReadTorrentDataTask extends AsyncTask<Uri, Void, ReadTorrentDataTask.TaskData> {
        public class TaskData {
            public File file;
            public String path;
        }

        private Uri uri;

        @Override protected TaskData doInBackground(Uri... params) {
            uri = params[0];
            ContentResolver cr = getContentResolver();
            InputStream stream = null;
            Base64.InputStream base64 = null;

            try {
                stream = cr.openInputStream(uri);

                base64 = new Base64.InputStream(stream, Base64.ENCODE | Base64.DO_BREAK_LINES);
                StringBuilder fileContent = new StringBuilder("");
                int ch;

                while( (ch = base64.read()) != -1)
                  fileContent.append((char)ch);

                File file = new File(TorrentFileReadActivity.this.getCacheDir(), "torrentdata");

                if (file.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }

                if (file.createNewFile()) {
                    BufferedWriter bw = new BufferedWriter(new FileWriter(file));
                    bw.append(fileContent);
                    bw.close();

                    String path = null;
                    if ("content".equals(uri.getScheme())) {
                        Cursor cursor = cr.query(uri,
                            new String[] {
                                android.provider.MediaStore.Files.FileColumns.DATA
                            }, null, null, null);
                        if (cursor.moveToFirst()) {
                            path = cursor.getString(0);
                        }
                        cursor.close();
                    } else if ("file".equals(uri.getScheme())) {
                        path = uri.getPath();
                    }

                    G.logD("Torrent file path: " + path);

                    TaskData data = new TaskData();
                    data.file = file;
                    data.path = path;
                    return data;
                } else {
                    return null;
                }
            } catch (Exception e) {
                G.logE("Error while reading the torrent file", e);
                return null;
            } finally {
                try {
                    if (base64 != null)
                        base64.close();
                    if (stream != null)
                        stream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        @Override protected void onPostExecute(TaskData data) {
            if (data == null) {
                Toast.makeText(TorrentFileReadActivity.this,
                        R.string.error_reading_torrent_file, Toast.LENGTH_SHORT).show();
            } else {
                Intent listIntent = new Intent(TorrentFileReadActivity.this, TorrentListActivity.class);
                listIntent.setAction(TorrentListActivity.ACTION_OPEN);
                listIntent.setData(uri);
                listIntent.putExtra(TorrentListActivity.ARG_FILE_URI, data.file.toURI().toString());
                listIntent.putExtra(TorrentListActivity.ARG_FILE_PATH, data.path);

                startActivity(listIntent);
            }

            finish();
        }
    }
}
