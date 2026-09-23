package com.victory.shakeguard

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.provider.Settings

/**
 * 下拉通知栏快捷磁贴：一键开关全机传感器。
 * 开拼多多前一秒关、退出后一秒开，比进 App 更顺手。
 */
class SensorsTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val off = Settings.Secure.getInt(contentResolver, "sensors_off", 0) == 1
        Settings.Secure.putInt(contentResolver, "sensors_off", if (off) 0 else 1)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val off = Settings.Secure.getInt(contentResolver, "sensors_off", 0) == 1
        tile.state = if (off) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (off) "已关闭 · 摇一摇失效" else "开启中"
        tile.updateTile()
    }
}
