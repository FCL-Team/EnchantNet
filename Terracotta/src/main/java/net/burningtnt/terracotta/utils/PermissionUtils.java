package net.burningtnt.terracotta.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * PermissionUtils (minSdk 26, targetSdk 36)
 * <p>
 * Scope:
 * - Only requests POST_NOTIFICATIONS at runtime (API 33+).
 * - Other permissions you listed are install-time or handled via Settings intent
 *   (e.g., FOREGROUND_SERVICE*, INTERNET, ACCESS_*_STATE, CHANGE_*_STATE, REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).
 * <p>
 * UI is not implemented here; show dialogs/toasts in your callbacks.
 * Works with any Activity: uses Activity Result API if the Activity is a ComponentActivity,
 * otherwise falls back to requestPermissions + onRequestPermissionsResult.
 */
public final class PermissionUtils {

    /** Aggregated result of a permission request. */
    public static final class Result {

        public final Set<String> granted;
        public final Set<String> denied;
        public final Set<String> permanentlyDenied;

        public Result(Set<String> g, Set<String> d, Set<String> p) {
            this.granted = Collections.unmodifiableSet(g);
            this.denied = Collections.unmodifiableSet(d);
            this.permanentlyDenied = Collections.unmodifiableSet(p);
        }
    }

    /** Callbacks for rationale and final result. */
    public interface Callbacks {
        /** Called when some permissions need rationale. Call proceed.run() to continue, or cancel.run() to abort. */
        default void onRationale(@NonNull String[] permissionsNeedingRationale,
                                 @NonNull Runnable proceed,
                                 @NonNull Runnable cancel) {}
        /** Final result after user interaction. */
        void onResult(@NonNull Result result);
    }

    /** Legacy request code for non-ComponentActivity flow. */
    public static final int REQUEST_CODE_LEGACY = 0xE7AF;

    private final Activity activity;
    private final boolean useActivityResultApi;
    private final SharedPreferences prefs;
    private ActivityResultLauncher<String[]> launcher; // only if ComponentActivity

    private boolean inFlight = false;
    private String[] pendingPermissions = null;
    private Callbacks pendingCallbacks = null;

    private @Nullable Set<String> manifestDeclared; // cached manifest permissions

    private ActivityResultLauncher<Intent> vpnLauncher;
    private Runnable vpnGrantedAction, vpnDeniedAction;

    private PermissionUtils(@NonNull Activity activity) {
        this.activity = activity;
        this.useActivityResultApi = (activity instanceof ComponentActivity);
        this.prefs = activity.getSharedPreferences("perm_utils_prefs", Context.MODE_PRIVATE);

        if (activity instanceof ComponentActivity)
            vpnLauncher = ((ComponentActivity) activity).registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    res -> {
                        boolean granted = res.getResultCode() == Activity.RESULT_OK
                                || VpnService.prepare(activity) == null;
                        Runnable r = granted ? vpnGrantedAction : vpnDeniedAction;
                        if (r != null) r.run();
                        vpnGrantedAction = vpnDeniedAction = null;
                    });

