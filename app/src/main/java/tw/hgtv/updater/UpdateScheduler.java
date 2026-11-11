package tw.hgtv.updater;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

final class UpdateScheduler {
    static final int JOB_ID = 61026;
    static void schedule(Context c) {
        JobScheduler js = (JobScheduler)c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, UpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(24L * 60L * 60L * 1000L)
                .build();
        js.schedule(job);
    }
}
