package net.nhiroki.bluelineconsole.applock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import net.nhiroki.bluelineconsole.commands.logs.AppLogger;

public class PackageAddedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || context == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            Uri data = intent.getData();
            if (data != null) {
                String packageName = data.getSchemeSpecificPart();
                if (packageName != null && !packageName.isEmpty()) {
                    AppLogger.i("APPLOCK", "PackageAddedReceiver: Newly installed package detected: " + packageName);
                    AppLockManager.getInstance().onPackageInstalled(context, packageName);
                }
            }
        }
    }
}