        if (useActivityResultApi) {
            this.launcher = ((ComponentActivity) activity).registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    this::handleActivityResultMap
            );
        }
    }

    /** Factory. */
    public static PermissionUtils create(@NonNull Activity activity) {
        return new PermissionUtils(activity);
    }

    // --------------------------------------------------------------------------------------------
    // Public API
    // --------------------------------------------------------------------------------------------

    /**
     * Request arbitrary runtime permissions (filtered to real runtime + declared in manifest).
     * Here, effectively only POST_NOTIFICATIONS (API 33+) will be requested at runtime.
     */
    public void request(@NonNull String[] permissions, @NonNull Callbacks callbacks) {
        if (inFlight) throw new IllegalStateException("Another permission request is in flight");

        // Filter to runtime-dangerous for this SDK window (here only POST_NOTIFICATIONS).
        String[] runtime = filterRuntimeDangerous(permissions);
        if (runtime.length == 0) {
            callbacks.onResult(emptyResult());
            return;
        }

        // Filter to manifest-declared to avoid "not declared" issues.
        String[] declared = filterDeclaredInManifest(runtime);
        if (declared.length == 0) {
            callbacks.onResult(emptyResult());
            return;
        }

        // Pick missing ones.
        Set<String> missing = new LinkedHashSet<>();
        for (String p : declared) if (!isGranted(p)) missing.add(p);
        if (missing.isEmpty()) {
            callbacks.onResult(new Result(setOf(declared), set(), set()));
            return;
        }

        // Mark as "asked" for permanent-deny detection.
        SharedPreferences.Editor ed = prefs.edit();
        for (String p : missing) ed.putBoolean(keyAsked(p), true);
        ed.apply();

        // Collect those needing rationale.
        Set<String> needRationale = new LinkedHashSet<>();
        for (String p : missing) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, p)) {
                needRationale.add(p);
            }
        }

        final Runnable reallyLaunch = () -> {
            inFlight = true;
            pendingPermissions = missing.toArray(new String[0]);
            pendingCallbacks = callbacks;

            if (useActivityResultApi) {
                launcher.launch(pendingPermissions);
            } else {
                ActivityCompat.requestPermissions(activity, pendingPermissions, REQUEST_CODE_LEGACY);
            }
        };

        if (!needRationale.isEmpty()) {
            callbacks.onRationale(
                    needRationale.toArray(new String[0]),
                    reallyLaunch,
                    () -> callbacks.onResult(new Result(set(), needRationale, set()))
            );
        } else {
            reallyLaunch.run();
        }
    }

    /** One-tap request for app's common runtime permissions (currently only POST_NOTIFICATIONS on API 33+). */
    public void requestAppPermissions(@NonNull Callbacks callbacks) {
        request(getDefaultRuntimePermissions(), callbacks);
    }

    public boolean isVpnPermissionGranted() {
        return VpnService.prepare(activity) == null;
    }

    public void requestVpnPermission(@NonNull Runnable onGranted,
                                     @Nullable Runnable onDenied) {
        if (vpnLauncher == null) {
            Log.e("PermissionUtils", "vpnLauncher == null, use AppCompatActivity!");
            return;
        }

        Intent intent = VpnService.prepare(activity);
        if (intent == null) {
            onGranted.run();
            return;
        }
        this.vpnGrantedAction = onGranted;
        this.vpnDeniedAction = onDenied;
        vpnLauncher.launch(intent);
    }

    /** Request notifications (POST_NOTIFICATIONS) if API >= 33; no-op otherwise. */
    public void requestNotifications(@NonNull Callbacks callbacks) {
        if (Build.VERSION.SDK_INT >= 33) {
            request(new String[]{ Manifest.permission.POST_NOTIFICATIONS }, callbacks);
        } else {
            callbacks.onResult(emptyResult());
        }
    }

    /** Whether we should prompt user to ignore battery optimizations (handled via Settings intent). */
    public boolean shouldRequestIgnoreBattery() {
        PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        return pm != null && !pm.isIgnoringBatteryOptimizations(activity.getPackageName());
    }

    /** Launch system screen to request battery optimization exemption. Returns true if an Intent was started. */
    public boolean requestIgnoreBattery(@Nullable Runnable onBeforeLaunch) {
        try {
            @SuppressLint("BatteryLife") Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            if (onBeforeLaunch != null) onBeforeLaunch.run();
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                if (onBeforeLaunch != null) onBeforeLaunch.run();
                activity.startActivity(fallback);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    /** Default runtime permissions for this app: only POST_NOTIFICATIONS (Android 13+). */
    @NonNull
    public String[] getDefaultRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{ Manifest.permission.POST_NOTIFICATIONS };
        }
        return new String[0];
    }

    // --------------------------------------------------------------------------------------------
    // Legacy flow hook (for non-ComponentActivity)
    // --------------------------------------------------------------------------------------------

    /**
     * Call this from Activity#onRequestPermissionsResult if your Activity is NOT a ComponentActivity.
     * Returns true if handled by PermissionUtils.
     */
    public boolean onRequestPermissionsResult(int requestCode,
                                              @NonNull String[] permissions,
                                              @NonNull int[] grantResults) {
        if (!useActivityResultApi && inFlight && requestCode == REQUEST_CODE_LEGACY) {
            handleLegacyResultArrays(permissions, grantResults);
            return true;
        }
        return false;
    }

    // --------------------------------------------------------------------------------------------
    // Internals
    // --------------------------------------------------------------------------------------------

    private void handleActivityResultMap(@NonNull Map<String, Boolean> grantMap) {
        Set<String> requested = (pendingPermissions == null) ? set() : setOf(pendingPermissions);
        Callbacks cb = pendingCallbacks;

        inFlight = false;
        pendingPermissions = null;
        pendingCallbacks = null;

        if (cb == null || requested.isEmpty()) return;

        Set<String> granted = new LinkedHashSet<>();
        Set<String> denied = new LinkedHashSet<>();
        Set<String> permanentlyDenied = new LinkedHashSet<>();

        for (String perm : requested) {
            boolean ok = Boolean.TRUE.equals(grantMap.get(perm));
            if (ok) {
                granted.add(perm);
            } else {
                boolean askedBefore = prefs.getBoolean(keyAsked(perm), false);
                boolean showRationale = ActivityCompat.shouldShowRequestPermissionRationale(activity, perm);
                if (!showRationale && askedBefore) permanentlyDenied.add(perm); else denied.add(perm);
            }
        }
        cb.onResult(new Result(granted, denied, permanentlyDenied));
    }

    private void handleLegacyResultArrays(@NonNull String[] permissions, @NonNull int[] grantResults) {
        Callbacks cb = pendingCallbacks;
        inFlight = false;
        pendingPermissions = null;
        pendingCallbacks = null;
        if (cb == null) return;

        Set<String> granted = new LinkedHashSet<>();
        Set<String> denied = new LinkedHashSet<>();
        Set<String> permanentlyDenied = new LinkedHashSet<>();

        for (int i = 0; i < permissions.length; i++) {
            String perm = permissions[i];
            boolean ok = i < grantResults.length && grantResults[i] == PackageManager.PERMISSION_GRANTED;
            if (ok) {
                granted.add(perm);
            } else {
                boolean show = ActivityCompat.shouldShowRequestPermissionRationale(activity, perm);
                if (!show) permanentlyDenied.add(perm); else denied.add(perm);
            }
        }
        cb.onResult(new Result(granted, denied, permanentlyDenied));
    }

    private boolean isGranted(@NonNull String perm) {
        return ContextCompat.checkSelfPermission(activity, perm) == PackageManager.PERMISSION_GRANTED;
    }

    /** Only POST_NOTIFICATIONS is runtime-dangerous here; everything else is install-time or via Settings intent. */
    private String[] filterRuntimeDangerous(@NonNull String[] permissions) {
        Set<String> out = new LinkedHashSet<>();
        for (String p : permissions) {
            if (Manifest.permission.POST_NOTIFICATIONS.equals(p) && Build.VERSION.SDK_INT >= 33) {
                out.add(p);
            }
        }
        return out.toArray(new String[0]);
    }

    /** Request only permissions that are declared in the manifest. */
    private String[] filterDeclaredInManifest(@NonNull String[] perms) {
        Set<String> declared = getManifestDeclaredPermissions();
        if (declared.isEmpty()) return new String[0];
        Set<String> out = new LinkedHashSet<>();
        for (String p : perms) if (declared.contains(p)) out.add(p);
        return out.toArray(new String[0]);
    }

    private Set<String> getManifestDeclaredPermissions() {
        if (manifestDeclared != null) return manifestDeclared;
        Set<String> out = new HashSet<>();
        try {
            PackageManager pm = activity.getPackageManager();
            PackageInfo pi;
            if (Build.VERSION.SDK_INT >= 33) {
                pi = pm.getPackageInfo(activity.getPackageName(),
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS));
            } else {
                pi = pm.getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS);
            }
            if (pi.requestedPermissions != null) {
                out.addAll(Arrays.asList(pi.requestedPermissions));
            }
        } catch (Throwable ignored) {}
        manifestDeclared = out;
        return out;
    }

    private String keyAsked(String perm) { return "asked_" + perm; }

    private static Result emptyResult() { return new Result(set(), set(), set()); }
    private static Set<String> setOf(String... arr) { return new LinkedHashSet<>(Arrays.asList(arr)); }
    private static Set<String> set() { return new LinkedHashSet<>(); }
}
