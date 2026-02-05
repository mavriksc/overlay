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

//how many casts of a spell before you can't cast a full rotation
//red <= 1
//yellow <= 2
//green > 2

class BurndownCalculator : Closeable {
    private val champDataService: ChampDataService = ChampDataService()
    private val liveClientService: LiveClientService = LiveClientService()
    private val activePlayerData: Flow<ActivePlayerData?> = liveClientService.activePlayerData
    private var latestActivePlayerData: ActivePlayerData? = null
    private var champion: Champion? = null
    private var lastChampionLevel = 0
    private var spellCosts = listOf(0.0f, 0.0f, 0.0f, 0.0f)
    private var spellState = List(4) { Pair(Color.RED, false) }.toMutableList()
    var gameOver = false
        private set

    fun getSpellState() = spellState.toList()


    init {
        CoroutineScope(Dispatchers.Default).launch {
            activePlayerData
                .onCompletion { stop() }
                .collect { data ->
                    if (data == null) return@collect
                    println("Received active player data: $data")
                    latestActivePlayerData = data
                    champion?.let { _ -> everyUpdate() } ?: gameStartStuff()

                }
        }
    }

    private fun gameStartStuff() {
        champion = champDataService.getChampion(latestActivePlayerData!!.championName)
        // get initial values
        println("game start stuff: ${champion?.name}")
        everyUpdate()
    }

    private fun everyUpdate() {
        latestActivePlayerData?.let { data ->
            println("every update: $data")
            val level = data.spellLevels.map { it.value }.sum()
            if (level != lastChampionLevel) {

                spellCosts = calculateSpellCosts(data)
                lastChampionLevel = level
            }
            setSpellStatus(data)
        }
    }

    private fun setSpellStatus(data: ActivePlayerData) {
        val availAfterFullRotation = data.currentResources - spellCosts.sum()
        if (availAfterFullRotation >= 0) {
            spellCosts.forEachIndexed { i, cost ->
                val casts = availAfterFullRotation / cost
                when {
                    casts >= 2 -> spellState[i] = Pair(Color.GREEN, true)
                    casts >= 1 -> spellState[i] = Pair(Color.YELLOW, true)
                    else -> spellState[i] = Pair(Color.RED, true)
                }
            }
        }
    }

    private fun calculateSpellCosts(data: ActivePlayerData) =
        data.spellLevels.map { (spell, level) ->
            champion!!.abilities.first { it.name == spell }.cost!![level]
        }

    private fun stop() {
        latestActivePlayerData = null
        gameOver = true
        liveClientService.close()
        println("Burndown calculator stopped")
    }

    override fun close() {
        stop()
    }
}
