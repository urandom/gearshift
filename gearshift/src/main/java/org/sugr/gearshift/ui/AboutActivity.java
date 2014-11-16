package org.sugr.gearshift.ui;

import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.sugr.gearshift.G;
import org.sugr.gearshift.GearShiftApplication;
import org.sugr.gearshift.R;
import org.sugr.gearshift.ui.util.UpdateCheckDialog;

public class AboutActivity extends ActionBarActivity {
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

        findViewById(R.id.about_check_for_updates).setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final Button button = ((Button) v);

                button.setText(R.string.update_checking);

                ((GearShiftApplication) getApplication()).checkForUpdates(new GearShiftApplication.OnUpdateCheck() {
                    @Override public void onNewRelease(String title, String description, String url, String downloadUrl) {
                        new UpdateCheckDialog(AboutActivity.this,
                            G.trimTrailingWhitespace(Html.fromHtml(String.format(getString(R.string.update_available), title))),
                            url, downloadUrl).show();

                        button.setText(R.string.about_updates);
                    }

                    @Override public void onCurrentRelease() {
                        new UpdateCheckDialog(AboutActivity.this,
                            G.trimTrailingWhitespace(Html.fromHtml(getString(R.string.update_current)))
                        ).show();

                        button.setText(R.string.about_updates);
                    }

                    @Override public void onUpdateCheckError(Exception e) {
                        button.setText(R.string.about_updates);
                    }
                });
            }
        });
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
