package tw.hgtv.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public class InstallStatusReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
        if(status==PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm=intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if(confirm!=null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(confirm); }
        } else if(status==PackageInstaller.STATUS_SUCCESS) {
            Notifier.show(context,"HGTV 更新完成","新版已成功安裝。");
        } else {
            String msg=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Notifier.show(context,"HGTV 安裝未完成",msg==null?"Android 安裝程序未能完成安裝。":msg);
        }
    }
}
