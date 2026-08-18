package com.recall.android.capture

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.recall.android.R
import com.recall.android.RecallApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PauseTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val settings = (application as RecallApplication).container.settings
        val nextPaused = !settings.state.value.collectionPaused
        scope.launch {
            settings.setPaused(nextPaused)
            updateTile()
        }
    }

    private fun updateTile() {
        val state = (application as RecallApplication).container.settings.state.value
        qsTile?.apply {
            this.state = if (state.collectionPaused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            label = getString(if (state.collectionPaused) R.string.pause_tile_paused else R.string.pause_tile_recording)
            updateTile()
        }
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }
}
