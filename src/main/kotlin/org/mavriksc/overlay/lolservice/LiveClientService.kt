package org.mavriksc.overlay.lolservice

import okhttp3.OkHttpClient

class LiveClientService {
    // Subscribe to game start and end events for the starting and stopping the UI
    // poll active player data for calculating spell burndown
    // CDR calculation: ActualCooldown = Cooldown * (100/(100 + AbilityHase))
    // 2 methods for getting active champ.
    // activeplayer api will have the champ name in the ability field `id`: "{champ}{abilityKey}" prob easiest
    // get all players and match the player name versus the active player name
    private val client = OkHttpClient()
    private val activePlayerURL = "http://localhost:2999/liveclientdata/activeplayer"
    private val abilityKeys = listOf("Q", "W", "E", "R")


}