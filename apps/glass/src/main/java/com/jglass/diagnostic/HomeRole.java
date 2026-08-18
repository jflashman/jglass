package com.jglass.diagnostic;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

final class HomeRole {
    private HomeRole() {
    }

    static Intent homeIntent() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        return intent;
    }

    static boolean isJGlassDefaultHome(Context context) {
        PackageManager packages = context.getPackageManager();
        ResolveInfo resolved = packages.resolveActivity(
                homeIntent(), PackageManager.MATCH_DEFAULT_ONLY);
        return resolved != null
                && resolved.activityInfo != null
                && context.getPackageName().equals(resolved.activityInfo.packageName)
                && JGlassHomeActivity.class.getName().equals(resolved.activityInfo.name);
    }
}
