package org.mavriksc.overlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import org.mavriksc.overlay.lolservice.ActivePlayerData
import org.mavriksc.overlay.lolservice.ChampDataService
import org.mavriksc.overlay.lolservice.Champion
import org.mavriksc.overlay.lolservice.LiveClientService
import java.awt.Color
import java.io.Closeable

class BurndownCalculator : Closeable {
    private val champDataService: ChampDataService = ChampDataService()
    private val liveClientService: LiveClientService = LiveClientService()
    private val activePlayerData: Flow<ActivePlayerData?> = liveClientService.activePlayerData
    private var latestActivePlayer: ActivePlayerData? = null
    private var champion: Champion? = null
    var gameOver = false
        private set
    var qSignal: Color = Color.RED
        private set
    var qAvailable: Boolean = false
        private set
    var wSignal: Color = Color.RED
        private set
    var wAvailable: Boolean = false
        private set
    var eSignal: Color = Color.RED
        private set
    var eAvailable: Boolean = false
        private set
    var rSignal: Color = Color.RED
        private set
    var rAvailable: Boolean = false
        private set


    init {
        CoroutineScope(Dispatchers.Default).launch {
            activePlayerData
                .onCompletion { stop() }
                .collect { data ->
                    if (data == null) return@collect
                    latestActivePlayer = data
                    if (champion == null) champion = champDataService.getChampion(latestActivePlayer!!.championName)
                    println("Received active player data: $data")
                }
        }
    }

    private fun stop() {
        latestActivePlayer = null
        gameOver = true
        liveClientService.close()
        println("Burndown calculator stopped")
    }

    override fun close() {
        stop()
    }
}
