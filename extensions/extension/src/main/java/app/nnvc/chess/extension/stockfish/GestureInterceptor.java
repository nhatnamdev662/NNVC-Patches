/*
 * Copyright 2026 NNVC
 * https://github.com/NNVC/NNVC-Patches
 */

package app.nnvc.chess.extension.stockfish;

import android.app.Activity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.Window;

import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class GestureInterceptor {

    public static boolean isComposeActivity(Activity activity) {
        return isComposeActivity((Object) activity);
    }

    public static boolean isComposeActivity(Object activity) {
        if (activity == null) return false;
        String name = activity.getClass().getName();
        String simpleName = activity.getClass().getSimpleName();
        if (simpleName.contains("Lesson")
                || simpleName.contains("Puzzle")
                || simpleName.contains("Daily")
                || simpleName.contains("Train")
                || simpleName.contains("Compose")
                || simpleName.contains("Review")
                || simpleName.contains("Practice")
                || simpleName.contains("Solo")
                || name.contains(".lessons.")
                || name.contains(".puzzles.")
                || name.contains(".daily.")) {
            return true;
        }
        if (activity instanceof Activity) {
            try {
                Activity act = (Activity) activity;
                if (act.getWindow() != null && act.getWindow().getDecorView() != null) {
                    return containsComposeView(act.getWindow().getDecorView());
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private static boolean containsComposeView(View view) {
        if (view == null) return false;
        String name = view.getClass().getName();
        if (name.contains("ComposeView") || name.contains("AndroidComposeView")) {
            return true;
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                if (containsComposeView(vg.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void ensureGestureInterceptorRegistered() {
        Activity activity = StockfishExtension.getCurrentActivity();
        if (activity != null) {
            registerGestureInterceptor(activity);
        }
    }

    public static void registerGestureInterceptor(Activity activity) {
        try {
            Window window = activity.getWindow();
            if (window == null) return;
            Window.Callback originalCallback = window.getCallback();
            if (originalCallback == null) return;

            // In Jetpack Compose activities, proxying Window.Callback breaks Compose touch delegation and freezes UI
            if (isComposeActivity(activity)) {
                if (Proxy.isProxyClass(originalCallback.getClass())) {
                    try {
                        InvocationHandler handler = Proxy.getInvocationHandler(originalCallback);
                        if (handler instanceof GestureHandler) {
                            window.setCallback(((GestureHandler) handler).getDelegate());
                        }
                    } catch (Throwable ignored) {}
                }
                return;
            }

            if (Proxy.isProxyClass(originalCallback.getClass())) {
                try {
                    InvocationHandler handler = Proxy.getInvocationHandler(originalCallback);
                    if (handler instanceof GestureHandler) {
                        return; // Already wrapped
                    }
                } catch (Exception ignored) {}
            }

            GestureDetector gestureDetector = new GestureDetector(activity, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public void onLongPress(MotionEvent e) {
                    if (isNearLogo(activity, e)) {
                        StockfishSettingsDialog.showSettingsMenu(activity);
                    }
                }

                @Override
                public boolean onDoubleTap(MotionEvent e) {
                    if (isNearLogo(activity, e)) {
                        StockfishExtension.toggleEverything(activity);
                        return true;
                    }
                    return false;
                }
            });

            Window.Callback proxyCallback = (Window.Callback) Proxy.newProxyInstance(
                Window.Callback.class.getClassLoader(),
                new Class<?>[]{Window.Callback.class},
                new GestureHandler(originalCallback, gestureDetector)
            );
            window.setCallback(proxyCallback);
            
            // Tour overlay disabled to prevent dimming or blocking matches
            // StockfishTourOverlay.showTourIfNeeded(activity);
        } catch (Throwable t) {
            android.util.Log.e("NNVC", "registerGestureInterceptor failed: " + t.getMessage());
        }
    }

    public static class GestureHandler implements InvocationHandler {
        private final Window.Callback delegate;
        private final GestureDetector gestureDetector;

        public GestureHandler(Window.Callback delegate, GestureDetector gestureDetector) {
            this.delegate = delegate;
            this.gestureDetector = gestureDetector;
        }

        public Window.Callback getDelegate() {
            return delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("dispatchTouchEvent".equals(method.getName()) && args != null && args.length > 0) {
                MotionEvent event = (MotionEvent) args[0];
                gestureDetector.onTouchEvent(event);
            }
            try {
                return method.invoke(delegate, args);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }

    private static boolean isNearLogo(Activity activity, MotionEvent e) {
        float density = activity.getResources().getDisplayMetrics().density;
        float maxLogoY = 100f * density; // top 100dp
        float y = e.getY();
        return y >= 0 && y <= maxLogoY;
    }
}
