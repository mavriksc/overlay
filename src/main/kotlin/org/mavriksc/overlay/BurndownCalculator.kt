package org.mavriksc.overlay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.mavriksc.overlay.lolservice.ActivePlayerData
import org.mavriksc.overlay.lolservice.ChampDataService
import org.mavriksc.overlay.lolservice.Champion
import java.awt.Color

//how many casts of a spell before you can't cast a full rotation
//red <= 1
//yellow <= 2
//green > 2

class BurndownCalculator(overlay: GameOverlay, activePlayerData: Flow<ActivePlayerData?>) {
    //TODO fix spell indicators are all there instead of just on leveled up spells only.
    private val champDataService: ChampDataService = ChampDataService()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var champion: Champion? = null
    private var lastChampionLevel = 0
    private var spellCosts = listOf(0.0f, 0.0f, 0.0f, 0.0f)
    private var spellState = List(4) { Pair(Color.RED, false) }.toMutableList()

    init {
        scope.launch {
            activePlayerData
                .collect { data ->
                    data?.let {
                        //println("Received active player data: $it")
                        champion?.let { _ -> everyUpdate(it) } ?: gameStartStuff(it)
                        overlay.spellStates = spellState.toList()
                    }
                }
        }
    }

    private fun gameStartStuff(data: ActivePlayerData) {
        champion = champDataService.getChampion(data.championName)
        // get initial values
        println("game start stuff: ${champion?.name}")
        everyUpdate(data)
    }

    private fun everyUpdate(data: ActivePlayerData) {
        println("every update: $data")
        val level = data.spellLevels.map { it.value }.sum()
        if (level != lastChampionLevel) {
            spellCosts = calculateSpellCosts(data)
            lastChampionLevel = level
        }
        setSpellStatus(data)
    }

    private fun setSpellStatus(data: ActivePlayerData) {
        val availAfterFullRotation = data.currentResources - spellCosts.sum()
        if (availAfterFullRotation >= 0) {
            spellCosts.forEachIndexed { i, cost ->
                val casts = if (cost > 0) availAfterFullRotation / cost else 9.0f
                when {
                    casts >= 2 -> spellState[i] = Pair(Color.GREEN, true)
                    casts >= 1 -> spellState[i] = Pair(Color.YELLOW, true)
                    else -> spellState[i] = Pair(Color.RED, true)
                }
            }
        } else spellState = List(4) { Pair(Color.RED, false) }.toMutableList()
    }

    private fun calculateSpellCosts(data: ActivePlayerData) =
        data.spellLevels.map { (spell, level) ->
            println("spell: $spell, level: $level")
            champion!!.abilities.first { it.name == spell }.cost!![level]
        }

}
