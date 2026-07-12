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
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final String PLAY_STORE_PACKAGE = "com.android.vending";
    private static final String SAMSUNG_DEVICE_CARE_PACKAGE = "com.samsung.android.lool";
    private static final int MAX_ATTEMPTS = 3;
    private static final long LAUNCH_INTERVAL_MS = 450;
    private static final long VERIFICATION_DELAY_MS = 2_000;

    private static final int COLOR_BACKGROUND = Color.rgb(10, 15, 13);
    private static final int COLOR_SURFACE = Color.rgb(17, 29, 23);
    private static final int COLOR_PRIMARY = Color.rgb(102, 255, 167);
    private static final int COLOR_TEXT = Color.rgb(222, 240, 229);
    private static final int COLOR_MUTED = Color.rgb(137, 161, 146);
    private static final int COLOR_WARNING = Color.rgb(255, 197, 92);
    private static final int COLOR_ERROR = Color.rgb(255, 123, 111);

    private final ArrayDeque<String> pendingPackages = new ArrayDeque<>();
    private final Map<String, PackageEntry> packageEntries = new LinkedHashMap<>();

    private Handler handler;
    private PackageManager packageManager;
    private TextView statusView;
    private LinearLayout appList;
    private Button activateButton;
    private Button rescanButton;
    private Button playStoreButton;
    private Button deepSleepButton;
    private int attempt;
    private int launchedInAttempt;
    private boolean activationInProgress;
    private String statusMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        handler = new Handler(Looper.getMainLooper());
        packageManager = getPackageManager();
        configureWindow();
        createConsole();
        scanPackages();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        renderConsole();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusView != null) {
            renderConsole();
        }
    }

    @Override
    protected void onDestroy() {
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(COLOR_BACKGROUND);
        getWindow().setNavigationBarColor(COLOR_BACKGROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getWindow().setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().getInsetsController().setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            );
        }
    }

    private void createConsole() {
        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);
        applySystemBarInsets(root);

        final TextView title = consoleText(20, COLOR_PRIMARY, Typeface.BOLD);
        title.setText(R.string.console_title);
        root.addView(title);

        final TextView target = consoleText(12, COLOR_MUTED, Typeface.NORMAL);
        target.setText(R.string.console_target);
        target.setPadding(0, dp(6), 0, dp(16));
        root.addView(target);

        final View divider = new View(this);
        divider.setBackgroundColor(COLOR_PRIMARY);
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        statusView = consoleText(14, COLOR_TEXT, Typeface.BOLD);
        statusView.setPadding(0, dp(14), 0, dp(12));
        root.addView(statusView);

        final ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        appList = new LinearLayout(this);
        appList.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(appList);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        final View footerDivider = new View(this);
        footerDivider.setBackgroundColor(COLOR_MUTED);
        root.addView(footerDivider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
        ));

        activateButton = consoleButton(R.string.action_activate, COLOR_PRIMARY);
        activateButton.setOnClickListener(view -> startActivation());
        root.addView(activateButton, buttonLayoutParams(dp(12)));

        playStoreButton = consoleButton(R.string.action_play_store, COLOR_PRIMARY);
        playStoreButton.setOnClickListener(view -> openPlayStore(packageManager));
        root.addView(playStoreButton, buttonLayoutParams(dp(8)));

        deepSleepButton = consoleButton(R.string.action_deep_sleep_settings, COLOR_WARNING);
        deepSleepButton.setOnClickListener(view -> openSamsungDeepSleepSettings());
        root.addView(deepSleepButton, buttonLayoutParams(dp(8)));

        rescanButton = consoleButton(R.string.action_rescan, COLOR_MUTED);
        rescanButton.setOnClickListener(view -> scanPackages());
        root.addView(rescanButton, buttonLayoutParams(dp(8)));

        setContentView(root);
    }

    private void applySystemBarInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                final android.graphics.Insets systemBars = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                view.setPadding(
                        dp(20) + systemBars.left,
                        dp(24) + systemBars.top,
                        dp(20) + systemBars.right,
                        dp(16) + systemBars.bottom
                );
            } else {
                view.setPadding(
                        dp(20) + windowInsets.getSystemWindowInsetLeft(),
                        dp(24) + windowInsets.getSystemWindowInsetTop(),
                        dp(20) + windowInsets.getSystemWindowInsetRight(),
                        dp(16) + windowInsets.getSystemWindowInsetBottom()
                );
            }
            return windowInsets;
        });
        root.requestApplyInsets();
    }

    private LinearLayout.LayoutParams buttonLayoutParams(int topMargin) {
        final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        params.topMargin = topMargin;
        return params;
    }

    private Button consoleButton(int textRes, int accentColor) {
        final Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(textRes);
        button.setTextColor(accentColor);
        button.setTextSize(13);
        button.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(consoleSurface(accentColor));
        return button;
    }

    private TextView consoleText(int textSize, int color, int style) {
        final TextView textView = new TextView(this);
        textView.setTextColor(color);
        textView.setTextSize(textSize);
        textView.setTypeface(Typeface.MONOSPACE, style);
        textView.setGravity(Gravity.START);
        return textView;
    }

    private GradientDrawable consoleSurface(int borderColor) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(COLOR_SURFACE);
        drawable.setStroke(dp(1), borderColor);
        drawable.setCornerRadius(dp(3));
        return drawable;
    }

    private void scanPackages() {
        if (activationInProgress) {
            return;
        }

        packageEntries.clear();
        final List<ApplicationInfo> applications = findDisabledPlayStoreApplications(packageManager);
        Collections.sort(applications, Comparator.comparing(
                applicationInfo -> applicationLabel(applicationInfo).toLowerCase(Locale.ROOT)
        ));
        for (ApplicationInfo applicationInfo : applications) {
            final String label = applicationLabel(applicationInfo);
            packageEntries.put(applicationInfo.packageName, new PackageEntry(
                    applicationInfo.packageName,
                    label,
                    AppStatus.QUEUED
            ));
        }

        statusMessage = packageEntries.isEmpty()
                ? getString(R.string.status_no_apps)
                : getString(R.string.status_ready, packageEntries.size());
        renderConsole();
    }

    private String applicationLabel(ApplicationInfo applicationInfo) {
        final CharSequence label = packageManager.getApplicationLabel(applicationInfo);
        return label == null || label.length() == 0 ? applicationInfo.packageName : label.toString();
    }

    private void startActivation() {
        if (activationInProgress) {
            return;
        }

        final List<String> queuedPackages = packagesWithStatus(AppStatus.QUEUED);
        if (queuedPackages.isEmpty()) {
            statusMessage = getString(R.string.status_no_apps);
            renderConsole();
            return;
        }

        activationInProgress = true;
        attempt = 0;
        startAttempt(queuedPackages);
    }

    private void startAttempt(List<String> packages) {
        attempt++;
        launchedInAttempt = 0;
        pendingPackages.clear();
        pendingPackages.addAll(packages);
        for (String packageName : packages) {
            final PackageEntry entry = packageEntries.get(packageName);
            if (entry != null) {
                entry.status = AppStatus.QUEUED;
            }
        }
        launchNextPackage();
    }

    private void launchNextPackage() {
        final String packageName = pendingPackages.pollFirst();
        if (packageName == null) {
            handler.postDelayed(this::verifyPackages, VERIFICATION_DELAY_MS);
            return;
        }

        launchedInAttempt++;
        final PackageEntry entry = packageEntries.get(packageName);
        if (entry != null) {
            entry.status = AppStatus.ACTIVATING;
        }
        statusMessage = getString(
                R.string.status_activating,
                launchedInAttempt,
                launchedInAttempt + pendingPackages.size(),
                attempt,
                MAX_ATTEMPTS
        );
        renderConsole();
        launchPackage(packageManager, packageName);
        handler.postDelayed(this::launchNextPackage, LAUNCH_INTERVAL_MS);
    }

    private void verifyPackages() {
        final List<String> stillDisabled = new ArrayList<>();
        for (PackageEntry entry : packageEntries.values()) {
            if (entry.status == AppStatus.ACTIVE || entry.status == AppStatus.FAILED) {
                continue;
            }

            if (isApplicationDisabled(packageManager, entry.packageName)) {
                entry.status = AppStatus.QUEUED;
                stillDisabled.add(entry.packageName);
            } else {
                entry.status = AppStatus.ACTIVE;
            }
        }

        if (stillDisabled.isEmpty()) {
            completeActivation();
        } else if (attempt < MAX_ATTEMPTS) {
            startAttempt(stillDisabled);
        } else {
            for (String packageName : stillDisabled) {
                final PackageEntry entry = packageEntries.get(packageName);
                if (entry != null) {
                    entry.status = AppStatus.FAILED;
                }
            }
            completeActivation();
        }
    }

    private void completeActivation() {
        activationInProgress = false;
        final int failures = packagesWithStatus(AppStatus.FAILED).size();
        statusMessage = failures == 0
                ? getString(R.string.status_complete, packageEntries.size())
                : getResources().getQuantityString(R.plurals.status_failed, failures, failures);
        renderConsole();
        returnToConsole();
    }

    private void returnToConsole() {
        final Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivitySafely(intent);
    }

    private List<String> packagesWithStatus(AppStatus status) {
        final List<String> packages = new ArrayList<>();
        for (PackageEntry entry : packageEntries.values()) {
            if (entry.status == status) {
                packages.add(entry.packageName);
            }
        }
        return packages;
    }

    private void renderConsole() {
        if (statusView == null || appList == null) {
            return;
        }

        statusView.setText(statusMessage == null ? getString(R.string.status_scanning) : statusMessage);
        appList.removeAllViews();
        if (packageEntries.isEmpty()) {
            final TextView empty = consoleText(14, COLOR_MUTED, Typeface.NORMAL);
            empty.setText(R.string.console_no_apps);
            empty.setPadding(0, dp(8), 0, dp(8));
            appList.addView(empty);
        } else {
            for (PackageEntry entry : packageEntries.values()) {
                appList.addView(packageRow(entry));
            }
        }

        final boolean hasQueuedApps = !packagesWithStatus(AppStatus.QUEUED).isEmpty();
        final boolean hasFailures = !packagesWithStatus(AppStatus.FAILED).isEmpty();
        activateButton.setEnabled(!activationInProgress && hasQueuedApps);
        rescanButton.setEnabled(!activationInProgress);
        playStoreButton.setEnabled(!activationInProgress);
        deepSleepButton.setVisibility(View.VISIBLE);
    }

    private TextView packageRow(PackageEntry entry) {
        final TextView row = consoleText(14, entry.status.color, Typeface.NORMAL);
        row.setText(String.format(
                Locale.getDefault(),
                "%s %s\n    %s",
                entry.status.marker,
                entry.label,
                entry.packageName
        ));
        row.setPadding(dp(2), dp(7), dp(2), dp(7));
        return row;
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

    private static List<ApplicationInfo> findDisabledPlayStoreApplications(PackageManager packageManager) {
        final List<ApplicationInfo> applications = new ArrayList<>();
        for (ApplicationInfo applicationInfo : getInstalledApplications(packageManager)) {
            if ((applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) == 0
                    && !applicationInfo.enabled
                    && isInstalledFromPlayStore(packageManager, applicationInfo.packageName)) {
                applications.add(applicationInfo);
            }
        }
        return applications;
    }

    private static boolean isApplicationDisabled(PackageManager packageManager, String packageName) {
        try {
            final ApplicationInfo applicationInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                applicationInfo = packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS)
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
        return packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS);
    }

    @SuppressWarnings("deprecation")
    private static Iterable<ApplicationInfo> getInstalledApplications(PackageManager packageManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return packageManager.getInstalledApplications(
                    PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_DISABLED_COMPONENTS)
            );
        }
        return packageManager.getInstalledApplications(PackageManager.MATCH_DISABLED_COMPONENTS);
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

    private static final class PackageEntry {
        private final String packageName;
        private final String label;
        private AppStatus status;

        private PackageEntry(String packageName, String label, AppStatus status) {
            this.packageName = packageName;
            this.label = label;
            this.status = status;
        }
    }

    private enum AppStatus {
        QUEUED("[  ]", COLOR_MUTED),
        ACTIVATING("[>>]", COLOR_WARNING),
        ACTIVE("[✅]", COLOR_PRIMARY),
        FAILED("[!!]", COLOR_ERROR);

        private final String marker;
        private final int color;

        AppStatus(String marker, int color) {
            this.marker = marker;
            this.color = color;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
