package org.sugr.gearshift.ui;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.widget.TextView;

import org.sugr.gearshift.R;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

public class CrashReport extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_crash_report);

        ((TextView) findViewById(R.id.crash_title)).setText(
            Html.fromHtml(getString(R.string.crash_title))
        );

        findViewById(R.id.cancel).setOnClickListener(v -> System.exit(1));

        findViewById(R.id.send).setOnClickListener(v -> {
            sendLogFile();
            System.exit(1);
        });
    }

    private String extractLogToString() {
        PackageManager manager = this.getPackageManager();
        PackageInfo info = null;
        try {
            info = manager.getPackageInfo(this.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e2) { }

        String model = Build.MODEL;
        if (!model.startsWith(Build.MANUFACTURER)) {
            model = Build.MANUFACTURER + " " + model;
        }

        InputStreamReader reader = null;

        try {
            String cmd = "logcat -d -v time";

            // get input stream
            Process process = Runtime.getRuntime().exec(cmd);
            reader = new InputStreamReader(process.getInputStream());

            StringBuilder stringBuffer = new StringBuilder();

            stringBuffer.append("Android version: ").append(Build.VERSION.SDK_INT).append("\n");
            stringBuffer.append("Device: ").append(model).append("\n");
            stringBuffer.append("App version: ").append(info == null ? "(null)" : info.versionCode).append("\n");
            stringBuffer.append("App version name: ").append(info == null ? "(null)" : info.versionName).append("\n");

            char[] buffer = new char[10000];
            do {
                int n = reader.read (buffer, 0, buffer.length);
                if (n == -1) {
                    break;
                }
                stringBuffer.append(buffer, 0, n);
            } while (true);

            reader.close();

            return stringBuffer.toString();
        } catch (IOException e) {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) { }
            }

            // You might want to write a failure message to the log here.
            return null;
        }
    }

    private void sendLogFile() {
        String log = extractLogToString();
        if (log == null) {
            return;
        }

        Intent intent = new Intent (Intent.ACTION_SEND);
        intent.setType("plain/text");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[] {"crashreports@sugr.org"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "Gear Shift crash report on " + new Date().toString());
        intent.putExtra(Intent.EXTRA_TEXT, "The crash produced the following error log:\n\n" + log);
        startActivity(intent);
    }
}
