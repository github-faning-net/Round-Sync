package ca.pkay.rcloneexplorer.Services;

import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;

import java.util.List;

import ca.pkay.rcloneexplorer.BroadcastReceivers.DownloadCancelAction;
import ca.pkay.rcloneexplorer.Items.FileItem;
import ca.pkay.rcloneexplorer.R;
import ca.pkay.rcloneexplorer.Rclone;


public class DownloadService extends IntentService {

    private final String CHANNEL_ID = "ca.pkay.rcexplorer.download_channel";
    private final String CHANNEL_NAME = "Downloads";
    public static final String DOWNLOAD_LIST_ARG = "ca.pkay.rcexplorer.download_service.arg1";
    public static final String DOWNLOAD_PATH_ARG = "ca.pkay.rcexplorer.download_service.arg2";
    public static final String REMOTE_ARG = "ca.pkay.rcexplorer.download_service.arg3";
    private Rclone rclone;
    private List<Process> runningProcesses;
    private Boolean aProcessFailed;

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.*
     */
    public DownloadService() {
        super("ca.pkay.rcexplorer.downloadservice");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setNotificationChannel();
        rclone = new Rclone(this);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Intent foregroundIntent = new Intent(this, DownloadService.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, foregroundIntent, 0);

        Intent cancelIntent = new Intent(this, DownloadCancelAction.class);
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(this, 0, cancelIntent, 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(getString(R.string.download_service_notification_title))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_cancel_download, getString(R.string.cancel), cancelPendingIntent);

        startForeground(1, builder.build());

        if (intent == null) {
            return;
        }

        final List<FileItem> downloadList = intent.getParcelableArrayListExtra(DOWNLOAD_LIST_ARG);
        final String downloadPath = intent.getStringExtra(DOWNLOAD_PATH_ARG);
        final String remote = intent.getStringExtra(REMOTE_ARG);

        runningProcesses = rclone.downloadItems(remote, downloadList, downloadPath);

        for (Process process : runningProcesses) {
            try {
                process.waitFor();
                if (process.exitValue() != 0) {
                    aProcessFailed = true;
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        stopForeground(true);

        NotificationCompat.Builder builder1;
        if (aProcessFailed) {
            builder1 = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_warning)
                    .setContentTitle(getString(R.string.download_cancelled))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(2, builder1.build());
            }
        } else {
            builder1 = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(getString(R.string.download_complete))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(3, builder1.build());
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        for (Process process : runningProcesses) {
            process.destroy();
        }
    }

    private void setNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel, but only on API 26+ because
            // the NotificationChannel class is new and not in the support library
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.download_service_notification_description));
            // Register the channel with the system
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}