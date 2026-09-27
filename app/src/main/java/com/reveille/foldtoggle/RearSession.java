package com.reveille.foldtoggle;

import android.app.Activity;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Fallback path through the OEM WindowManager Extensions library (androidx.window.extensions),
 * the same rear-display implementation Jetpack WindowManager uses. It ships on the device as a
 * platform library, so it keeps working on builds where the hidden DeviceStateRequest API is
 * no longer reachable from apps. Declared optional in the manifest via uses-library.
 */
final class RearSession {
    private static Object component; // androidx.window.extensions.area.WindowAreaComponent
    private static boolean looked;
    /** True while a session started by this process is active (per the session callback). */
    private static volatile boolean owned;

    static boolean ownsSession() {
        return owned;
    }

    /** The WindowAreaComponent, or null if this device doesn't ship one. */
    static synchronized Object component() {
        if (looked) return component;
        looked = true;
        try {
            Object ext = Class.forName("androidx.window.extensions.WindowExtensionsProvider")
                    .getMethod("getWindowExtensions").invoke(null);
            component = ext.getClass().getMethod("getWindowAreaComponent").invoke(ext);
        } catch (Throwable t) {
            Log.i(Fold.TAG, "window extensions unavailable: " + t);
        }
        return component;
    }

    static boolean available() {
        return component() != null;
    }

    static void start(Activity activity) throws Exception {
        Object c = component();
        if (c == null) throw new IllegalStateException("window extensions unavailable");
        Method start = find(c, "startRearDisplaySession");
        // The session callback type differs by vendor API level (java.util.function.Consumer
        // vs androidx.window.extensions.core.util.function.Consumer), so proxy whichever it is.
        Class<?> cbType = start.getParameterTypes()[1];
        Object cb = Proxy.newProxyInstance(cbType.getClassLoader(), new Class<?>[]{cbType},
                (proxy, m, args) -> {
                    switch (m.getName()) {
                        case "accept":
                            // SESSION_STATE_INACTIVE = 0; ACTIVE (1) / CONTENT_VISIBLE (2) = ours.
                            owned = !Integer.valueOf(0).equals(args[0]);
                            Log.i(Fold.TAG, "rear session status " + args[0]);
                            return null;
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == args[0];
                        case "toString": return "FoldToggleRearSession";
                        default: return null;
                    }
                });
        start.invoke(c, activity, cb);
    }

    static void end() throws Exception {
        Object c = component();
        if (c == null) throw new IllegalStateException("window extensions unavailable");
        c.getClass().getMethod("endRearDisplaySession").invoke(c);
    }

    private static Method find(Object c, String name) throws NoSuchMethodException {
        for (Method m : c.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == 2) return m;
        }
        throw new NoSuchMethodException(c.getClass().getName() + "." + name);
    }

    private RearSession() {}
}
