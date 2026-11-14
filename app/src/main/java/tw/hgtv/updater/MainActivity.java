package tw.hgtv.updater;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.File;

public class MainActivity extends Activity {
    private static final int REQ_STORAGE = 701;
    private TextView status;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        UpdateScheduler.schedule(this);
        buildUi();
        ensureStoragePermissionThenCheck(false);
    }

    private void buildUi() {
        int pad=(int)(getResources().getDisplayMetrics().density*22);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad,pad,pad,pad);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title=new TextView(this);
        title.setText("HGTV 更新助手");
        title.setTextSize(27);
        title.setGravity(Gravity.CENTER);
        root.addView(title,new LinearLayout.LayoutParams(-1,-2));

        TextView sub=new TextView(this);
        sub.setText("適用 Android 8 以上｜每日自動檢查 GitHub 正式版");
        sub.setTextSize(15);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0,pad/2,0,pad);
        root.addView(sub);

        status=new TextView(this);
        status.setTextSize(17);
        status.setText("正在讀取版本資訊…");
        status.setPadding(0,pad,0,pad);
        root.addView(status,new LinearLayout.LayoutParams(-1,-2));

        Button check=new Button(this);
        check.setText("立即檢查更新");
        check.setOnClickListener(v->ensureStoragePermissionThenCheck(true));
        root.addView(check,new LinearLayout.LayoutParams(-1,-2));

        Button install=new Button(this);
        install.setText("安裝已下載更新");
        install.setOnClickListener(v->installDownloaded());
        root.addView(install,new LinearLayout.LayoutParams(-1,-2));

        Button progress=new Button(this);
        progress.setText("查看下載進度／檔案位置");
        progress.setOnClickListener(v->showDownloadStatus());
        root.addView(progress,new LinearLayout.LayoutParams(-1,-2));

        Button setting=new Button(this);
        setting.setText("允許此 APP 安裝更新");
        setting.setOnClickListener(v->openInstallPermission());
        root.addView(setting,new LinearLayout.LayoutParams(-1,-2));

        TextView note=new TextView(this);
        note.setText("下載位置：內部儲存空間/Download/HGTV_update_版本.apk\n\n安全措施：只接受與原版 HGTV 相同簽章及套件識別碼的 APK。更新時保留原有 HGTV 與資料；Android 仍會顯示系統安裝確認。");
        note.setTextSize(13);
        note.setPadding(0,pad,0,0);
        root.addView(note);

        ScrollView scroll=new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void ensureStoragePermissionThenCheck(boolean showNoUpdate) {
        if (android.os.Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            status.setText("需要儲存空間權限，請在系統提示中選擇允許，才能下載到公開 Download 資料夾。");
            return;
        }
        checkForUpdate(showNoUpdate);
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                ensureStoragePermissionThenCheck(false);
            } else {
                status.setText("未取得儲存空間權限，無法將 APK 存入公開 Download 資料夾。請到 Android 設定允許 HGTV 更新助手存取儲存空間。");
            }
        }
    }

    private void checkForUpdate(boolean showNoUpdate) {
        status.setText("正在連線 GitHub 檢查正式版…");
        new Thread(()->{
            try {
                String installed=UpdateCore.installedVersion(this);
                UpdateCore.Release r=UpdateCore.latestRelease();
                boolean newer=UpdateJobService.compareVersions(r.tag,installed)>0;
                runOnUiThread(()->{
                    status.setText("HGTV 已安裝版本："+installed+"\nGitHub 最新正式版："+r.tag+"\n\n"+(newer?"發現新版，正在下載至公開 Download 資料夾…":"目前已是最新版本。"));
                    if(newer) {
                        try {
                            long id=UpdateCore.download(this,r);
                            status.append("\n下載工作已建立（編號 "+id+"）。");
                            status.append("\n儲存位置："+Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath());
                        } catch(Exception e) {
                            status.append("\n下載啟動失敗："+e.getMessage());
                        }
                    } else if(showNoUpdate) {
                        new AlertDialog.Builder(this).setMessage("目前 HGTV 已是最新正式版："+installed).setPositiveButton("確定",null).show();
                    }
                });
            } catch(Exception e) {
                runOnUiThread(()->status.setText("檢查失敗："+e.getMessage()+"\n請確認網路，或先安裝原版 HGTV。"));
            }
        }).start();
    }

    private void showDownloadStatus() {
        String message=UpdateCore.downloadStatus(this);
        File f=UpdateCore.downloadedFile(this);
        if (f != null) message += "\n\nAPK 路徑：\n" + f.getAbsolutePath();
        new AlertDialog.Builder(this).setTitle("HGTV 下載診斷").setMessage(message).setPositiveButton("確定",null).show();
        status.setText(message);
    }

    private void openInstallPermission() {
        if(android.os.Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()) {
            Intent i=new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:"+getPackageName()));
            startActivity(i);
        } else {
            new AlertDialog.Builder(this).setMessage("已允許此更新助手呼叫 Android 安裝程序。").setPositiveButton("確定",null).show();
        }
    }

    private void installDownloaded() {
        try {
            File apk=UpdateCore.downloadedFile(this);
            if(apk==null || !apk.exists() || apk.length()==0) {
                String diagnostic=UpdateCore.downloadStatus(this);
                throw new Exception("公開 Download 資料夾中找不到 APK。\n"+diagnostic);
            }
            if(!UpdateCore.verifyDownloadedApk(this,apk)) throw new Exception("簽章驗證失敗，拒絕安裝。");
            String installedPkg=UpdateCore.findInstalledHgtv(this);
            PackageInfo archive=getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNATURES);
            if(archive==null || !installedPkg.equals(archive.packageName)) throw new Exception("下載 APK 的套件識別碼與已安裝 HGTV 不一致，拒絕安裝。");

            if(android.os.Build.VERSION.SDK_INT>=26 && !getPackageManager().canRequestPackageInstalls()) {
                new AlertDialog.Builder(this).setTitle("需要安裝權限")
                    .setMessage("請允許「HGTV 更新助手」安裝未知來源應用程式，返回後再按一次安裝更新。")
                    .setPositiveButton("前往設定",(d,w)->openInstallPermission())
                    .setNegativeButton("取消",null).show();
                return;
            }

            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",apk);
            Intent intent=new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri,"application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            status.setText("已開啟 Android 系統安裝器。請在電視畫面選擇「安裝」，完成後 HGTV 將以新版覆蓋更新，原有資料會保留。");
        } catch(Exception e) {
            new AlertDialog.Builder(this).setTitle("無法啟動安裝").setMessage(e.getMessage()).setPositiveButton("確定",null).show();
        }
    }
}
