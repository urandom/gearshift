package org.sugr.gearshift;

import java.io.IOException;
import java.io.InputStream;

import org.sugr.gearshift.util.Base64;

import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.widget.Toast;

public class TorrentFileReadActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            setContentView(R.layout.activity_torrent_file_read);

            new ReadDataAsyncTask().execute(intent.getData());
        } else {
            finish();
        }
    }

    private class ReadDataAsyncTask extends AsyncTask<Uri, Void, String> {
        private Uri mUri;

        @Override protected String doInBackground(Uri... params) {
            mUri = params[0];
            ContentResolver cr = getContentResolver();
            InputStream stream = null;
            Base64.InputStream base64 = null;

            try {
                stream = cr.openInputStream(mUri);

                base64 = new Base64.InputStream(stream, Base64.ENCODE | Base64.DO_BREAK_LINES);
                StringBuilder fileContent = new StringBuilder("");
                int ch;

                while( (ch = base64.read()) != -1)
                  fileContent.append((char)ch);

                return fileContent.toString();
            } catch (Exception e) {
                /* FIXME: proper error handling */
                G.logE("Error while reading the torrent file", e);
                Toast.makeText(TorrentFileReadActivity.this,
                        R.string.error_reading_torrent_file, Toast.LENGTH_SHORT).show();
                return null;
            } finally {
                try {
                    base64.close();
                    stream.close();
                } catch (IOException e) {
                    return null;
                }
            }
        }

        @Override protected void onPostExecute(String data) {
            if (data != null) {
                Intent listIntent = new Intent(TorrentFileReadActivity.this, TorrentListActivity.class);
                listIntent.setAction(TorrentListActivity.ACTION_OPEN);
                listIntent.setData(mUri);
                listIntent.putExtra(TorrentListActivity.ARG_FILE_DATA, data);

                startActivity(listIntent);

                finish();
            }
        }
    }
}
