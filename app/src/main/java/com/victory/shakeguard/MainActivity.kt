package com.victory.shakeguard

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * ShakeGuard —— 安卓版「Restrict Motion Data」
 *
 * 原理：安卓没有 iOS 那种按 App 拒绝运动传感器授权的机制（加速度计读取无需任何
 * 运行时权限），但系统提供了一个开发者级总开关 Settings.Secure"sensors_off"，
 * 置 1 后整个系统的加速度计/陀螺仪/计步器全部静默，摇一摇跳广告的 App 直接拿不到
 * 任何运动数据。
 *
 * 本 App 通过 WRITE_SECURE_SETTINGS 权限（需 adb 授权一次）读写这个开关，
 * 实现：一键开关 + 下拉快捷磁贴 + 定时自动恢复。
 */
class MainActivity : Activity() {

    private lateinit var statusText: TextView
    private lateinit var mainSwitch: Switch
    private lateinit var restoreHint: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var autoRestoreRunnable: Runnable? = null

    private val adbCommand =
        "adb shell pm grant com.victory.shakeguard android.permission.WRITE_SECURE_SETTINGS"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
            setBackgroundColor(Color.parseColor("#FAFAFA"))
        }

        val title = TextView(this).apply {
            text = "🛡 摇一摇克星 ShakeGuard"
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1B1B1B"))
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "关闭传感器后，拼多多/闲鱼们的「摇一摇跳广告」\n会因为拿不到加速度计数据而彻底失效"
            textSize = 13f
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, dp(8), 0, dp(16))
        }
        root.addView(subtitle)

        statusText = TextView(this).apply {
            textSize = 16f
            setPadding(0, dp(16), 0, dp(16))
        }
        root.addView(statusText)

        mainSwitch = Switch(this).apply {
            textSize = 18f
            setPadding(0, dp(12), 0, dp(12))
            setOnCheckedChangeListener { _, checked ->
                if (!hasPermission()) {
                    // 没授权时弹回开关
                    isChecked = !checked
                    showPermissionDialog()
                    return@setOnCheckedChangeListener
                }
                setSensorsOff(checked)
                if (checked) {
                    vibrateTick()
                }
                refreshStatus()
            }
        }
        root.addView(mainSwitch)

        restoreHint = TextView(this).apply {
            text = "定时恢复：开启传感器后自动关闭传感器 N 分钟（防止忘开导航/计步）"
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, dp(16), 0, dp(4))
        }
        root.addView(restoreHint)

        val timerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(1 to "1 分钟", 5 to "5 分钟", 10 to "10 分钟").forEach { (min, label) ->
            timerRow.addView(Button(this).apply {
                text = label
                setOnClickListener { scheduleRestore(min) }
            })
        }
        root.addView(timerRow)

        val guideTitle = TextView(this).apply {
            text = "首次使用（仅需一次）\n手机连电脑执行一条 adb 命令："
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(24), 0, dp(8))
        }
        root.addView(guideTitle)

        val cmdView = TextView(this).apply {
            text = adbCommand
            textSize = 12f
            setTextColor(Color.parseColor("#0B57D0"))
            setBackgroundColor(Color.parseColor("#EEF3FE"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setOnClickListener { copyCommand() }
        }
        root.addView(cmdView)

        val copyBtn = Button(this).apply {
            text = "复制命令并打开无线调试说明"
            setOnClickListener { copyCommand(); openDeveloperOptions() }
        }
        root.addView(copyBtn)

        val explain = TextView(this).apply {
            text = """
                原理说明：
                · 安卓读取加速度计不需要权限，系统层面无法像 iOS 27.2 那样按 App 精确拒绝；
                · 但 sensors_off 开关可以一键让全机传感器静默，摇一摇检测直接失效；
                · 副作用：自动横屏、计步、抬手亮屏、指南针、部分导航会暂时失效，用完记得开回来；
                · 推荐用下拉通知栏的「传感器开关」磁贴，开 App 前一秒关、退出后一秒开。
            """.trimIndent()
            textSize = 12f
            setTextColor(Color.parseColor("#555555"))
            setPadding(0, dp(24), 0, 0)
        }
        root.addView(explain)

        setContentView(ScrollView(this).apply { addView(root) })
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun hasPermission(): Boolean =
        checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    private fun isSensorsOff(): Boolean =
        Settings.Secure.getInt(contentResolver, "sensors_off", 0) == 1

    private fun setSensorsOff(off: Boolean) {
        Settings.Secure.putInt(contentResolver, "sensors_off", if (off) 1 else 0)
        cancelAutoRestore()
    }

    private fun refreshStatus() {
        if (!hasPermission()) {
            statusText.text = "❌ 尚未授权：请先执行下方 adb 命令"
            statusText.setTextColor(Color.parseColor("#D93025"))
            mainSwitch.isEnabled = true
            mainSwitch.isChecked = false
            return
        }
        if (isSensorsOff()) {
            statusText.text = "🚫 传感器已关闭 —— 摇一摇广告已被封印"
            statusText.setTextColor(Color.parseColor("#D93025"))
            mainSwitch.isChecked = true
        } else {
            statusText.text = "✅ 传感器正常 —— App 可以摇（也能跳广告）"
            statusText.setTextColor(Color.parseColor("#188038"))
            mainSwitch.isChecked = false
        }
    }

    private fun scheduleRestore(minutes: Int) {
        if (!isSensorsOff()) {
            Toast.makeText(this, "当前传感器本来就是开的，直接去摇吧", Toast.LENGTH_SHORT).show()
            return
        }
        cancelAutoRestore()
        statusText.text = "⏳ 传感器将在 $minutes 分钟后自动恢复…"
        autoRestoreRunnable = Runnable {
            if (hasPermission() && isSensorsOff()) {
                Settings.Secure.putInt(contentResolver, "sensors_off", 0)
                refreshStatus()
                Toast.makeText(this, "传感器已自动恢复 ✅", Toast.LENGTH_SHORT).show()
            }
        }.also { handler.postDelayed(it, minutes * 60_000L) }
        Toast.makeText(this, "已设置 $minutes 分钟后自动恢复", Toast.LENGTH_SHORT).show()
    }

    private fun cancelAutoRestore() {
        autoRestoreRunnable?.let { handler.removeCallbacks(it) }
        autoRestoreRunnable = null
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("需要一次性授权")
            .setMessage("安卓不允许普通 App 直接改系统设置，需要用电脑执行一次 adb 命令（仅第一次）：\n\n$adbCommand\n\n之后永久生效，无需再连电脑。")
            .setPositiveButton("复制命令并打开开发者选项") { _, _ -> copyCommand(); openDeveloperOptions() }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun copyCommand() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("adb", adbCommand))
        Toast.makeText(this, "adb 命令已复制", Toast.LENGTH_SHORT).show()
    }

    private fun openDeveloperOptions() {
        try {
            startActivity(android.content.Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        } catch (e: Exception) {
            startActivity(android.content.Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun vibrateTick() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(30, 80))
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
