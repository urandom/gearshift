package org.sugr.gearshift;

import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

public class AboutActivity extends FragmentActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_about);

        TextView version = (TextView) findViewById(R.id.about_version);
        try {
            version.setText(getPackageManager().getPackageInfo(getPackageName(), 0 ).versionName);
        } catch (NameNotFoundException e) {
            G.logE("Error getting the app version", e);
        }
        TextView donation = (TextView) findViewById(R.id.about_donation);
        donation.setText(Html.fromHtml(getString(R.string.about_donation)));
        donation.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
