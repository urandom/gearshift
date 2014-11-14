package org.sugr.gearshift.ui.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.text.Spanned;

import org.sugr.gearshift.R;

public class UpdateCheckDialog {
    private AlertDialog dialog;

    public UpdateCheckDialog(final Context context, CharSequence text, final String viewUrl, final String downloadUrl) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
            .setCancelable(true)
            .setNegativeButton(android.R.string.no, null);

        builder.setNeutralButton(R.string.update_download, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(downloadUrl));
                context.startActivity(i);
            }
        });

        builder.setPositiveButton(R.string.update_view, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(viewUrl));
                context.startActivity(i);
            }
        });

        builder.setMessage(text);

        dialog = builder.create();
    }

    public UpdateCheckDialog(Context context, CharSequence text) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
            .setCancelable(true)
            .setPositiveButton(android.R.string.ok, null);

        builder.setMessage(text);

        dialog = builder.create();
    }

    public void show() {
        dialog.show();
    }
}
