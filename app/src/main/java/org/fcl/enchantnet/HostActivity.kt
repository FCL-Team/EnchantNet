package org.fcl.enchantnet

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.MaterialColors
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.burningtnt.terracotta.TerracottaAndroidAPI
import net.burningtnt.terracotta.TerracottaState
import net.burningtnt.terracotta.utils.JsonTypeAdapterFactory
import org.fcl.enchantnet.databinding.ActivityHostBinding
import com.google.android.material.R as MaterialR


class HostActivity : AppCompatActivity() {

    private val binding by lazy { ActivityHostBinding.inflate(layoutInflater) }
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
                        this@HostActivity,
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
            binding.hostBtnSwitch.isEnabled = true
            binding.hostBtnCopy.isEnabled = false
            binding.hostBtnSwitch.setText(R.string.host_btn_start_scan)
            binding.hostBtnCopy.visibility = View.INVISIBLE
            binding.hostProgress.visibility = View.INVISIBLE
            binding.hostStateImage.visibility = View.VISIBLE
            binding.hostStateText.setText(R.string.host_text_waiting)
            binding.hostStateImage.setImageResource(R.drawable.baseline_host_24)
            binding.hostStateText.setTextColor(
                MaterialColors.getColor(this, MaterialR.attr.colorOnBackground, 0)
            )

            binding.hostBtnSwitch.setOnClickListener {
                binding.hostBtnSwitch.isEnabled = false
                TerracottaAndroidAPI.setScanning(null, null)
            }
        }
        if (state is TerracottaState.HostScanning || state is TerracottaState.HostStarting) {
            binding.hostBtnSwitch.isEnabled = true
            binding.hostBtnCopy.isEnabled = false
            binding.hostBtnSwitch.setText(R.string.host_btn_stop_scan)
            binding.hostBtnCopy.visibility = View.INVISIBLE
            binding.hostProgress.visibility = View.VISIBLE
            binding.hostStateImage.visibility = View.INVISIBLE
            binding.hostStateText.setText(R.string.host_text_scanning)
            binding.hostStateText.setTextColor(
                MaterialColors.getColor(this, MaterialR.attr.colorOnPrimaryContainer, 0)
            )

            binding.hostBtnSwitch.setOnClickListener {
                MainActivity.setWaiting(this)
                binding.hostBtnSwitch.isEnabled = false
            }
        }
        if (state is TerracottaState.HostOK) {
            binding.hostBtnSwitch.isEnabled = true
            binding.hostBtnCopy.isEnabled = true
            binding.hostBtnSwitch.setText(R.string.host_btn_exit)
            binding.hostBtnCopy.visibility = View.VISIBLE
            binding.hostProgress.visibility = View.INVISIBLE
            binding.hostStateImage.visibility = View.VISIBLE
            binding.hostStateText.text = getString(R.string.host_text_hosting, state.code)
            binding.hostStateImage.setImageResource(R.drawable.baseline_connected_24)
            binding.hostStateText.setTextColor(
                MaterialColors.getColor(this, MaterialR.attr.colorPrimary, 0)
            )

            copyInviteCode(state.code)

            binding.hostBtnCopy.setOnClickListener {
                copyInviteCode(state.code)
            }
            binding.hostBtnSwitch.setOnClickListener {
                MainActivity.setWaiting(this)
                binding.hostBtnSwitch.isEnabled = false
            }
        }
        if (state is TerracottaState.Exception) {
            var err = "Unknown Error"
            if (state.type == TerracottaState.Exception.Type.PING_HOST_RST)
                err = getString(R.string.host_text_exception_fail)
            if (state.type == TerracottaState.Exception.Type.HOST_ET_CRASH)
                err = getString(R.string.host_text_exception_crash)
            if (state.type == TerracottaState.Exception.Type.PING_SERVER_RST)
                err = getString(R.string.host_text_exception_lost)
            binding.hostBtnSwitch.isEnabled = true
            binding.hostBtnCopy.isEnabled = false
            binding.hostBtnSwitch.setText(R.string.host_btn_stop_task)
            binding.hostBtnCopy.visibility = View.INVISIBLE
            binding.hostProgress.visibility = View.INVISIBLE
            binding.hostStateImage.visibility = View.VISIBLE
            binding.hostStateText.text = getString(R.string.host_text_exception, err)
            binding.hostStateImage.setImageResource(R.drawable.baseline_exit_room_24)
            binding.hostStateText.setTextColor(
                MaterialColors.getColor(this, MaterialR.attr.colorError, 0)
            )

            binding.hostBtnSwitch.setOnClickListener {
                MainActivity.setWaiting(this)
                binding.hostBtnSwitch.isEnabled = false
            }
        }
    }

    private fun copyInviteCode(code: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("invite_code", code))
        Toast.makeText(this, getString(R.string.action_copy_invite), Toast.LENGTH_SHORT).show()
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