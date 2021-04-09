package io.github.juby210.appinfofix;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.util.Pair;
import android.view.View;

import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Main implements IXposedHookLoadPackage {
    private static final String settingsPackage = "com.android.settings";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals(settingsPackage)) return;

        boolean hasPackageName =
                XposedHelpers.findClassIfExists("com.android.settings.applications.appinfo.AppPackageNamePreferenceController", lpparam.classLoader) != null;

        XposedHelpers.findAndHookMethod(
                "com.android.settings.applications.appinfo.AppVersionPreferenceController",
                lpparam.classLoader,
                "getSummary",
                new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object mParent = XposedHelpers.getObjectField(param.thisObject, "mParent");
                        PackageInfo info = (PackageInfo) XposedHelpers.callMethod(mParent, "getPackageInfo");
                        param.setResult(param.getResult() + " (" + info.getLongVersionCode() + ")"
                                + (hasPackageName ? "" : "\n\n" + info.packageName));
                    }
                }
        );

        // add disable/enable button
        XposedHelpers.findAndHookMethod(
                "com.android.settings.applications.appinfo.AppButtonsPreferenceController",
                lpparam.classLoader,
                "updateUninstallButton",
                new XC_MethodHook() {
                    @SuppressWarnings({"deprecation", "unchecked"})
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object mAppEntry = XposedHelpers.getObjectField(param.thisObject, "mAppEntry");
                        ApplicationInfo info = (ApplicationInfo) XposedHelpers.getObjectField(mAppEntry, "info");
                        if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return;
                        Set<String> mHomePackages = (Set<String>) XposedHelpers.getObjectField(param.thisObject, "mHomePackages");
                        Object mActivity = XposedHelpers.getObjectField(param.thisObject, "mActivity");
                        Resources res = (Resources) XposedHelpers.callMethod(mActivity, "getResources");
                        PackageManager mPm = (PackageManager) XposedHelpers.getObjectField(param.thisObject, "mPm");
                        boolean isSystemPackage = (boolean) XposedHelpers.callMethod(
                                param.thisObject,
                                "isSystemPackage",
                                res, mPm,
                                XposedHelpers.getObjectField(param.thisObject, "mPackageInfo")
                        );
                        if (mHomePackages.contains(info.packageName) || isSystemPackage) return;

                        Object mButtonsPref = XposedHelpers.getObjectField(param.thisObject, "mButtonsPref");
                        boolean isDisabledUntilUsed = (boolean) XposedHelpers.callMethod(param.thisObject, "isDisabledUntilUsed");
                        boolean enabled = info.enabled && !isDisabledUntilUsed;
                        boolean disableable;
                        if (enabled) {
                            Set<String> keepEnabledPackages = (Set<String>) XposedHelpers.callMethod(XposedHelpers.getObjectField(param.thisObject, "mApplicationFeatureProvider"), "getKeepEnabledPackages");
                            disableable = !keepEnabledPackages.contains(info.packageName);
                        } else disableable = true;
                        XposedHelpers.callMethod(mButtonsPref, "setButton4Text",
                                enabled ? getStringId(res, "disable_text") : getStringId(res, "enable_text"));
                        XposedHelpers.callMethod(mButtonsPref, "setButton4Icon",
                                enabled ? getDrawableId(res, "ic_settings_disable") : getDrawableId(res, "ic_settings_enable"));
                        setButton4Enabled(mButtonsPref, disableable);

                        XposedHelpers.callMethod(mButtonsPref, "setButton4OnClickListener", (View.OnClickListener) v -> {
                            XposedHelpers.callMethod(
                                    XposedHelpers.getObjectField(param.thisObject, "mMetricsFeatureProvider"),
                                    "action",
                                    mActivity,
                                    info.enabled ? 874 : 875,
                                    new Pair[0]
                            );
                            AsyncTask.execute((Runnable) XposedHelpers.newInstance(
                                    XposedHelpers.findClass(
                                            "com.android.settings.applications.appinfo.AppButtonsPreferenceController$DisableChangerRunnable",
                                            lpparam.classLoader
                                    ),
                                    param.thisObject, mPm, info.packageName, info.enabled ? 3 : 0
                            ));
                        });
                    }
                }
        );
    }

    public static int getStringId(Resources res, String name) {
        return res.getIdentifier(name, "string", settingsPackage);
    }
    public static int getDrawableId(Resources res, String name) {
        return res.getIdentifier(name, "drawable", settingsPackage);
    }
    public static void setButton4Enabled(Object mButtonsPref, boolean enabled) {
        Object mButton4Info = XposedHelpers.getObjectField(mButtonsPref, "mButton4Info");
        boolean mIsEnabled = XposedHelpers.getBooleanField(mButton4Info, "mIsEnabled");
        if (enabled != mIsEnabled) {
            XposedHelpers.setBooleanField(mButton4Info, "mIsEnabled", enabled);
            XposedHelpers.callMethod(mButtonsPref, "notifyChanged");
        }
    }
}
