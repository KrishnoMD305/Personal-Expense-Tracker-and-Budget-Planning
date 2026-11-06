package com.example.expensetracker;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/** The app's version name (from the manifest/Gradle), with a safe fallback. */
final class BuildConfigInfo {
    private BuildConfigInfo() {}

    static String versionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }
}
