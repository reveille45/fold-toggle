package com.reveille.foldtoggle;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * Thin reflection wrapper over the hidden DeviceStateManager API.
 * Requests can outlive this process, so the real state comes from a state callback.
 *
 * Device-state IDs are defined per OEM, so the rear-display state is discovered at runtime
 * (see {@link #detect}) instead of hardcoded. CLOSED is never app-requestable; REAR_DISPLAY
 * (inner screen off, outer screen on) is the state the platform lets the top app request.
 */
// Hidden framework classes/resources are the whole point of this app; see README "How it works".
@SuppressLint({"PrivateApi", "DiscouragedApi"})
final class Fold {
    static final String TAG = "FoldToggle";

    /** Rear-display state ID for this device, or -1 if none was found. */
    static volatile int target = -1;
    /** How {@link #target} was found, for the diagnostics screen. */
    static volatile String source = "not detected";
    static volatile boolean forcedOuter = false;

    private static boolean detected;
    private static boolean watching;

    static boolean supported(Context ctx) {
        detect(ctx);
        return target >= 0;
    }

    /**
     * Finds the rear-display state, most to least authoritative:
     * 1. the framework config value WindowManager Extensions itself uses,
     * 2. a DeviceState flagged PROPERTY_FEATURE_REAR_DISPLAY (Android 15+),
     * 3. a DeviceState whose name mentions REAR_DISPLAY.
     */
    static synchronized void detect(Context ctx) {
        if (detected) return;
        detected = true;
        List<Object> states = supportedStates(ctx);

        int cfg = rearDisplayConfig();
        if (cfg >= 0 && idOf(states, cfg) != null && !hasProperty(idOf(states, cfg), "PROPERTY_APP_INACCESSIBLE")) {
            set(cfg, "config_deviceStateRearDisplay");
            return;
        }
        for (Object s : states) {
            if (hasProperty(s, "PROPERTY_FEATURE_REAR_DISPLAY") && !hasProperty(s, "PROPERTY_APP_INACCESSIBLE")) {
                set(id(s), "PROPERTY_FEATURE_REAR_DISPLAY");
                return;
            }
        }
        for (Object s : states) {
            if (name(s).toUpperCase(Locale.ROOT).contains("REAR_DISPLAY")) {
                set(id(s), "state name " + name(s));
                return;
            }
        }
        Log.w(TAG, "no rear-display state found");
    }

    private static void set(int id, String how) {
        target = id;
        source = how;
        Log.i(TAG, "rear-display state " + id + " via " + how);
    }

    /** Human-readable dump for bug reports. */
    static String diagnostics(Context ctx) {
        detect(ctx);
        StringBuilder sb = new StringBuilder();
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" (").append(Build.DEVICE).append(")\n");
        sb.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("App: ").append(BuildConfig.VERSION_NAME).append('\n');
        sb.append("config_deviceStateRearDisplay: ").append(rearDisplayConfig()).append('\n');
        sb.append("Detected target: ").append(target).append(" (").append(source).append(")\n");
        sb.append("Supported states:\n");
        List<Object> states = supportedStates(ctx);
        if (states.isEmpty()) sb.append("  (none readable)\n");
        for (Object s : states) {
            sb.append("  ").append(id(s)).append(' ').append(name(s));
            if (hasProperty(s, "PROPERTY_FEATURE_REAR_DISPLAY")) sb.append(" [rear]");
            if (hasProperty(s, "PROPERTY_POLICY_AVAILABLE_FOR_APP_REQUEST")) sb.append(" [app-request]");
            if (hasProperty(s, "PROPERTY_APP_INACCESSIBLE")) sb.append(" [app-inaccessible]");
            sb.append('\n');
        }
        return sb.toString();
    }

    // --- state discovery helpers (every call is best-effort; hidden APIs vary by OEM/release) ---

    /** Integer IDs on Android 14, DeviceState objects on 15+. */
    private static List<Object> supportedStates(Context ctx) {
        List<Object> out = new ArrayList<>();
        try {
            Object dsm = dsm(ctx);
            Object r = dsm.getClass().getMethod("getSupportedDeviceStates").invoke(dsm);
            if (r instanceof int[]) {
                for (int i : (int[]) r) out.add(i);
            } else if (r instanceof List) {
                out.addAll((List<?>) r);
            }
        } catch (Throwable t) {
            Log.w(TAG, "getSupportedDeviceStates failed", t);
        }
        return out;
    }

    private static int rearDisplayConfig() {
        try {
            Resources sys = Resources.getSystem();
            int res = sys.getIdentifier("config_deviceStateRearDisplay", "integer", "android");
            return res == 0 ? -1 : sys.getInteger(res);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static Object idOf(List<Object> states, int id) {
        for (Object s : states) if (id(s) == id) return s;
        return null;
    }

    static int id(Object s) {
        if (s instanceof Integer) return (Integer) s;
        try {
            return (Integer) s.getClass().getMethod("getIdentifier").invoke(s);
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String name(Object s) {
        if (s instanceof Integer) return "";
        try {
            return String.valueOf(s.getClass().getMethod("getName").invoke(s));
        } catch (Throwable t) {
            return "";
        }
    }

    /** Looks the property constant up by name so no OEM/release-specific values are hardcoded. */
    private static boolean hasProperty(Object s, String field) {
        if (s == null || s instanceof Integer) return false;
        try {
            int prop = s.getClass().getField(field).getInt(null);
            return (Boolean) s.getClass().getMethod("hasProperty", int.class).invoke(s, prop);
        } catch (Throwable t) {
            return false;
        }
    }

    // --- requests ---

    /** "device_state" isn't a public Context constant, hence the suppression. */
    @SuppressLint("WrongConstant")
    private static Object dsm(Context ctx) {
        return ctx.getSystemService("device_state");
    }

    /** Keeps forcedOuter in sync with the committed device state; runs onChange on each update. */
    static synchronized void watch(Context ctx, Runnable onChange) {
        if (watching) return;
        detect(ctx);
        try {
            Object dsm = dsm(ctx);
            Class<?> cbC = Class.forName(
                    "android.hardware.devicestate.DeviceStateManager$DeviceStateCallback");
            Object cb = Proxy.newProxyInstance(cbC.getClassLoader(), new Class<?>[]{cbC},
                    (proxy, method, args) -> {
                        String n = method.getName();
                        if (n.equals("onDeviceStateChanged") || n.equals("onStateChanged")) {
                            int id = id(args[0]);
                            forcedOuter = id == target;
                            Log.i(TAG, "device state " + id);
                            if (onChange != null) onChange.run();
                        } else if (n.equals("hashCode")) {
                            return System.identityHashCode(proxy);
                        } else if (n.equals("equals")) {
                            return proxy == args[0];
                        } else if (n.equals("toString")) {
                            return "FoldToggleCallback";
                        }
                        return null;
                    });
            dsm.getClass().getMethod("registerCallback", Executor.class, cbC)
                    .invoke(dsm, ctx.getMainExecutor(), cb);
            watching = true;
        } catch (Throwable t) {
            Log.e(TAG, "watch failed", t);
        }
    }

    private static void request(Context ctx, int state) throws Exception {
        Object dsm = dsm(ctx);
        Class<?> reqC = Class.forName("android.hardware.devicestate.DeviceStateRequest");
        Class<?> cbC = Class.forName("android.hardware.devicestate.DeviceStateRequest$Callback");
        Object builder = reqC.getMethod("newBuilder", int.class).invoke(null, state);
        Object req = builder.getClass().getMethod("build").invoke(builder);
        Method m = dsm.getClass().getMethod("requestState", reqC, Executor.class, cbC);
        m.invoke(dsm, req, null, null);
    }

    static void forceOuter(Context ctx) throws Exception {
        request(ctx, target);
        forcedOuter = true;
        Log.i(TAG, "requested state " + target);
    }

    static void release(Context ctx) throws Exception {
        // The active request may belong to an earlier process of ours; re-request to own it,
        // then cancel (cancelStateRequest only cancels the caller's own request).
        request(ctx, target);
        Object dsm = dsm(ctx);
        dsm.getClass().getMethod("cancelStateRequest").invoke(dsm);
        forcedOuter = false;
        Log.i(TAG, "cancelled request");
    }

    private Fold() {}
}
