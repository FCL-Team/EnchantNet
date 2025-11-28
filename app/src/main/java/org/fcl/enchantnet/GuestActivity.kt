package org.fcl.enchantnet

import android.content.ClipData
import android.content.ClipboardManager
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
import com.google.android.material.color.MaterialColors
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.burningtnt.terracotta.TerracottaAndroidAPI
import net.burningtnt.terracotta.TerracottaState
import net.burningtnt.terracotta.utils.JsonTypeAdapterFactory
import org.fcl.enchantnet.databinding.ActivityGuestBinding
import com.google.android.material.R as MaterialR

class GuestActivity : AppCompatActivity() {

    private val binding by lazy { ActivityGuestBinding.inflate(layoutInflater) }
    private lateinit var backCallback: OnBackPressedCallback

    private var currentState: TerracottaState.Ready = object : TerracottaState.Ready(0, -1, "") {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.isNavigationBarContrastEnforced = false
        }

        setContentView(binding.root)

        setSupportActionBar(binding.bar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        TerracottaAndroidAPI.setWaiting()

        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentState is TerracottaState.Waiting) {
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

        val handler = Handler()
        handler.post(object : Runnable {
            override fun run() {
                val stateJson = TerracottaAndroidAPI.getState()
                val state = GsonBuilder()
                    .enableComplexMapKeySerialization()
                    .setPrettyPrinting()
                    .registerTypeAdapterFactory(JsonTypeAdapterFactory.INSTANCE)
                    .create()
                    .fromJson(stateJson, TypeToken.get(TerracottaState.Ready::class.java))
                if (state.index > currentState.index) {
                    renderState(state)
                }
                currentState = state
                handler.postDelayed(this, 500L)
            }
        })
    }

