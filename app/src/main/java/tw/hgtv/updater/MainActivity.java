package tw.hgtv.updater;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public class MainActivity extends Activity {
    private TextView status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable downloadPoll = new Runnable() {
        @Override public void run() {
            if (status != null) {
                String d = UpdateCore.downloadStatus(MainActivity.this);
                String base = status.getText().toString().split("\n\n下載狀態：")[0];
                if (!d.startsWith("尚未建立")) {
                    status.setText(base + "\n\n下載狀態：" + d);
                }
                handler.postDelayed(this, 2000);
            }
        }
    };
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        UpdateScheduler.schedule(this);
        buildUi();
        checkForUpdate(false);
    }
    private void buildUi() {
        int pad=(int)(getResources().getDisplayMetrics().density*22);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(pad,pad,pad,pad); root.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title=new TextView(this); title.setText("HGTV 更新助手"); title.setTextSize(27); title.setGravity(Gravity.CENTER); root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView sub=new TextView(this); sub.setText("適用 Android 8 以上｜每日自動檢查 GitHub 正式版"); sub.setTextSize(15); sub.setGravity(Gravity.CENTER); sub.setPadding(0,pad/2,0,pad); root.addView(sub);
        status=new TextView(this); status.setTextSize(17); status.setText("正在讀取版本資訊…"); status.setPadding(0,pad,0,pad); root.addView(status,new LinearLayout.LayoutParams(-1,-2));
        Button check=new Button(this); check.setText("立即檢查更新"); check.setOnClickListener(v->checkForUpdate(true)); root.addView(check,new LinearLayout.LayoutParams(-1,-2));
        Button install=new Button(this); install.setText("安裝已下載更新"); install.setOnClickListener(v->installDownloaded()); root.addView(install,new LinearLayout.LayoutParams(-1,-2));
        Button progress=new Button(this); progress.setText("查看下載進度／錯誤"); progress.setOnClickListener(v->showDownloadStatus()); root.addView(progress,new LinearLayout.LayoutParams(-1,-2));
        Button setting=new Button(this); setting.setText("允許此 App 安裝更新"); setting.setOnClickListener(v->openInstallPermission()); root.addView(setting,new LinearLayout.LayoutParams(-1,-2));
        TextView note=new TextView(this); note.setText("安全措施：只接受與你提供的 HGTV 1.3.5.1 / 1.3.6.2 相同簽章的 APK。更新助手不會解除安裝 HGTV，也不會清除它的資料。Android 仍會顯示系統安裝確認。\n\n背景檢查受 Android 電視盒的省電與網路政策影響；開機後會重新排程，每日檢查一次。"); note.setTextSize(13); note.setPadding(0,pad,0,0); root.addView(note);
        ScrollView scroll=new ScrollView(this); scroll.addView(root); setContentView(scroll);
    }
    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(downloadPoll);
        handler.post(downloadPoll);
    }
    @Override protected void onPause() {
        handler.removeCallbacks(downloadPoll);
        super.onPause();
    }
    private void showDownloadStatus() {
        String message = UpdateCore.downloadStatus(this);
        status.append("\n\n下載狀態：" + message);
        new AlertDialog.Builder(this).setTitle("HGTV 下載狀態").setMessage(message)
            .setPositiveButton("確定", null).show();
    }
    private void checkForUpdate(boolean showNoUpdate) {
        status.setText("正在連線 GitHub 檢查正式版…");
        new Thread(()->{
            try {
                String installed=UpdateCore.installedVersion(this);
                UpdateCore.Release r=UpdateCore.latestRelease();
                boolean newer=UpdateJobService.compareVersions(r.tag,installed)>0;
                runOnUiThread(()->{
                    status.setText("HGTV 已安裝版本："+installed+"\nGitHub 最新正式版："+r.tag+"\n\n"+(newer?"發現新版，正在自動下載…":"目前已是最新版本。"));
                    if(newer) {
                        try { long id=UpdateCore.download(this,r); status.append("\n下載工作已建立（編號 "+id+")。"); handler.removeCallbacks(downloadPoll); handler.post(downloadPoll); }
                        catch(Exception e) { status.append("\n下載啟動失敗："+e.getMessage()); }
                    } else if(showNoUpdate) new AlertDialog.Builder(this).setMessage("目前 HGTV 已是最新正式版："+installed).setPositiveButton("確定",null).show();
                });
            } catch(Exception e) { runOnUiThread(()->status.setText("檢查失敗："+e.getMessage()+"\n請確認網路，或先安裝原版 HGTV。")); }
        }).start();
    }
    private void openInstallPermission() {
        if(android.os.Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()) {
            Intent i=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:"+getPackageName())); startActivity(i);
        } else new AlertDialog.Builder(this).setMessage("已允許此更新助手呼叫 Android 安裝程序。").setPositiveButton("確定",null).show();
    }
    private void installDownloaded() {
        try {
            File apk=UpdateCore.downloadedFile(this);
            if(apk==null || !apk.exists()) throw new Exception("尚未找到已下載的更新 APK，請先檢查更新。");
            if(!UpdateCore.verifyDownloadedApk(this,apk)) throw new Exception("簽章驗證失敗，拒絕安裝。");
            String installedPkg=UpdateCore.findInstalledHgtv(this);
            PackageInfo archive=getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNATURES);
            if(archive==null || !installedPkg.equals(archive.packageName)) throw new Exception("下載 APK 的套件識別碼與已安裝 HGTV 不一致，拒絕安裝。");
            if(android.os.Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(this).setTitle("需要安裝權限").setMessage("請在下一頁允許「HGTV 更新助手」安裝未知來源 App，返回後再按一次安裝更新。").setPositiveButton("前往設定",(d,w)->openInstallPermission()).setNegativeButton("取消",null).show(); return;
            }
            installWithSession(apk);
        } catch(Exception e) { new AlertDialog.Builder(this).setMessage("無法安裝："+e.getMessage()).setPositiveButton("確定",null).show(); }
    }
    private void installWithSession(File apk) throws Exception {
        android.content.pm.PackageInstaller installer=getPackageManager().getPackageInstaller();
        android.content.pm.PackageInstaller.SessionParams params=new android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        int sessionId=installer.createSession(params);
        android.content.pm.PackageInstaller.Session session=installer.openSession(sessionId);
        try {
            OutputStream out=session.openWrite("hgtv-update.apk",0,apk.length());
            FileInputStream in=new FileInputStream(apk); byte[] buf=new byte[65536]; int n;
            while((n=in.read(buf))>0) out.write(buf,0,n);
            in.close(); session.fsync(out); out.close();
            Intent callback=new Intent(this,InstallStatusReceiver.class);
            int flags=android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            if(android.os.Build.VERSION.SDK_INT>=23) flags|=android.app.PendingIntent.FLAG_IMMUTABLE;
            android.app.PendingIntent pi=android.app.PendingIntent.getBroadcast(this,sessionId,callback,flags);
            session.commit(pi.getIntentSender());
            status.setText("已交由 Android 安裝程序處理，請依電視盒畫面確認安裝。");
        } finally { session.close(); }
    }
}
