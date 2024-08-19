# AppInfoFix
Small Xposed module that shows more info about application in AOSP app info.

Tested on Android 10-15 Beta 4, however should also work on Android 9.

### Features
- show version code
- show package name (if you don't have it - AOSP Android 14 QPR2 finally added it by default)
- add disable/enable button on nonsystem apps 

Also adds package name on launchers with [Shade Launcher's AppInfo Bottom Sheet](https://github.com/crdroidandroid/android_packages_apps_Launcher3/commit/f0ce2572ed6b68da928fb3058743684560c01427), so if your launcher has this feature enable the module for the launcher as well. This mostly applies to crDroid based ROMs.
