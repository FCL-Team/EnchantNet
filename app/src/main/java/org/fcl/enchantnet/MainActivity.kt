package org.fcl.enchantnet

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fcl.enchantnet.databinding.ActivityMainBinding
import org.fcl.enchantnetcore.EnchantNet
import org.fcl.enchantnetcore.utils.PermissionUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Single PermissionUtils instance (must be created before Activity STARTED)
    private lateinit var pu: PermissionUtils

    // Flow flags
    private var permissionFlowCompleted = false
    private var waitingBatteryExemption = false

    // Step results (null = not decided yet). Battery is optional and does not block.
    private var notifGranted: Boolean? = null
    private var batteryGranted: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.isNavigationBarContrastEnforced = false
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Register activity-result launchers early
        pu = PermissionUtils.create(this)

        // Restore transient flow state (rotation etc.)
        permissionFlowCompleted = savedInstanceState?.getBoolean(KEY_DONE, false) == true
        waitingBatteryExemption = savedInstanceState?.getBoolean(KEY_WAITING_BATTERY, false) == true
        notifGranted = savedInstanceState?.getBoolean(KEY_NOTIF_GRANTED)
        batteryGranted = savedInstanceState?.getBoolean(KEY_BATT_GRANTED)

        // If notifications are already granted, skip the startup dialog
        if (isNotificationPermissionGranted() && pu.isVpnPermissionGranted) {
            permissionFlowCompleted = true
            initializeUI()
            return
        }

        if (permissionFlowCompleted) {
            initializeUI()
        } else {
            showStartupPermissionDialog()
        }
    }

    override fun onResume() {
        super.onResume()
        // If we returned from the battery optimization settings page, verify the result here.
        if (waitingBatteryExemption) {
            waitingBatteryExemption = false
            // Battery exemption is OPTIONAL: record and finalize, but never block or show failure here.
            batteryGranted = !pu.shouldRequestIgnoreBattery()
            finalizePermissionFlow()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_DONE, permissionFlowCompleted)
        outState.putBoolean(KEY_WAITING_BATTERY, waitingBatteryExemption)
        notifGranted?.let { outState.putBoolean(KEY_NOTIF_GRANTED, it) }
        batteryGranted?.let { outState.putBoolean(KEY_BATT_GRANTED, it) }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            // On API < 33 notifications are granted by default (no runtime permission)
            true
        }
    }

    /** First dialog asking user to begin granting required (non-VPN) permissions. */
    private fun showStartupPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_permission_title)
            .setMessage(R.string.dialog_permission_msg)
            .setCancelable(false)
            .setNegativeButton(R.string.dialog_exit) { _, _ -> finishAffinity() }
            .setPositiveButton(R.string.dialog_permission_grant) { _, _ -> startPermissionFlow() }
            .show()
    }

    /**
     * Request sequence (excluding VPN):
     * 1) POST_NOTIFICATIONS (mandatory)
     * 2) Battery optimization exemption (optional; no failure prompt)
     */
    private fun startPermissionFlow() {
        // Reset step results for a clean retry
        notifGranted = null
        batteryGranted = null

        // Step 1: Notification permission (Android 13+; no-op on lower APIs)
        pu.requestNotifications(object : PermissionUtils.Callbacks {
            override fun onRationale(
                permissionsNeedingRationale: Array<String>,
                proceed: Runnable,
                cancel: Runnable
            ) {
                // Rationale UI is handled by the outer dialog; proceed immediately.
                proceed.run()
            }

            override fun onResult(result: PermissionUtils.Result) {
                // Only notifications are mandatory.
                notifGranted = result.denied.isEmpty() && result.permanentlyDenied.isEmpty()
                if (notifGranted == false) {
                    // Notifications denied → show single denial dialog; do not continue to battery step.
                    showDeniedDialog()
                    return
                }
                // Notifications granted → continue to vpn step.
                requestVpnThenContinue()
            }
        })
    }

    private fun requestVpnThenContinue() {
        pu.requestVpnPermission(
            {  // onGranted
                requestBatteryExemptionIfNeeded()
            },
            {  // onDenied
                showDeniedDialog()
            }
        )
    }

    /**
     * Step 2: If battery optimization exemption is still required, launch the system page.
     * There is no callback; the outcome is verified in onResume().
     * Regardless of the outcome, we finalize the flow (battery is optional).
     */
    private fun requestBatteryExemptionIfNeeded() {
        if (!pu.shouldRequestIgnoreBattery()) {
            // Already exempted or not required → mark and finalize
            batteryGranted = true
            finalizePermissionFlow()
            return
        }
        val launched = pu.requestIgnoreBattery(null)
        if (launched) {
            // Wait for onResume() to record the final state, then finalize.
            waitingBatteryExemption = true
        } else {
            // Could not launch settings page; treat as not granted and finalize (still optional).
            batteryGranted = false
            finalizePermissionFlow()
        }
    }

    /**
     * Finalize after notifications decided (mandatory). Battery is optional and does not block.
     * If notifications granted → proceed to UI; else show denial dialog.
     */
    private fun finalizePermissionFlow() {
        val n = notifGranted ?: return

        if (n) {
            permissionFlowCompleted = true
            initializeUI()
        } else {
            showDeniedDialog()
        }
    }

    /** Dialog shown when mandatory (notification) permission is denied. */
    private fun showDeniedDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_permission_deny_title)
            .setMessage(R.string.dialog_permission_deny_msg)
            .setCancelable(false)
            .setNegativeButton(R.string.dialog_exit) { _, _ -> finishAffinity() }
            .setPositiveButton(R.string.dialog_permission_deny_retry) { _, _ -> startPermissionFlow() }
            .show()
    }

    private fun initializeUI() {
        binding.appBar.title = getString(R.string.app_name)

        binding.appBar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_about -> {
                    showAboutDialog()
                    true
                }
                else -> false
            }
        }

        binding.cardHost.setOnClickListener {
            startActivity(Intent(this, HostActivity::class.java))
        }
        binding.cardGuest.setOnClickListener {
            startActivity(Intent(this, GuestActivity::class.java))
        }
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dialog_about_title)
            .setMessage(getString(R.string.dialog_about_msg, BuildConfig.VERSION_NAME, EnchantNet.get().developers))
            .setPositiveButton(R.string.action_ok, null)
            .show()
    }

    companion object {
        private const val KEY_DONE = "permission_flow_done"
        private const val KEY_WAITING_BATTERY = "permission_waiting_battery"
        private const val KEY_NOTIF_GRANTED = "permission_notif_granted"
        private const val KEY_BATT_GRANTED = "permission_batt_granted"
    }
}
