package org.fcl.enchantnet

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import org.fcl.enchantnet.databinding.ActivityHostBinding
import org.fcl.enchantnetcore.EnchantNet
import org.fcl.enchantnetcore.state.EnchantNetException
import org.fcl.enchantnetcore.state.EnchantNetSnapshot
import org.fcl.enchantnetcore.state.EnchantNetState
import org.fcl.enchantnetcore.state.EnchantNetStateListener

class HostActivity : AppCompatActivity() {

    private val binding by lazy { ActivityHostBinding.inflate(layoutInflater) }
    private lateinit var backCallback: OnBackPressedCallback

    private val stateListener = object : EnchantNetStateListener {
        override fun onStateChanged(snapshot: EnchantNetSnapshot) {
            runOnUiThread { renderState(snapshot) }
        }

        override fun onCopyInviteCode(inviteCode: String) {
            runOnUiThread { copyInviteCode(inviteCode) }
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

        binding.bar.title = getString(R.string.bar_host)
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
                Log.d("Host", state.name)
                if (state == EnchantNetState.WAITING) {
                    // Allow normal back navigation
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    Toast.makeText(
                        this@HostActivity,
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
                binding.hostBtnSwitch.isEnabled = true
                binding.hostBtnCopy.isEnabled = false
                binding.hostBtnSwitch.setText(R.string.host_btn_start_scan)
                binding.hostBtnCopy.visibility = View.INVISIBLE
                binding.hostProgress.visibility = View.INVISIBLE
                binding.hostStateImage.visibility = View.VISIBLE
                binding.hostStateText.setText(R.string.host_text_waiting)
                binding.hostStateImage.setImageResource(R.drawable.baseline_host_24)
                binding.hostStateText.setTextColor(Color.GRAY)

                binding.hostBtnSwitch.setOnClickListener {
                    binding.hostBtnSwitch.isEnabled = false
                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed({
                        EnchantNet.get().startHost()
                    }, 100)
                }
            }

            EnchantNetState.SCANNING -> {
                binding.hostBtnSwitch.isEnabled = true
                binding.hostBtnCopy.isEnabled = false
                binding.hostBtnSwitch.setText(R.string.host_btn_stop_scan)
                binding.hostBtnCopy.visibility = View.INVISIBLE
                binding.hostProgress.visibility = View.VISIBLE
                binding.hostStateImage.visibility = View.INVISIBLE
                binding.hostStateText.setText(R.string.host_text_scanning)
                binding.hostStateText.setTextColor(Color.BLUE)

                binding.hostBtnSwitch.setOnClickListener {
                    EnchantNet.get().stop()
                }
            }

            EnchantNetState.HOSTING -> {
                if (snap.inviteCode.isNullOrBlank())
                    return

                binding.hostBtnSwitch.isEnabled = true
                binding.hostBtnCopy.isEnabled = true
                binding.hostBtnSwitch.setText(R.string.host_btn_exit)
                binding.hostBtnCopy.visibility = View.VISIBLE
                binding.hostProgress.visibility = View.INVISIBLE
                binding.hostStateImage.visibility = View.VISIBLE
                binding.hostStateText.text = getString(R.string.host_text_hosting, snap.inviteCode)
                binding.hostStateImage.setImageResource(R.drawable.baseline_connected_24)
                binding.hostStateText.setTextColor(Color.GREEN)

                copyInviteFromSnapshot(snap)

                binding.hostBtnCopy.setOnClickListener {
                    copyInviteFromSnapshot(snap)
                }
                binding.hostBtnSwitch.setOnClickListener {
                    EnchantNet.get().stop()
                }
            }

            EnchantNetState.EXCEPTION -> {
                var err = "Unknown Error"
                if (snap.exception == EnchantNetException.START_FAILED)
                    err = getString(R.string.host_text_exception_fail)
                if (snap.exception == EnchantNetException.HOST_EASYTIER_CRASH)
                    err = getString(R.string.host_text_exception_crash)
                if (snap.exception == EnchantNetException.HOST_GAME_CLOSED)
                    err = getString(R.string.host_text_exception_lost)
                binding.hostBtnSwitch.isEnabled = true
                binding.hostBtnCopy.isEnabled = false
                binding.hostBtnSwitch.setText(R.string.host_btn_stop_task)
                binding.hostBtnCopy.visibility = View.INVISIBLE
                binding.hostProgress.visibility = View.INVISIBLE
                binding.hostStateImage.visibility = View.VISIBLE
                binding.hostStateText.text = getString(R.string.host_text_exception, err)
                binding.hostStateImage.setImageResource(R.drawable.baseline_exit_room_24)
                binding.hostStateText.setTextColor(Color.RED)

                binding.hostBtnSwitch.setOnClickListener {
                    EnchantNet.get().stop()
                }
            }

            EnchantNetState.GUESTING -> { /* Fk this shit */
            }
        }
    }

    private fun copyInviteCode(code: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("invite_code", code))
        Toast.makeText(this, getString(R.string.action_copy_invite), Toast.LENGTH_SHORT).show()
    }

    private fun copyInviteFromSnapshot(snap: EnchantNetSnapshot) {
        val code = snap.inviteCode

        if (code.isNullOrBlank())
            return

        copyInviteCode(code)
    }

    override fun onDestroy() {
        EnchantNet.get().removeListener(stateListener)
        EnchantNet.get().stop()
        super.onDestroy()
    }
}