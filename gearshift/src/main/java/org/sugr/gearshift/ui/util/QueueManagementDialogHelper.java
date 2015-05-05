package org.sugr.gearshift.ui.util;

import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.sugr.gearshift.G;
import org.sugr.gearshift.R;
import org.sugr.gearshift.core.TransmissionSession;
import org.sugr.gearshift.service.DataService;
import org.sugr.gearshift.service.DataServiceManager;
import org.sugr.gearshift.service.DataServiceManagerInterface;
import org.sugr.gearshift.ui.BaseTorrentActivity;
import org.sugr.gearshift.ui.TransmissionSessionInterface;
import org.sugr.gearshift.util.CheatSheet;

public class QueueManagementDialogHelper {
    private BaseTorrentActivity activity;
    private AlertDialog dialog;

    public QueueManagementDialogHelper(BaseTorrentActivity activity) {
        if (!(activity instanceof TransmissionSessionInterface)
            || !(activity instanceof DataServiceManagerInterface)) {
            throw new IllegalArgumentException("Invalid activity instance");
        }

        this.activity = activity;
    }

    public AlertDialog showDialog(final String[] hashStrings) {
        LayoutInflater inflater = activity.getLayoutInflater();

        final TransmissionSession session = activity.getSession();
        final DataServiceManager manager = activity.getDataServiceManager();
        if (session == null || manager == null) {
            return null;
        }

        final View view = inflater.inflate(R.layout.torrent_queue_management_popup, null);
        final AlertDialog.Builder builder = new AlertDialog.Builder(activity)
            .setView(view)
            .setTitle(R.string.queue_management);

        if (dialog != null) {
            dialog.dismiss();
        }
        dialog = builder.create();

        dialog.show();

        TextView label = (TextView) dialog.findViewById(R.id.queue_label);
        label.setText(activity.getResources().getQuantityString(R.plurals.queue_label, hashStrings.length));

        View button = dialog.findViewById(R.id.queue_top);
        CheatSheet.setup(button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                manager.setTorrentAction(hashStrings, G.TorrentAction.QUEUE_MOVE_TOP);
                activity.setRefreshing(true, DataService.Requests.SET_TORRENT_ACTION);

                dialog.dismiss();
            }
        });

        button = dialog.findViewById(R.id.queue_up);
        CheatSheet.setup(button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                manager.setTorrentAction(hashStrings, G.TorrentAction.QUEUE_MOVE_UP);
                activity.setRefreshing(true, DataService.Requests.SET_TORRENT_ACTION);

                dialog.dismiss();
            }
        });

        button = dialog.findViewById(R.id.queue_down);
        CheatSheet.setup(button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                manager.setTorrentAction(hashStrings, G.TorrentAction.QUEUE_MOVE_DOWN);
                activity.setRefreshing(true, DataService.Requests.SET_TORRENT_ACTION);

                dialog.dismiss();
            }
        });

        button = dialog.findViewById(R.id.queue_bottom);
        CheatSheet.setup(button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                manager.setTorrentAction(hashStrings, G.TorrentAction.QUEUE_MOVE_BOTTOM);
                activity.setRefreshing(true, DataService.Requests.SET_TORRENT_ACTION);

                dialog.dismiss();
            }
        });

        return dialog;
    }

    public void reset() {
        if (dialog != null) {
            dialog.dismiss();
            dialog = null;
        }
    }
}
