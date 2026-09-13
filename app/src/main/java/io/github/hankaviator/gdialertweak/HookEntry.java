package io.github.hankaviator.gdialertweak;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.SystemClock;
import android.telecom.TelecomManager;

import java.util.List;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    private static final String DIALER = "com.google.android.dialer";
    private static final String STOCK_INCALL_UI = "com.android.incallui";
    private static final String STOCK_INCALL_ACTIVITY = "com.android.incallui.InCallActivity";
    private static final String RECEIVER = "com.android.dialer.incall.statusbarnotification.buttonintent.impl.NotificationBroadcastReceiver_Receiver";
    private static final String INCALL_ACTIVITY = "com.android.dialer.incall.activity.ui.InCallActivity";
    private static final String ACTION_EXTRA = "com.android.dialer.incall.statusbarnotification.buttonintentActionValue";
    private static final String EXTRA_SHOW_DIALPAD = "InCallActivity.show_dialpad";
    private static final String EXTRA_NEW_OUTGOING = "InCallActivity.new_outgoing_call";
    private static final String EXTRA_FULL_SCREEN = "InCallActivity.for_full_screen";
    private static final String EXTRA_NOTIFICATION_BUTTON = "InCallActivity.for_notification_button";
    private static final String EXTRA_NOTIFICATION_CONTENT = "InCallActivity.for_notification_content";
    private static final String EXTRA_MODULE_NOTIFICATION_CONTENT =
            "io.github.hankaviator.gdialertweak.NOTIFICATION_CONTENT";
    private static volatile long suppressUntil;
    private static volatile boolean hardSuppressInCallUi;
    private static final Set<Activity> notificationAnswerActivities =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Activity> backgroundedStockActivities =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static XSharedPreferences preferences;

    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        if (!DIALER.equals(load.packageName) && !STOCK_INCALL_UI.equals(load.packageName)) return;
        preferences = new XSharedPreferences("io.github.hankaviator.gdialertweak", Prefs.FILE);
        preferences.reload();
        log("preferences readable=" + preferences.getFile().canRead()
                + ", keepCurrentApp=" + preferences.getBoolean(Prefs.KEEP_APP, true)
                + ", recording=" + preferences.getBoolean(Prefs.RECORDING, false));
        if (STOCK_INCALL_UI.equals(load.packageName)) {
            hookStockInCallActivityLaunch(load.classLoader);
            hookStockInCallActivity(load.classLoader);
            log("loaded in Xiaomi InCallUI " + load.processName);
            return;
        }
        hookReceiverOnlyNotificationAnswer(load.classLoader);
        hookNotificationContentMarker(load.classLoader);
        hookNotificationAnswer(load.classLoader);
        hookInternalInCallActivityLaunch(load.classLoader);
        hookInCallActivity(load.classLoader);
        hookRecordingEligibility(load.classLoader);
        log("loaded in Google Phone " + load.processName);
    }

    private static void hookStockInCallActivityLaunch(ClassLoader loader) {
        try {
            Class<?> instrumentation = XposedHelpers.findClass("android.app.Instrumentation", loader);
            XposedBridge.hookAllMethods(instrumentation, "execStartActivity", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Context context = param.args.length > 0 && param.args[0] instanceof Context
                            ? (Context) param.args[0] : null;
                    if (context == null || !shouldSuppressStockUi(context)) return;
                    for (Object argument : param.args) {
                        if (!(argument instanceof Intent)) continue;
                        ComponentName component = ((Intent) argument).getComponent();
                        if (component != null
                                && STOCK_INCALL_UI.equals(component.getPackageName())
                                && STOCK_INCALL_ACTIVITY.equals(component.getClassName())) {
                            param.setResult(null);
                            log("blocked Xiaomi InCallActivity launch; Google Phone is the default dialer");
                            return;
                        }
                    }
                }
            });
            log("Xiaomi InCallActivity launch blocker installed");
        } catch (Throwable error) {
            log("Xiaomi InCallActivity launch blocker unavailable", error);
        }
    }

    private static void hookStockInCallActivity(ClassLoader loader) {
        try {
            Class<?> activityClass = XposedHelpers.findClass(STOCK_INCALL_ACTIVITY, loader);
            XC_MethodHook suppressStockUi = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    backgroundStockInCallActivity((Activity) param.thisObject);
                }
            };
            XposedBridge.hookAllMethods(activityClass, "onCreate", suppressStockUi);
            XposedBridge.hookAllMethods(activityClass, "onResume", suppressStockUi);
            XposedBridge.hookAllMethods(activityClass, "onWindowFocusChanged", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(param.args[0])) {
                        backgroundStockInCallActivity((Activity) param.thisObject);
                    }
                }
            });
            log("Xiaomi InCallActivity suppressor installed");
        } catch (Throwable error) {
            log("Xiaomi InCallActivity suppressor unavailable", error);
        }
    }

    private static void backgroundStockInCallActivity(Activity activity) {
        if (activity.isFinishing() || !shouldSuppressStockUi(activity)) return;

        if (activity.moveTaskToBack(true)) {
            activity.overridePendingTransition(0, 0);
            if (backgroundedStockActivities.add(activity)) {
                log("backgrounded Xiaomi InCallActivity; Google Phone is the default dialer");
            }
        }
    }

    private static boolean shouldSuppressStockUi(Context context) {
        if (!enabled(Prefs.KEEP_APP, true)) return false;
        TelecomManager telecom = context.getSystemService(TelecomManager.class);
        return telecom != null && DIALER.equals(telecom.getDefaultDialerPackage());
    }

    private static void hookReceiverOnlyNotificationAnswer(ClassLoader loader) {
        try {
            // Google Phone 236: rcb.a(...) builds a receiver PendingIntent, while
            // rcb.b(...) builds an InCallActivity PendingIntent unless gaming mode is on.
            Class<?> buttonIntents = XposedHelpers.findClass("rcb", loader);
            XposedBridge.hookAllMethods(buttonIntents, "b", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!enabled(Prefs.KEEP_APP, true) || param.args.length != 2
                            || !(param.args[0] instanceof Enum<?>)) return;
                    String action = ((Enum<?>) param.args[0]).name();
                    if (!isAnswerEnum(action)) return;
                    PendingIntent receiverPendingIntent = (PendingIntent) XposedHelpers.callMethod(
                            param.thisObject, "a", param.args[0], param.args[1]);
                    param.setResult(receiverPendingIntent);
                    log("using receiver-only notification action for " + action);
                }
            });
            log("receiver-only notification answer hook installed");
        } catch (Throwable error) {
            log("receiver-only notification answer hook unavailable", error);
        }
    }

    private static boolean isAnswerEnum(String action) {
        return "ANSWER".equals(action) || "ANSWER_VIDEO".equals(action);
    }

    private static void hookNotificationContentMarker(ClassLoader loader) {
        try {
            // Google Phone's txk content-intent provider calls aghx.a with request code 0,
            // but Phone 236 leaves for_notification_content=false. Use a module-private
            // marker: Google's own flag enters a handler that requires InCallActivity.callId.
            Class<?> pendingIntents = XposedHelpers.findClass("aghx", loader);
            XposedBridge.hookAllMethods(pendingIntents, "a", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length != 4 || !(param.args[1] instanceof Integer)
                            || ((Integer) param.args[1]) != 0
                            || !(param.args[2] instanceof Intent)) return;
                    Intent intent = (Intent) param.args[2];
                    ComponentName component = intent.getComponent();
                    if (component != null && DIALER.equals(component.getPackageName())
                            && INCALL_ACTIVITY.equals(component.getClassName())
                            && Intent.ACTION_MAIN.equals(intent.getAction())) {
                        intent.putExtra(EXTRA_MODULE_NOTIFICATION_CONTENT, true);
                        log("marked ongoing-call notification content intent");
                    }
                }
            });
            log("notification content marker hook installed");
        } catch (Throwable error) {
            log("notification content marker hook unavailable", error);
        }
    }

    private static boolean enabled(String key, boolean defaultValue) {
        preferences.reload();
        return preferences.getBoolean(key, defaultValue);
    }

    private static void hookNotificationAnswer(ClassLoader loader) {
        try {
            Class<?> receiver = XposedHelpers.findClass(RECEIVER, loader);
            XposedBridge.hookMethod(receiver.getMethod("onReceive", Context.class, Intent.class), new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!RECEIVER.equals(param.thisObject.getClass().getName())
                            || !enabled(Prefs.KEEP_APP, true)) return;
                    Context context = (Context) param.args[0];
                    Intent intent = (Intent) param.args[1];
                    if (!isAnswerAction(intent) || shouldShowInCallUi(context)) return;
                    suppressUntil = SystemClock.elapsedRealtime() + 8_000L;
                    hardSuppressInCallUi = true;
                    log("receiver answer detected; internal in-call activity launches will be blocked");
                }
            });
        } catch (Throwable error) {
            log("notification hook unavailable", error);
        }
    }

    private static boolean isAnswerAction(Intent intent) {
        String action = intent.getAction();
        if (action != null && action.startsWith("com.android.dialer.incall.statusbarnotification.buttonintent")
                && action.contains("ANSWER")) return true;
        int value = intent.getIntExtra(ACTION_EXTRA, 0);
        return value == 1 || value == 2 || value == 6 || value == 7
                || value == 16 || value == 18 || value == 19;
    }

    private static boolean shouldShowInCallUi(Context context) {
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        if (keyguard != null && keyguard.isKeyguardLocked()) return true;

        String foreground = currentForegroundPackage(context);
        if (foreground == null || DIALER.equals(foreground)) return true;

        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo home = context.getPackageManager().resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        return home != null && home.activityInfo != null && foreground.equals(home.activityInfo.packageName);
    }

    private static String currentForegroundPackage(Context context) {
        try {
            Class<?> processManager = XposedHelpers.findClass("miui.process.ProcessManager", null);
            Object info = XposedHelpers.callStaticMethod(processManager, "getForegroundInfo");
            String current = (String) XposedHelpers.getObjectField(
                    info, "mForegroundPackageName");
            if (current != null) return current;
        } catch (Throwable error) {
            log("HyperOS current foreground unavailable; using task fallback", error);
        }
        return foregroundPackage(context);
    }

    private static void hookInternalInCallActivityLaunch(ClassLoader loader) {
        try {
            Class<?> instrumentation = XposedHelpers.findClass("android.app.Instrumentation", loader);
            XposedBridge.hookAllMethods(instrumentation, "execStartActivity", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    if (!hardSuppressInCallUi || !enabled(Prefs.KEEP_APP, true)) return;
                    for (Object argument : param.args) {
                        if (!(argument instanceof Intent)) continue;
                        Intent intent = (Intent) argument;
                        ComponentName component = intent.getComponent();
                        if (component != null && DIALER.equals(component.getPackageName())
                                && INCALL_ACTIVITY.equals(component.getClassName())
                                && !isExplicitUiLaunch(intent)) {
                            param.setResult(null);
                            log("blocked Google Phone's internal InCallActivity launch after receiver answer");
                            return;
                        }
                    }
                }
            });
            log("internal InCallActivity launch blocker installed");
        } catch (Throwable error) {
            log("internal InCallActivity launch blocker unavailable", error);
        }
    }

    @SuppressWarnings("deprecation")
    private static String foregroundPackage(Context context) {
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            List<ActivityManager.RunningTaskInfo> tasks = manager == null ? null : manager.getRunningTasks(1);
            ComponentName top = tasks == null || tasks.isEmpty() ? null : tasks.get(0).topActivity;
            return top == null ? null : top.getPackageName();
        } catch (Throwable error) {
            log("could not determine foreground task", error);
            return null;
        }
    }

    private static void hookInCallActivity(ClassLoader loader) {
        try {
            Class<?> activityClass = XposedHelpers.findClass(INCALL_ACTIVITY, loader);
            XC_MethodHook rememberAnswerLaunch = new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    Activity activity = (Activity) param.thisObject;
                    Intent intent = param.args.length > 0 && param.args[0] instanceof Intent
                            ? (Intent) param.args[0] : activity.getIntent();
                    if (isExplicitUiLaunch(intent)) {
                        notificationAnswerActivities.remove(activity);
                        hardSuppressInCallUi = false;
                        log("explicit in-call UI launch; hard suppression cleared");
                        return;
                    }
                    if (isNotificationAnswerLaunch(intent)) {
                        notificationAnswerActivities.add(activity);
                        log("notification answer activity launch detected");
                    }
                }

                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Activity activity = (Activity) param.thisObject;
                    if (notificationAnswerActivities.contains(activity) || hardSuppressInCallUi) {
                        hardSuppressActivity(activity, "activity launch");
                    }
                }
            };
            XposedBridge.hookAllMethods(activityClass, "onCreate", rememberAnswerLaunch);
            XposedBridge.hookAllMethods(activityClass, "onNewIntent", rememberAnswerLaunch);
            XposedBridge.hookAllMethods(activityClass, "onResume", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    long deadline = suppressUntil;
                    Activity activity = (Activity) param.thisObject;
                    if (!enabled(Prefs.KEEP_APP, true)) return;

                    Intent intent = activity.getIntent();
                    boolean explicitlyMarked = deadline != 0 && SystemClock.elapsedRealtime() <= deadline;
                    boolean answerLaunch = notificationAnswerActivities.contains(activity)
                            || isNotificationAnswerLaunch(intent);
                    boolean fullScreen = intent != null && intent.getBooleanExtra(EXTRA_FULL_SCREEN, false);
                    boolean outgoing = intent != null && intent.getBooleanExtra(EXTRA_NEW_OUTGOING, false);
                    boolean notificationContent = intent != null
                            && (intent.getBooleanExtra(EXTRA_NOTIFICATION_CONTENT, false)
                            || intent.getBooleanExtra(EXTRA_MODULE_NOTIFICATION_CONTENT, false));
                    boolean notificationButton = intent != null
                            && intent.getBooleanExtra(EXTRA_NOTIFICATION_BUTTON, false);
                    boolean showDialpad = intent != null && intent.getBooleanExtra(EXTRA_SHOW_DIALPAD, false);
                    String previousPackage = taskBehindDialer(activity);

                    log("in-call resume: answerLaunch=" + answerLaunch
                            + ", marked=" + explicitlyMarked
                            + ", fullScreen=" + fullScreen
                            + ", outgoing=" + outgoing
                            + ", notificationButton=" + notificationButton
                            + ", notificationContent=" + notificationContent
                            + ", showDialpad=" + showDialpad
                            + ", taskBehind=" + previousPackage);

                    // Full-screen/lock-screen calls and explicit notification-content taps should
                    // remain visible. Android 15's CallStyle Answer action bypasses the Dialer
                    // receiver and normally arrives here with no launch marker (or button=true).
                    if (isKeyguardLocked(activity) || fullScreen || outgoing || notificationContent
                            || (previousPackage != null && isHomePackage(activity, previousPackage))) {
                        notificationAnswerActivities.remove(activity);
                        hardSuppressInCallUi = false;
                        return;
                    }
                    if (!answerLaunch && !explicitlyMarked && !hardSuppressInCallUi) return;
                    hardSuppressActivity(activity, "resume");
                }
            });
            XposedBridge.hookAllMethods(activityClass, "onWindowFocusChanged", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (Boolean.TRUE.equals(param.args[0]) && hardSuppressInCallUi) {
                        hardSuppressActivity((Activity) param.thisObject, "window focus");
                    }
                }
            });
        } catch (Throwable error) {
            log("in-call activity hook unavailable", error);
        }
    }

    private static boolean isNotificationAnswerLaunch(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        return (action != null && action.startsWith(
                "com.android.dialer.incall.statusbarnotification.buttonintentANSWER"))
                || intent.getBooleanExtra(EXTRA_NOTIFICATION_BUTTON, false);
    }

    private static boolean isExplicitUiLaunch(Intent intent) {
        return intent != null && (intent.getBooleanExtra(EXTRA_FULL_SCREEN, false)
                || intent.getBooleanExtra(EXTRA_NEW_OUTGOING, false)
                || intent.getBooleanExtra(EXTRA_NOTIFICATION_CONTENT, false)
                || intent.getBooleanExtra(EXTRA_MODULE_NOTIFICATION_CONTENT, false));
    }

    private static void hardSuppressActivity(Activity activity, String phase) {
        if (!enabled(Prefs.KEEP_APP, true) || isKeyguardLocked(activity)) {
            notificationAnswerActivities.remove(activity);
            hardSuppressInCallUi = false;
            return;
        }
        String previousPackage = taskBehindDialer(activity);
        if (previousPackage == null || isHomePackage(activity, previousPackage)) {
            notificationAnswerActivities.remove(activity);
            hardSuppressInCallUi = false;
            return;
        }
        hardSuppressInCallUi = true;
        suppressUntil = 0;
        if (!activity.isFinishing() && activity.moveTaskToBack(true)) {
            activity.overridePendingTransition(0, 0);
            log("hard-backgrounded in-call task during " + phase + "; returning to "
                    + previousPackage);
        }
    }

    @SuppressWarnings("deprecation")
    private static String taskBehindDialer(Context context) {
        // HyperOS exposes both the current and previous foreground package. Unlike
        // ActivityManager.getRunningTasks, this continues to work for third-party
        // tasks on modern Android, where task visibility is otherwise restricted.
        try {
            Class<?> processManager = XposedHelpers.findClass("miui.process.ProcessManager", null);
            Object info = XposedHelpers.callStaticMethod(processManager, "getForegroundInfo");
            String previous = (String) XposedHelpers.getObjectField(
                    info, "mLastForegroundPackageName");
            if (previous != null && !DIALER.equals(previous)
                    && !"com.android.systemui".equals(previous)) return previous;
        } catch (Throwable error) {
            log("HyperOS foreground history unavailable; using task fallback", error);
        }
        try {
            ActivityManager manager = context.getSystemService(ActivityManager.class);
            List<ActivityManager.RunningTaskInfo> tasks = manager == null ? null : manager.getRunningTasks(10);
            if (tasks == null) return null;
            for (ActivityManager.RunningTaskInfo task : tasks) {
                ComponentName top = task.topActivity;
                if (top != null && !DIALER.equals(top.getPackageName())) return top.getPackageName();
            }
        } catch (Throwable error) {
            log("could not determine task behind Dialer", error);
        }
        return null;
    }

    private static boolean isHomePackage(Context context, String packageName) {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo home = context.getPackageManager().resolveActivity(
                homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        return home != null && home.activityInfo != null
                && packageName.equals(home.activityInfo.packageName);
    }

    private static boolean isKeyguardLocked(Context context) {
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        return keyguard != null && keyguard.isKeyguardLocked();
    }

    private static void hookRecordingEligibility(ClassLoader loader) {
        XC_MethodHook forceTrue = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam param) {
                if (enabled(Prefs.RECORDING, false)) param.setResult(true);
            }
        };
        // Raw R8 names verified against Google Phone 236.0.969488611-pixel.
        hookBooleanNoArg(loader, "kxt", "a", forceTrue); // CanRecord.canRecordCall
        hookBooleanNoArg(loader, "kwz", "a", forceTrue); // CallRecordingEnabledFn.isEnabled

        try {
            XposedBridge.hookAllMethods(
                    XposedHelpers.findClass("android.app.ApplicationPackageManager", loader),
                    "hasSystemFeature", new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            if (enabled(Prefs.RECORDING, false) && param.args.length > 0
                                    && "com.google.android.apps.dialer.call_recording_audio".equals(param.args[0])) {
                                param.setResult(true);
                            }
                        }
                    });
        } catch (Throwable error) {
            log("recording system-feature hook unavailable", error);
        }
    }

    private static void hookBooleanNoArg(ClassLoader loader, String className, String methodName,
                                         XC_MethodHook callback) {
        try {
            Class<?> type = XposedHelpers.findClass(className, loader);
            XposedHelpers.findAndHookMethod(type, methodName, callback);
            log("recording gate hooked: " + className + "." + methodName);
        } catch (Throwable error) {
            log("recording gate unavailable: " + className + "." + methodName, error);
        }
    }

    private static void log(String message) {
        XposedBridge.log("GDialerTweak: " + message);
    }

    private static void log(String message, Throwable error) {
        XposedBridge.log("GDialerTweak: " + message + ": " + error);
    }
}
