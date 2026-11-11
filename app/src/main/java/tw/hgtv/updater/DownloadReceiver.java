package tw.hgtv.updater;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.io.File;

public class DownloadReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id=intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID,-1);
        long expected=context.getSharedPreferences(UpdateCore.PREFS,0).getLong(UpdateCore.KEY_DOWNLOAD_ID,-2);
        if(id!=expected) return;
        try {
            File f=UpdateCore.downloadedFile(context);
            if(f==null || !f.exists() || f.length()==0 || !UpdateCore.verifyDownloadedApk(context,f)) {
                Notifier.show(context,"HGTV 更新驗證失敗","下載檔案不存在或簽章不符合原版，已拒絕安裝。"); return;
            }
            context.getSharedPreferences(UpdateCore.PREFS,0).edit().putString(UpdateCore.KEY_APK,f.getAbsolutePath()).apply();
            Notifier.show(context,"HGTV 新版已下載","已通過原版簽章驗證。請開啟 HGTV 更新助手，按「安裝更新」。");
        } catch(Exception e) { Notifier.show(context,"HGTV 更新失敗",e.getMessage()==null?"下載或驗證失敗":e.getMessage()); }
    }
}
