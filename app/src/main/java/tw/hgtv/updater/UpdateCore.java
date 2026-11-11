package tw.hgtv.updater;

import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;

final class UpdateCore {
    static final String TAG = "HGTVUpdater";
    static final String API = "https://api.github.com/repos/pan8664716/HGTV/releases/latest";
    // SHA-256 certificate digest independently extracted from both user-provided HGTV APKs.
    static final String EXPECTED_CERT = "27DA90725C2BCEC6C334096EE438877B480875DFAA1339B5DBEDC5FD9FCEE019";
    static final String PREFS = "updater";
    static final String KEY_APK = "downloaded_apk";
    static final String KEY_NAME = "downloaded_name";
    static final String KEY_DOWNLOAD_ID = "download_id";

    static class Release { String tag, url, name; }

    static Release latestRelease() throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API).openConnection();
        c.setConnectTimeout(15000); c.setReadTimeout(15000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "HGTV-Auto-Updater-Android");
        try {
            if (c.getResponseCode() != 200) throw new Exception("GitHub API HTTP " + c.getResponseCode());
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder b = new StringBuilder(); String line;
            while ((line = r.readLine()) != null) b.append(line);
            JSONObject obj = new JSONObject(b.toString());
            Release rel = new Release(); rel.tag = obj.optString("tag_name", "");
            JSONArray assets = obj.optJSONArray("assets");
            if (assets == null) throw new Exception("Release 沒有 APK 附件");
            for (int i=0; i<assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                String n = a.optString("name", "");
                if (n.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                    rel.name = n; rel.url = a.optString("browser_download_url", ""); break;
                }
            }
            if (rel.url == null || rel.url.isEmpty()) throw new Exception("找不到正式版 APK 附件");
            return rel;
        } finally { c.disconnect(); }
    }

    static String findInstalledHgtv(Context context) throws Exception {
        PackageManager pm = context.getPackageManager();
        for (PackageInfo pi : pm.getInstalledPackages(PackageManager.GET_SIGNATURES)) {
            if (pi.signatures == null) continue;
            for (Signature sig : pi.signatures) {
                if (EXPECTED_CERT.equals(certDigest(sig))) return pi.packageName;
            }
        }
        throw new Exception("找不到已安裝且簽章符合的 HGTV。請先安裝原版 HGTV 1.3.6.2。");
    }

    static String installedVersion(Context context) throws Exception {
        String pkg = findInstalledHgtv(context);
        PackageInfo pi = context.getPackageManager().getPackageInfo(pkg, 0);
        return pi.versionName == null ? "未知" : pi.versionName;
    }

    static boolean verifyDownloadedApk(Context context, File apk) throws Exception {
        PackageManager pm = context.getPackageManager();
        PackageInfo pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(), PackageManager.GET_SIGNATURES);
        if (pi == null || pi.signatures == null || pi.signatures.length == 0) return false;
        for (Signature s : pi.signatures) if (EXPECTED_CERT.equals(certDigest(s))) return true;
        return false;
    }

    static String certDigest(Signature sig) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(sig.toByteArray());
        StringBuilder b = new StringBuilder();
        for (byte v : digest) b.append(String.format(Locale.US, "%02X", v));
        return b.toString();
    }

    static long download(Context context, Release rel) throws Exception {
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        String filename = "HGTV_update_" + rel.tag.replaceAll("[^A-Za-z0-9._-]", "_") + ".apk";
        File dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) throw new Exception("無法取得下載目錄");
        File old = new File(dir, filename); if (old.exists()) old.delete();
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(rel.url));
        req.setTitle("HGTV 更新 " + rel.tag);
        req.setDescription("正在下載官方 GitHub Release");
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, filename);
        req.setAllowedOverMetered(true); req.setAllowedOverRoaming(false);
        long id = dm.enqueue(req);
        context.getSharedPreferences(PREFS, 0).edit().putLong(KEY_DOWNLOAD_ID, id).putString(KEY_NAME, filename).apply();
        return id;
    }


    static String downloadStatus(Context context) {
        long id = context.getSharedPreferences(PREFS, 0).getLong(KEY_DOWNLOAD_ID, -1);
        if (id < 0) return "尚未建立下載工作。";
        DownloadManager dm = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return "Android DownloadManager 無法使用。";
        DownloadManager.Query q = new DownloadManager.Query().setFilterById(id);
        android.database.Cursor cur = null;
        try {
            cur = dm.query(q);
            if (cur == null || !cur.moveToFirst()) return "找不到下載工作（編號 " + id + "）。可能已被系統清除。";
            int status = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long done = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            long total = cur.getLong(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            int reason = cur.getInt(cur.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
            String size = total > 0 ? (done * 100 / total) + "%（" + done + "/" + total + " bytes）" : done + " bytes";
            switch (status) {
                case DownloadManager.STATUS_PENDING: return "下載等待中：" + size;
                case DownloadManager.STATUS_RUNNING: return "正在下載：" + size;
                case DownloadManager.STATUS_PAUSED: return "下載暫停：" + pauseReason(reason) + "；" + size;
                case DownloadManager.STATUS_SUCCESSFUL:
                    File f = downloadedFile(context);
                    return "下載完成：" + size + "；檔案 " + (f != null && f.exists() ? "存在" : "不存在");
                case DownloadManager.STATUS_FAILED: return "下載失敗：" + failReason(reason) + "（錯誤碼 " + reason + "）。請重新檢查更新。";
                default: return "下載狀態未知（" + status + "）。";
            }
        } catch (Exception e) {
            return "讀取下載狀態失敗：" + e.getMessage();
        } finally {
            if (cur != null) cur.close();
        }
    }

    private static String pauseReason(int r) {
        switch (r) {
            case DownloadManager.PAUSED_WAITING_TO_RETRY: return "等待重試";
            case DownloadManager.PAUSED_WAITING_FOR_NETWORK: return "等待網路";
            case DownloadManager.PAUSED_QUEUED_FOR_WIFI: return "等待 Wi-Fi";
            case DownloadManager.PAUSED_UNKNOWN: return "系統暫停";
            default: return "原因 " + r;
        }
    }
    private static String failReason(int r) {
        switch (r) {
            case DownloadManager.ERROR_CANNOT_RESUME: return "無法續傳";
            case DownloadManager.ERROR_DEVICE_NOT_FOUND: return "儲存裝置不存在";
            case DownloadManager.ERROR_FILE_ALREADY_EXISTS: return "檔案已存在";
            case DownloadManager.ERROR_FILE_ERROR: return "檔案寫入錯誤";
            case DownloadManager.ERROR_HTTP_DATA_ERROR: return "HTTP 資料錯誤";
            case DownloadManager.ERROR_INSUFFICIENT_SPACE: return "儲存空間不足";
            case DownloadManager.ERROR_TOO_MANY_REDIRECTS: return "重新導向過多";
            case DownloadManager.ERROR_UNHANDLED_HTTP_CODE: return "伺服器 HTTP 錯誤";
            case DownloadManager.ERROR_UNKNOWN: return "未知錯誤";
            default: return "下載管理員錯誤";
        }
    }

    static File downloadedFile(Context c) {
        String name = c.getSharedPreferences(PREFS, 0).getString(KEY_NAME, null);
        File dir = c.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        return name == null || dir == null ? null : new File(dir, name);
    }
}
