package tw.hgtv.updater;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateJobService extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @Override public boolean onStartJob(final JobParameters params) {
        executor.execute(() -> {
            try {
                UpdateCore.Release rel = UpdateCore.latestRelease();
                String current = UpdateCore.installedVersion(this);
                if (compareVersions(rel.tag, current) > 0) {
                    UpdateCore.download(this, rel);
                    Notifier.show(this, "找到 HGTV 新版本", rel.tag + " 已開始自動下載；下載完成後可回到更新助手安裝。");
                }
            } catch (Exception e) { android.util.Log.e(UpdateCore.TAG, "background check failed", e); }
            jobFinished(params, false);
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
    static int compareVersions(String a, String b) {
        String x=a.replaceFirst("^[vV]", ""), y=b.replaceFirst("^[vV]", "");
        String[] aa=x.split("[.-]"), bb=y.split("[.-]");
        for(int i=0;i<Math.max(aa.length,bb.length);i++) {
            int ai=i<aa.length?number(aa[i]):0, bi=i<bb.length?number(bb[i]):0;
            if(ai!=bi) return Integer.compare(ai,bi);
        }
        return 0;
    }
    private static int number(String s) { try { return Integer.parseInt(s.replaceAll("[^0-9].*$", "")); } catch(Exception e) { return 0; } }
}
