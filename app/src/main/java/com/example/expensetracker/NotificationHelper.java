package com.example.expensetracker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/** Notification channel, permission check and bill-reminder notifications. */
public final class NotificationHelper {
    public static final String CHANNEL_BILLS = "bill_reminders";

    private NotificationHelper() {}

    /** Safe to call repeatedly; the channel only exists on Android 8.0+. */
    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_BILLS,
                context.getString(R.string.channel_bills_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.channel_bills_desc));
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(channel);
    }

    /** True when the POST_NOTIFICATIONS permission (Android 13+) is granted. Always true below Android 13. */
    public static boolean hasPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Permission granted AND notifications not switched off for the app in system settings. */
    public static boolean canNotify(Context context) {
        return hasPermission(context) && NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    /** Shows (or refreshes) the notification for one bill. Returns false if nothing was shown. */
    public static boolean notifyBill(Context context, Bill bill, long todayStart) {
        if (!canNotify(context)) return false;
        ensureChannel(context);
        String money = MoneyUtils.format(bill.amountPaisa);
        int days = BillCalculator.daysUntilDue(bill, todayStart);
        String text;
        switch (BillCalculator.state(bill, todayStart)) {
            case OVERDUE:
                text = context.getString(R.string.notif_overdue, daysText(context, -days), money);
                break;
            case DUE_TODAY:
                text = context.getString(R.string.notif_due_today, money);
                break;
            default:
                text = context.getString(R.string.notif_due_in, daysText(context, days), money);
                break;
        }
        Intent open = new Intent(context, BillsActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, (int) bill.id, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_BILLS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(bill.name)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pi)
                .setAutoCancel(true);
        try {
            NotificationManagerCompat.from(context).notify((int) bill.id, b.build());
            return true;
        } catch (SecurityException e) { // permission revoked between the check and the call
            return false;
        }
    }

    public static void cancelBill(Context context, long billId) {
        NotificationManagerCompat.from(context).cancel((int) billId);
    }

    static String daysText(Context context, int days) {
        return context.getResources().getQuantityString(R.plurals.days_count, days, days);
    }
}
