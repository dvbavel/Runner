package com.brouken.runner;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

public class MainActivity extends Activity {
    private static final String PLAY_STORE_PACKAGE = "com.android.vending";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final PackageManager packageManager = getPackageManager();
        for (ApplicationInfo applicationInfo : getInstalledApplications(packageManager)) {
            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    || applicationInfo.enabled
                    || !isInstalledFromPlayStore(packageManager, applicationInfo.packageName)) {
                continue;
            }

            launchPackage(packageManager, applicationInfo.packageName);
        }

        openPlayStore(packageManager);

        finish();
    }

    private void launchPackage(PackageManager packageManager, String packageName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                packageManager.getLaunchIntentSenderForPackage(packageName)
                        .sendIntent(this, 0, null, null, null);
                return;
            } catch (IntentSender.SendIntentException | SecurityException ignored) {
                // Fall back to the regular launch intent below.
            }
        }

        final Intent launchIntent = packageManager.getLaunchIntentForPackage(packageName);
        if (launchIntent != null) {
            startActivitySafely(launchIntent);
        }
    }

    private void openPlayStore(PackageManager packageManager) {
        final Intent downloadsIntent = new Intent("com.google.android.finsky.VIEW_MY_DOWNLOADS")
                .setPackage(PLAY_STORE_PACKAGE);
        if (startActivitySafely(downloadsIntent)) {
            return;
        }

        final Intent launchIntent = packageManager.getLaunchIntentForPackage(PLAY_STORE_PACKAGE);
        if (launchIntent != null) {
            startActivitySafely(launchIntent);
        }
    }

    private boolean startActivitySafely(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException ignored) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static Iterable<ApplicationInfo> getInstalledApplications(PackageManager packageManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0));
        }
        return packageManager.getInstalledApplications(0);
    }

    @SuppressWarnings("deprecation")
    private static boolean isInstalledFromPlayStore(PackageManager packageManager, String packageName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                final InstallSourceInfo installSourceInfo = packageManager.getInstallSourceInfo(packageName);
                return PLAY_STORE_PACKAGE.equals(installSourceInfo.getInstallingPackageName())
                        || PLAY_STORE_PACKAGE.equals(installSourceInfo.getInitiatingPackageName());
            } catch (PackageManager.NameNotFoundException ignored) {
                return false;
            }
        }
        return PLAY_STORE_PACKAGE.equals(packageManager.getInstallerPackageName(packageName));
    }
}
