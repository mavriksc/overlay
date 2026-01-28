package org.mavriksc.overlay.lolservice

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import org.mavriksc.overlay.toRequest
import java.io.Closeable

class LiveClientService : Closeable {
    // Subscribe to game start and end events for the starting and stopping the UI
    // poll active player data for calculating spell burndown
    // CDR calculation: ActualCooldown = Cooldown * (100/(100 + AbilityHase))
    // 2 methods for getting active champ.
    // activeplayer api will have the champ name in the ability field `id`: "{champ}{abilityKey}" probably the easiest
    // get all players and match the player name versus the active player name
    private val client = OkHttpClient()
    private val activePlayerURL = "http://localhost:2999/liveclientdata/activeplayer"
    private val abilityKeys = listOf("Q", "W", "E", "R")
    private val _activePlayerData = MutableStateFlow<ActivePlayerData?>(null)
    private val pollingJob = Job()
    val activePlayerData: StateFlow<ActivePlayerData?> = _activePlayerData.asStateFlow()

    init {
        CoroutineScope(Dispatchers.Default + pollingJob).launch {
            while (isActive) {
                fetchActivePlayer()
                delay(1_000)
            }
        }
    }

    override fun close() {
        pollingJob.cancel()
    }

    fun fetchActivePlayer(): String {
        val activePlayerRequest = activePlayerURL.toRequest()
        client.newCall(activePlayerRequest).execute().use { response ->
            val body = response.body.string()
            val bodyObject = Json.parseToJsonElement(body).jsonObject
            val stats = parseStats(bodyObject)
            _activePlayerData.value = stats
            return body
        }
    }

    private fun parseStats(bodyObject: JsonObject): ActivePlayerData {
        val championStats = bodyObject["championStats"]!!.jsonObject
        val abilityHaste = championStats["abilityHaste"]!!.jsonPrimitive.float
        val resourceMax = championStats["resourceMax"]!!.jsonPrimitive.float
        val resourceRegenRate = championStats["resourceRegenRate"]!!.jsonPrimitive.float
        val resourceValue = championStats["resourceValue"]!!.jsonPrimitive.float
        val abilities = parseAbilities(bodyObject)
        val name = bodyObject["abilities"]!!.jsonObject["Q"]!!.jsonObject["id"]!!.jsonPrimitive.content.dropLast(1)
        return ActivePlayerData(
            name,
            resourceMax,
            resourceValue,
            resourceRegenRate,
            abilityHaste,
            abilities
        )
    }

    private fun parseAbilities(bodyObject: JsonObject): Map<String, Int> {
        val abilitiesMap = bodyObject["abilities"]!!.jsonObject
        return abilityKeys.associate { key ->
            val abilityObject = abilitiesMap[key]?.jsonObject!!
            val level = abilityObject["abilityLevel"]!!.jsonPrimitive.int
            val name = abilityObject["displayName"]!!.jsonPrimitive.content
            Pair(name, level)
        }
    }

}
