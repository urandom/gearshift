package org.sugr.gearshift.ui;

import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import org.sugr.gearshift.G;
import org.sugr.gearshift.GearShiftApplication;
import org.sugr.gearshift.R;

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

        TextView contact = (TextView) findViewById(R.id.about_contact);
        contact.setText(Html.fromHtml(String.format(getString(R.string.about_contact),
            "https://plus.google.com/communities/115768021623513120266")));
        contact.setMovementMethod(LinkMovementMethod.getInstance());

        TextView donation = (TextView) findViewById(R.id.about_donation);
        donation.setText(Html.fromHtml(String.format(getString(R.string.about_donation),
            "https://www.paypal.com/bg/cgi-bin/webscr?cmd=_donations&business=support@sugr.org&lc=US&item_name=Gear Shift&no_note=1&no_shipping=1&currency_code=EUR")));
        donation.setMovementMethod(LinkMovementMethod.getInstance());
    }

    @Override protected void onResume() {
        super.onResume();

        GearShiftApplication.setActivityVisible(true);
    }

    @Override protected void onPause() {
        super.onPause();

        GearShiftApplication.setActivityVisible(false);
    }
}
