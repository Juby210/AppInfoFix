package io.github.juby210.appinfofix;

import android.content.pm.PackageInfo;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Main implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals("com.android.settings")) return;

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
    }
}
