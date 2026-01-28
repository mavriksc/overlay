package org.mavriksc.overlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.mavriksc.overlay.lolservice.ActivePlayerData
import org.mavriksc.overlay.lolservice.ChampDataService
import org.mavriksc.overlay.lolservice.Champion
import org.mavriksc.overlay.lolservice.LiveClientService
import java.awt.Color
import java.io.Closeable

class BurndownCalculator: Closeable {
    private val champDataService: ChampDataService = ChampDataService()
    private val liveClientService: LiveClientService = LiveClientService()
    private var job: Job? = null
    private val activePlayerData: Flow<ActivePlayerData?> = liveClientService.activePlayerData
    private var latestActivePlayer: ActivePlayerData? = null
    private var champion: Champion? = null
    var qSignal: Color = Color.RED
    var qAvailable: Boolean = false
    var wSignal: Color = Color.RED
    var wAvailable: Boolean = false
    var eSignal: Color = Color.RED
    var eAvailable: Boolean = false
    var rSignal: Color = Color.RED
    var rAvailable: Boolean = false


    fun start() {
        if (job != null) return
        job = CoroutineScope(Dispatchers.Default).launch {
            activePlayerData.collect { data ->
                if (data == null) return@collect
                latestActivePlayer = data
                if (champion == null) champion = champDataService.getChampion(latestActivePlayer!!.championName)
                println("Received active player data: $data")
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        latestActivePlayer = null
    }

    override fun close() {
        stop()
    }
}
