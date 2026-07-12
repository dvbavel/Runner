package com.brouken.runner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final String PLAY_STORE_PACKAGE = "com.android.vending";
    private static final String SAMSUNG_DEVICE_CARE_PACKAGE = "com.samsung.android.lool";
    private static final int MAX_ATTEMPTS = 3;
    private static final long LAUNCH_INTERVAL_MS = 450;
    private static final long VERIFICATION_DELAY_MS = 2_000;

    private final ArrayDeque<String> pendingPackages = new ArrayDeque<>();
    private final List<String> targetPackages = new ArrayList<>();

    private Handler handler;
    private PackageManager packageManager;
    private TextView statusView;
    private int attempt;
    private int launchedInAttempt;
    private boolean completed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        statusView = new TextView(this);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(48, 48, 48, 48);
        statusView.setText(R.string.status_scanning);
        statusView.setTextSize(18);
        setContentView(statusView);

        handler = new Handler(Looper.getMainLooper());
        packageManager = getPackageManager();
        targetPackages.addAll(findDisabledPlayStorePackages(packageManager));

        if (targetPackages.isEmpty()) {
            completeSuccessfully();
            return;
        }

        startAttempt(targetPackages);
    }

    @Override
    protected void onDestroy() {
        if (!completed && handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
    }

    private void startAttempt(List<String> packages) {
        attempt++;
        launchedInAttempt = 0;
        pendingPackages.clear();
        pendingPackages.addAll(packages);
        launchNextPackage();
    }

    private void launchNextPackage() {
        final String packageName = pendingPackages.pollFirst();
        if (packageName == null) {
            handler.postDelayed(this::verifyPackages, VERIFICATION_DELAY_MS);
            return;
        }

        launchedInAttempt++;
        statusView.setText(getString(
                R.string.status_activating,
                launchedInAttempt,
                launchedInAttempt + pendingPackages.size(),
                attempt,
                MAX_ATTEMPTS
        ));
        launchPackage(packageManager, packageName);
        handler.postDelayed(this::launchNextPackage, LAUNCH_INTERVAL_MS);
    }

    private void verifyPackages() {
        final List<String> stillDisabled = new ArrayList<>();
        for (String packageName : targetPackages) {
            if (isApplicationDisabled(packageManager, packageName)) {
                stillDisabled.add(packageName);
            }
        }

        if (stillDisabled.isEmpty()) {
            completeSuccessfully();
        } else if (attempt < MAX_ATTEMPTS) {
            startAttempt(stillDisabled);
        } else {
            completeWithFailures(stillDisabled.size());
        }
    }

    private void completeSuccessfully() {
        completed = true;
        openPlayStore(packageManager);
        finish();
    }

    private void completeWithFailures(int failureCount) {
        completed = true;
        final String message = getResources().getQuantityString(
                R.plurals.status_failed,
                failureCount,
                failureCount
        );
        statusView.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        openSamsungDeepSleepSettings();
        finish();
    }

    private void launchPackage(PackageManager packageManager, String packageName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                startIntentSenderWithUserInitiatedPrivileges(
                        packageManager.getLaunchIntentSenderForPackage(packageName)
                );
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

    private void openSamsungDeepSleepSettings() {
        final Intent intent = new Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY")
                .setPackage(SAMSUNG_DEVICE_CARE_PACKAGE)
                .putExtra("activity_type", 1);
        startActivitySafely(intent);
    }

    private void startIntentSenderWithUserInitiatedPrivileges(IntentSender intentSender)
            throws IntentSender.SendIntentException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startIntentSender(intentSender, null, 0, 0, 0, createSenderActivityOptions());
        } else {
            intentSender.sendIntent(this, 0, null, null, null);
        }
    }

    private boolean startActivitySafely(Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                final ActivityOptions creatorOptions = ActivityOptions.makeBasic()
                        .setPendingIntentCreatorBackgroundActivityStartMode(
                                backgroundActivityStartMode()
                        );
                final PendingIntent pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE,
                        creatorOptions.toBundle()
                );
                pendingIntent.send(this, 0, null, null, null, null, createSenderActivityOptions());
            } else {
                startActivity(intent);
            }
            return true;
        } catch (ActivityNotFoundException | PendingIntent.CanceledException | SecurityException ignored) {
            return false;
        }
    }

    @SuppressLint("NewApi")
    private Bundle createSenderActivityOptions() {
        return ActivityOptions.makeBasic()
                .setPendingIntentBackgroundActivityStartMode(backgroundActivityStartMode())
                .toBundle();
    }

    @SuppressLint("NewApi")
    @SuppressWarnings("deprecation")
    private int backgroundActivityStartMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            return ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS;
        }
        return ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED;
    }

    private static List<String> findDisabledPlayStorePackages(PackageManager packageManager) {
        final List<String> packages = new ArrayList<>();
        for (ApplicationInfo applicationInfo : getInstalledApplications(packageManager)) {
            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    && !applicationInfo.enabled
                    && isInstalledFromPlayStore(packageManager, applicationInfo.packageName)) {
                packages.add(applicationInfo.packageName);
            }
        }
        return packages;
    }

    private static boolean isApplicationDisabled(PackageManager packageManager, String packageName) {
        try {
            final ApplicationInfo applicationInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationInfo = packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0)
                );
            } else {
                applicationInfo = getApplicationInfoLegacy(packageManager, packageName);
            }
            return !applicationInfo.enabled;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private static ApplicationInfo getApplicationInfoLegacy(
            PackageManager packageManager,
            String packageName
    ) throws PackageManager.NameNotFoundException {
        return packageManager.getApplicationInfo(packageName, 0);
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
