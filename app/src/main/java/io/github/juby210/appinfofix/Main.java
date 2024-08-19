package io.github.juby210.appinfofix;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

@SuppressLint("DiscouragedApi")
public final class Main implements IXposedHookLoadPackage {
    private static final String settingsPackage = "com.android.settings";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(settingsPackage)) {
            hookLauncher(lpparam.classLoader);
            return;
        }

        var cl = lpparam.classLoader;
        hookComposeSettings(cl);

        var hasPackageName =
            XposedHelpers.findClassIfExists("com.android.settings.applications.appinfo.AppPackageNamePreferenceController", cl) != null;

        var mParent = setFAccessible(XposedHelpers.findClass("com.android.settings.applications.appinfo.AppInfoPreferenceControllerBase", cl)
            .getDeclaredField("mParent"));
        var getPackageInfo = setMAccessible(mParent.getType().getDeclaredMethod("getPackageInfo"));

        XposedBridge.hookMethod(
            XposedHelpers.findClass("com.android.settings.applications.appinfo.AppVersionPreferenceController", cl)
                .getDeclaredMethod("getSummary"),
            new XC_MethodHook() {
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    var info = (PackageInfo) getPackageInfo.invoke(mParent.get(param.thisObject));
                    if (info != null) param.setResult(param.getResult() + " (" + info.getLongVersionCode() + ")"
                        + (hasPackageName ? "" : "\n\n" + info.packageName));
                }
            }
        );

        // add disable/enable button
        var c = XposedHelpers.findClass("com.android.settings.applications.appinfo.AppButtonsPreferenceController", cl);
        var mAppEntry = setFAccessible(c.getDeclaredField("mAppEntry"));
        var mInfo = setFAccessible(mAppEntry.getType().getDeclaredField("info"));

        var mHomePackages = setFAccessible(c.getDeclaredField("mHomePackages"));
        var mActivity = setFAccessible(c.getDeclaredField("mActivity"));
        var mPm = setFAccessible(c.getDeclaredField("mPm"));
        var mPackageInfo = setFAccessible(c.getDeclaredField("mPackageInfo"));
        var isSystemPackage = setMAccessible(c.getDeclaredMethod("isSystemPackage", Resources.class, PackageManager.class, PackageInfo.class));

        var mButtonsPref = setFAccessible(c.getDeclaredField("mButtonsPref"));
        var mIsDisabledUntilUsed = setMAccessible(c.getDeclaredMethod("isDisabledUntilUsed"));
        var mApplicationFeatureProvider = setFAccessible(c.getDeclaredField("mApplicationFeatureProvider"));
        var getKeepEnabledPackages = setMAccessible(mApplicationFeatureProvider.getType().getDeclaredMethod("getKeepEnabledPackages"));

        var buttonsPrefClass = mButtonsPref.getType();
        var setButton4Text = setMAccessible(buttonsPrefClass.getDeclaredMethod("setButton4Text", int.class));
        var setButton4Icon = setMAccessible(buttonsPrefClass.getDeclaredMethod("setButton4Icon", int.class));
        var setButton4OnClickListener = setMAccessible(buttonsPrefClass.getDeclaredMethod("setButton4OnClickListener", View.OnClickListener.class));

        var disableChangerRunnable = XposedHelpers
            .findClass("com.android.settings.applications.appinfo.AppButtonsPreferenceController$DisableChangerRunnable", cl)
            .getDeclaredConstructor(c, PackageManager.class, String.class, int.class);
        disableChangerRunnable.setAccessible(true);

        var mButton4Info = setFAccessible(buttonsPrefClass.getDeclaredField("mButton4Info"));
        var mIsEnabled = setFAccessible(mButton4Info.getType().getDeclaredField("mIsEnabled"));
        var notifyChanged = setMAccessible(buttonsPrefClass.getDeclaredMethod("notifyChanged"));

        XposedBridge.hookMethod(c.getDeclaredMethod("updateUninstallButton"), new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var _this = param.thisObject;
                var info = (ApplicationInfo) mInfo.get(mAppEntry.get(_this));
                if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) return;

                var packageName = info.packageName;
                var homePackages = (Set<?>) mHomePackages.get(_this);
                if (homePackages.contains(packageName)) return;

                var ctx = (Context) mActivity.get(_this);
                var res = ctx.getResources();
                var pm = (PackageManager) mPm.get(_this);
                if (isSystemPackage.invoke(_this, res, pm, mPackageInfo.get(_this)) == Boolean.TRUE) return;

                var isDisabledUntilUsed = (boolean) mIsDisabledUntilUsed.invoke(_this);
                var enabled = info.enabled && !isDisabledUntilUsed;
                var disableable = !enabled || !((Set<?>) getKeepEnabledPackages.invoke(mApplicationFeatureProvider.get(_this))).contains(packageName);
                var buttonsPref = mButtonsPref.get(_this);
                setButton4Text.invoke(buttonsPref, getStringId(res, enabled ? "disable_text" : "enable_text"));
                setButton4Icon.invoke(buttonsPref, getDrawableId(res, enabled ? "ic_settings_disable" : "ic_settings_enable"));
                setButton4OnClickListener.invoke(buttonsPref, (View.OnClickListener) v -> {
                    try {
                        AsyncTask.execute((Runnable) disableChangerRunnable.newInstance(_this, pm, packageName, info.enabled ? 3 : 0));
                    } catch (Throwable e) {
                        XposedBridge.log(e);
                    }
                });
                var buttonInfo = mButton4Info.get(buttonsPref);
                if (disableable != mIsEnabled.getBoolean(buttonInfo)) {
                    mIsEnabled.setBoolean(buttonInfo, disableable);
                    notifyChanged.invoke(buttonsPref);
                }
            }
        });
    }

    // Android 14+ compose settings
    public static void hookComposeSettings(ClassLoader cl) throws Throwable {
        var c = XposedHelpers.findClassIfExists("com.android.settingslib.spaprivileged.template.app.AppInfoProvider$Companion", cl);
        if (c == null) return; // no compose settings
        XposedBridge.hookMethod(c.getDeclaredMethod("getVersionNameBidiWrapped", PackageInfo.class), new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam param) {
                var info = (PackageInfo) param.args[0];
                param.setResult(param.getResult() + " (" + info.getLongVersionCode() + ")");
            }
        });

        XposedBridge.hookMethod(
            XposedHelpers.findClass("com.android.settings.spa.app.appinfo.AppDisableButton", cl)
                .getDeclaredMethod("getActionButton", ApplicationInfo.class, XposedHelpers.findClass("androidx.compose.runtime.Composer", cl), int.class),
            new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam param) {
                    var info = (ApplicationInfo) param.args[0];
                    if ((info.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        var info2 = new ApplicationInfo(info);
                        info2.flags |= ApplicationInfo.FLAG_SYSTEM;
                        param.args[0] = info2;
                    }
                }
            }
        );
    }

    public static String KEY_PACKAGE_NAME = "pref_app_info_package_name";

    public static void hookLauncher(ClassLoader cl) throws Throwable {
        var c = XposedHelpers.findClassIfExists("com.android.launcher3.customization.InfoBottomSheet$PrefsFragment", cl);
        if (c == null) return;

        var charSeq = CharSequence.class;
        var intClass = int.class;

        var prefFrag = XposedHelpers.findClass("androidx.preference.PreferenceFragment", cl);
        var getPrefScreen = setMAccessible(prefFrag.getDeclaredMethod("getPreferenceScreen"));
        var findPref = setMAccessible(prefFrag.getDeclaredMethod("findPreference", charSeq));
        var prefGroup = XposedHelpers.findClass("androidx.preference.PreferenceGroup", cl);
        var preference = XposedHelpers.findClass("androidx.preference.Preference", cl);
        var addPref = setMAccessible(prefGroup.getDeclaredMethod("addPreference", preference));

        var newPref = preference.getDeclaredConstructor(Context.class);
        newPref.setAccessible(true);
        var getContext = setMAccessible(preference.getDeclaredMethod("getContext"));
        var setKey = setMAccessible(preference.getDeclaredMethod("setKey", String.class));
        var setTitle = setMAccessible(preference.getDeclaredMethod("setTitle", charSeq));
        var setPersistent = setMAccessible(preference.getDeclaredMethod("setPersistent", boolean.class));
        var setOrder = setMAccessible(preference.getDeclaredMethod("setOrder", intClass));
        var setIcon = setMAccessible(preference.getDeclaredMethod("setIcon", intClass));
        var setLayoutResource = setMAccessible(preference.getDeclaredMethod("setLayoutResource", intClass));
        var setSummary = setMAccessible(preference.getDeclaredMethod("setSummary", charSeq));

        var itemInfo = XposedHelpers.findClass("com.android.launcher3.model.data.ItemInfo", cl);
        var getTargetPackage = setMAccessible(itemInfo.getDeclaredMethod("getTargetPackage"));

        int icon;
        try {
            icon = setFAccessible(XposedHelpers.findClass("com.android.launcher3.R$drawable", cl)
                .getDeclaredField("app_manager")).getInt(null);
        } catch (Throwable ignored) {
            icon = 0;
        }
        var layout = setFAccessible(XposedHelpers.findClass("com.android.launcher3.R$layout", cl)
            .getDeclaredField("settings_layout")).getInt(null);

        var finalIcon = icon;
        XposedBridge.hookMethod(c.getDeclaredMethod("onCreatePreferences", Bundle.class, String.class), new XC_MethodHook() {
            /** @noinspection JavaReflectionInvocation*/
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var screen = getPrefScreen.invoke(param.thisObject);
                var pref = newPref.newInstance(getContext.invoke(screen));
                setKey.invoke(pref, KEY_PACKAGE_NAME);
                setTitle.invoke(pref, "Package name");
                setPersistent.invoke(pref, Boolean.FALSE);
                setOrder.invoke(pref, 3);
                if (finalIcon != 0) setIcon.invoke(pref, finalIcon);
                setLayoutResource.invoke(pref, layout);
                addPref.invoke(screen, pref);
            }
        });

        XposedBridge.hookMethod(c.getDeclaredMethod("loadForApp", itemInfo), new XC_MethodHook() {
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var pref = findPref.invoke(param.thisObject, KEY_PACKAGE_NAME);
                setSummary.invoke(pref, getTargetPackage.invoke(param.args[0]));
            }
        });
    }

    public static Field setFAccessible(Field f) {
        f.setAccessible(true);
        return f;
    }

    public static Method setMAccessible(Method m) {
        m.setAccessible(true);
        return m;
    }

    public static int getStringId(Resources res, String name) {
        return res.getIdentifier(name, "string", settingsPackage);
    }

    public static int getDrawableId(Resources res, String name) {
        return res.getIdentifier(name, "drawable", settingsPackage);
    }
}
