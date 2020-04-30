package com.github.greyfox13.stravanotifier;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Message;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

public class StravaNotifService extends NotificationListenerService
{
    @Override
    public void onCreate()
    {
        super.onCreate();
    }

    @Override
    public void onDestroy()
    {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn)
    {
        String title, text;
        String time;
        String dist;
        boolean isRun;
        int pos1, pos2;
        long time_i=0, dist_i=0;
        String units[];
        Message msg;

        if(sbn.getPackageName().equals("com.strava"))
        {
            title = sbn.getNotification().extras.getCharSequence("android.title").toString();
            text = sbn.getNotification().extras.getString("android.text");
            if(title != null)
            {
                pos1 = title.indexOf('·',0);
                pos2 = title.indexOf('·',pos1+1);
                if (pos1 > -1 && pos1 > -1)
                {
                    time = title.substring(pos1 + 2, pos2 - 1);
                    dist = title.substring(pos2 + 2, title.length() - 3);
                    units = time.split(":");
                    if(units[0].equals("--")) return;
                    if (units.length == 2)
                    {
                        time_i = Integer.parseInt(units[0]) * 60 + Integer.parseInt(units[1]);
                    }
                    else if (units.length == 3)
                    {
                        time_i = Integer.parseInt(units[0]) * 3600 + Integer.parseInt(units[1]) * 60 + Integer.parseInt(units[2]);
                    }
                    dist = dist.replace(',', '.');
                    dist_i = (long) (Float.parseFloat(dist) * 1000);
                    msg = MainActivity.mainHandler.obtainMessage(MainActivity.WRIST_SET_TIME, 0, 0, time_i);
                    MainActivity.mainHandler.sendMessage(msg);
                    msg = MainActivity.mainHandler.obtainMessage(MainActivity.WRIST_SET_DIST, 0, 0, dist_i);
                    MainActivity.mainHandler.sendMessage(msg);
                    if(text != null)
                    {
                        if (text.equals("Остановлено") || text.equals("Stopped"))
                            isRun = false;
                        else
                            isRun = true;
                        msg = MainActivity.mainHandler.obtainMessage(MainActivity.WRIST_SET_RECORD_STATUS, 0, 0, isRun);
                        MainActivity.mainHandler.sendMessage(msg);
                    }
                    else
                    {
                        msg = MainActivity.mainHandler.obtainMessage(MainActivity.WRIST_SET_RECORD_STATUS, 0, 0, true);
                        MainActivity.mainHandler.sendMessage(msg);
                    }
                }
            }

        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn)
    {

    }
}
