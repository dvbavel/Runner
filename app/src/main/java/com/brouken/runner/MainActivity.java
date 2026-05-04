package com.brouken.runner;

import android.app.Activity;
import android.content.Intent;
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
            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                if (!applicationInfo.enabled) {
                    if (isInstalledFromPlayStore(packageManager, applicationInfo.packageName)) {
                        final Intent launchIntent = packageManager.getLaunchIntentForPackage(applicationInfo.packageName);
                        if (launchIntent != null) {
                            startActivity(launchIntent);
                        }
                    }
                }
            }
        }

        final Intent intent = new Intent("com.google.android.finsky.VIEW_MY_DOWNLOADS")
                .setPackage(PLAY_STORE_PACKAGE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        }

        finish();
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