    private fun renderState(state: TerracottaState.Ready) {
        if (state is TerracottaState.Waiting) {
            binding.guestBtnSwitch.isEnabled = true
            binding.guestBtnCopy.isEnabled = false
            binding.guestBtnSwitch.setText(R.string.guest_btn_start)
            binding.guestBtnCopy.visibility = View.INVISIBLE
            binding.guestProgress.visibility = View.INVISIBLE
            binding.guestStateImage.visibility = View.VISIBLE
            binding.guestStateText.setText(R.string.guest_text_waiting)
            binding.guestStateImage.setImageResource(R.drawable.baseline_guest_24)
            binding.guestStateText.setTextColor(
                MaterialColors.getColor(this, MaterialR.attr.colorOnBackground, 0)
            )
            binding.guestInput.setText("")
            binding.guestInputLayout.visibility = View.VISIBLE
            binding.guestInputLayout.error = null
            binding.guestInputLayout.helperText = getString(R.string.guest_input_helper)

            binding.guestInput.doOnTextChanged { text, _, _, _ ->
//                val s = text?.toString().orEmpty()
//                val kind = InviteQuickValidator.quickDetectKind(s)
//                if (kind == RoomKind.INVALID) {
//                    binding.guestInputLayout.error = getString(R.string.toast_invalid_code)
//                } else {
//                    binding.guestInputLayout.error = null
//                    binding.guestInputLayout.helperText = getString(R.string.guest_input_helper)
//                }
            }
            binding.guestInput.doAfterTextChanged { e ->
//                val s = e?.toString().orEmpty()
//                val kind = InviteQuickValidator.quickDetectKind(s)
//                if (kind == RoomKind.INVALID) {
//                    binding.guestInputLayout.error = getString(R.string.toast_invalid_code)
//                } else {
//                    binding.guestInputLayout.error = null
//                    binding.guestInputLayout.helperText = getString(R.string.guest_input_helper)
//                }
            }

            binding.guestBtnSwitch.setOnClickListener {
                val valid = TerracottaAndroidAPI.setGuesting(binding.guestInput.text.toString(), null)
                if (!valid) {
                    Toast.makeText(this, R.string.toast_invalid_code, Toast.LENGTH_SHORT).show()
                } else {
                    binding.guestBtnSwitch.isEnabled = false
                }
            }
        }
        if (state is TerracottaState.GuestStarting) {
            binding.guestBtnSwitch.isEnabled = true
            binding.guestBtnSwitch.setText(R.string.guest_btn_exit)
            binding.guestInput.setText("")
            binding.guestInputLayout.visibility = View.INVISIBLE
            binding.guestInputLayout.error = null
            binding.guestInputLayout.helperText = getString(R.string.guest_input_helper)
            binding.guestBtnCopy.isEnabled = false
            binding.guestBtnCopy.visibility = View.INVISIBLE
            binding.guestProgress.visibility = View.VISIBLE
            binding.guestStateImage.visibility = View.INVISIBLE
            binding.guestStateText.setText(R.string.guest_text_connecting)
            binding.guestStateText.setTextColor(
                MaterialColors.getColor(this, MaterialR.attr.colorOnPrimaryContainer, 0)
            )

            binding.guestBtnSwitch.setOnClickListener {
                MainActivity.setWaiting(this)
                binding.guestBtnSwitch.isEnabled = false
            }
        }
        if (state is TerracottaState.GuestOK) {
            binding.guestBtnSwitch.isEnabled = true
            binding.guestBtnSwitch.setText(R.string.guest_btn_exit)
            binding.guestInput.setText("")
            binding.guestInputLayout.visibility = View.INVISIBLE
            binding.guestInputLayout.error = null
            binding.guestInputLayout.helperText = getString(R.string.guest_input_helper)
            binding.guestBtnCopy.isEnabled = true
            binding.guestBtnCopy.visibility = View.VISIBLE
            binding.guestProgress.visibility = View.INVISIBLE
            binding.guestStateImage.visibility = View.VISIBLE
            binding.guestStateText.text =
                getString(R.string.guest_text_guesting, state.url)
            binding.guestStateImage.setImageResource(R.drawable.baseline_connected_24)
            binding.guestStateText.setTextColor(
                MaterialColors.getColor(this, MaterialR.attr.colorPrimary, 0)
            )

            binding.guestBtnCopy.setOnClickListener {
                copyServerAddress(state.url)
            }

            binding.guestBtnSwitch.setOnClickListener {
                MainActivity.setWaiting(this)
                binding.guestBtnSwitch.isEnabled = false
            }
        }
        if (state is TerracottaState.Exception) {
            var err = "Unknown Error"
            if (state.type == TerracottaState.Exception.Type.PING_HOST_FAIL)
                err = getString(R.string.guest_text_exception_fail)
            if (state.type == TerracottaState.Exception.Type.GUEST_ET_CRASH)
                err = getString(R.string.guest_text_exception_crash)
            if (state.type == TerracottaState.Exception.Type.PING_HOST_RST)
                err = getString(R.string.guest_text_exception_lost)
            binding.guestBtnSwitch.isEnabled = true
            binding.guestBtnCopy.isEnabled = false
            binding.guestBtnSwitch.setText(R.string.guest_btn_stop_task)
            binding.guestBtnCopy.visibility = View.INVISIBLE
            binding.guestProgress.visibility = View.INVISIBLE
            binding.guestStateImage.visibility = View.VISIBLE
            binding.guestStateText.text = getString(R.string.guest_text_exception, err)
            binding.guestStateImage.setImageResource(R.drawable.baseline_exit_room_24)
            binding.guestStateText.setTextColor(
                MaterialColors.getColor(this, MaterialR.attr.colorError, 0)
            )
            binding.guestInput.setText("")
            binding.guestInputLayout.visibility = View.INVISIBLE
            binding.guestInputLayout.error = null
            binding.guestInputLayout.helperText = getString(R.string.guest_input_helper)

            binding.guestBtnSwitch.setOnClickListener {
                MainActivity.setWaiting(this)
                binding.guestBtnSwitch.isEnabled = false
            }
        }
    }

    private fun copyServerAddress(address: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("server_address", address))
        Toast.makeText(this, getString(R.string.action_copy_address), Toast.LENGTH_SHORT).show()
    }

    // 重写 onSupportNavigateUp 方法来处理返回按钮的点击事件
    override fun onSupportNavigateUp(): Boolean {
        // 当点击返回按钮时，执行此方法
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        MainActivity.setWaiting(this)
        super.onDestroy()
    }
}