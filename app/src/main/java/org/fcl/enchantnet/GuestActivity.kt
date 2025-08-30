package org.fcl.enchantnet

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import org.fcl.enchantnet.databinding.ActivityGuestBinding
import org.fcl.enchantnetcore.EnchantNet
import org.fcl.enchantnetcore.core.RoomKind
import org.fcl.enchantnetcore.state.EnchantNetException
import org.fcl.enchantnetcore.state.EnchantNetSnapshot
import org.fcl.enchantnetcore.state.EnchantNetState
import org.fcl.enchantnetcore.state.EnchantNetStateListener
import org.fcl.enchantnetcore.utils.InviteQuickValidator

class GuestActivity : AppCompatActivity() {

    private val binding by lazy { ActivityGuestBinding.inflate(layoutInflater) }
    private lateinit var backCallback: OnBackPressedCallback

    private val stateListener = object : EnchantNetStateListener {
        override fun onStateChanged(snapshot: EnchantNetSnapshot) {
            runOnUiThread { renderState(snapshot) }
        }

        override fun onCopyInviteCode(inviteCode: String) {
            // Ignore
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.isNavigationBarContrastEnforced = false
        }

        setContentView(binding.root)

        val net = EnchantNet.get()
        net.addListener(stateListener)

        if (net.stateSnapshot.state != EnchantNetState.WAITING) {
            net.stop()
        }

        binding.bar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_back -> {
                    onBackPressedDispatcher.onBackPressed()
                    true
                }

                else -> false
            }
        }

        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val state = EnchantNet.get().stateSnapshot.state
                if (state == EnchantNetState.WAITING) {
                    // Allow normal back navigation
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    Toast.makeText(
                        this@GuestActivity,
                        getString(R.string.toast_disconnect_first),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    private fun renderState(snap: EnchantNetSnapshot) {
        when (snap.state) {
            EnchantNetState.WAITING -> {
                binding.guestBtnSwitch.isEnabled = true
                binding.guestBtnCopy.isEnabled = false
                binding.guestBtnSwitch.setText(R.string.guest_btn_start)
                binding.guestBtnCopy.visibility = View.INVISIBLE
                binding.guestProgress.visibility = View.INVISIBLE
                binding.guestStateImage.visibility = View.VISIBLE
                binding.guestStateText.setText(R.string.guest_text_waiting)
                binding.guestStateImage.setImageResource(R.drawable.baseline_guest_24)
                binding.guestStateText.setTextColor(Color.GRAY)
                binding.guestInput.setText("")
                binding.guestInputLayout.visibility = View.VISIBLE
                binding.guestInputLayout.error = null
                binding.guestInputLayout.helperText = getString(R.string.guest_input_helper)

                binding.guestInput.doOnTextChanged { text, _, _, _ ->
                    val s = text?.toString().orEmpty()
                    val kind = InviteQuickValidator.quickDetectKind(s)
                    if (kind == RoomKind.INVALID) {
                        binding.guestInputLayout.error = getString(R.string.toast_invalid_code)
                    } else {
                        binding.guestInputLayout.error = null
                        binding.guestInputLayout.helperText = getString(R.string.guest_input_helper)
                    }
                }
                binding.guestInput.doAfterTextChanged { e ->
                    val s = e?.toString().orEmpty()
                    val kind = InviteQuickValidator.quickDetectKind(s)
                    if (kind == RoomKind.INVALID) {
                        binding.guestInputLayout.error = getString(R.string.toast_invalid_code)
                    } else {
                        binding.guestInputLayout.error = null
                        binding.guestInputLayout.helperText = getString(R.string.guest_input_helper)
                    }
                }

                binding.guestBtnSwitch.setOnClickListener {
                    if (InviteQuickValidator.quickDetectKind(
                            binding.guestInput.text?.toString().orEmpty()
                        ) == RoomKind.INVALID
                    ) {
                        Toast.makeText(this, R.string.toast_invalid_code, Toast.LENGTH_SHORT).show()
                    } else {
                        binding.guestBtnSwitch.isEnabled = false
                        val handler = Handler(Looper.getMainLooper())
                        handler.postDelayed({
                            EnchantNet.get().startGuest(binding.guestInput.text.toString())
                        }, 100)
                    }
                }
            }

            EnchantNetState.GUESTING -> {
                val connected = snap.backupServer != null

                binding.guestBtnSwitch.isEnabled = true
                binding.guestBtnSwitch.setText(R.string.guest_btn_exit)
                binding.guestInput.setText("")
                binding.guestInputLayout.visibility = View.INVISIBLE
                binding.guestInputLayout.error = null
                binding.guestInputLayout.helperText = getString(R.string.guest_input_helper)

                if (!connected) {
                    binding.guestBtnCopy.isEnabled = false
                    binding.guestBtnCopy.visibility = View.INVISIBLE
                    binding.guestProgress.visibility = View.VISIBLE
                    binding.guestStateImage.visibility = View.INVISIBLE
                    binding.guestStateText.setText(R.string.guest_text_connecting)
                    binding.guestStateText.setTextColor(Color.BLUE)
                } else {
                    binding.guestBtnCopy.isEnabled = true
                    binding.guestBtnCopy.visibility = View.VISIBLE
                    binding.guestProgress.visibility = View.INVISIBLE
                    binding.guestStateImage.visibility = View.VISIBLE
                    binding.guestStateText.text =
                        getString(R.string.guest_text_guesting, snap.backupServer)
                    binding.guestStateImage.setImageResource(R.drawable.baseline_connected_24)
                    binding.guestStateText.setTextColor(Color.GREEN)

                    binding.guestBtnCopy.setOnClickListener {
                        copyAddressFromSnapshot(snap)
                    }
                }

                binding.guestBtnSwitch.setOnClickListener {
                    EnchantNet.get().stop()
                }
            }

            EnchantNetState.EXCEPTION -> {
                var err = "Unknown Error"
                if (snap.exception == EnchantNetException.START_FAILED)
                    err = getString(R.string.guest_text_exception_fail)
                if (snap.exception == EnchantNetException.GUEST_EASYTIER_CRASH)
                    err = getString(R.string.guest_text_exception_crash)
                if (snap.exception == EnchantNetException.GUEST_CONN_LOST)
                    err = getString(R.string.guest_text_exception_lost)
                binding.guestBtnSwitch.isEnabled = true
                binding.guestBtnCopy.isEnabled = false
                binding.guestBtnSwitch.setText(R.string.guest_btn_stop_task)
                binding.guestBtnCopy.visibility = View.INVISIBLE
                binding.guestProgress.visibility = View.INVISIBLE
                binding.guestStateImage.visibility = View.VISIBLE
                binding.guestStateText.text = getString(R.string.guest_text_exception, err)
                binding.guestStateImage.setImageResource(R.drawable.baseline_exit_room_24)
                binding.guestStateText.setTextColor(Color.RED)
                binding.guestInput.setText("")
                binding.guestInputLayout.visibility = View.INVISIBLE
                binding.guestInputLayout.error = null
                binding.guestInputLayout.helperText = getString(R.string.guest_input_helper)

                binding.guestBtnSwitch.setOnClickListener {
                    EnchantNet.get().stop()
                }
            }

            EnchantNetState.SCANNING -> { /* Fk this shit */
            }

            EnchantNetState.HOSTING -> { /* Fk this shit */
            }
        }
    }

    private fun copyServerAddress(address: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("server_address", address))
        Toast.makeText(this, getString(R.string.action_copy_address), Toast.LENGTH_SHORT).show()
    }

    private fun copyAddressFromSnapshot(snap: EnchantNetSnapshot) {
        val server = snap.backupServer

        if (server.isNullOrBlank())
            return

        copyServerAddress(server)
    }

    override fun onDestroy() {
        EnchantNet.get().removeListener(stateListener)
        EnchantNet.get().stop()
        super.onDestroy()
    }
}