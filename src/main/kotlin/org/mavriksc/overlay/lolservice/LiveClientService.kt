package org.mavriksc.overlay.lolservice

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import org.mavriksc.overlay.getOkHttpClientForGameClient
import org.mavriksc.overlay.toRequest
import java.io.Closeable
import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

open class LiveClientService(private val autoStartPolling: Boolean = true) : Closeable {
    private val logger = Logger.getLogger(LiveClientService::class.java.name)
    private val client = getOkHttpClientForGameClient(5, TimeUnit.SECONDS)
    private val activePlayerURL = "https://localhost:2999/liveclientdata/activeplayer"
    private val abilityKeys = listOf("Q", "W", "E", "R")
    private val _activePlayerData = MutableStateFlow<ActivePlayerData?>(null)
    private var pollingJob: Job? = null
    val activePlayerData: StateFlow<ActivePlayerData?> = _activePlayerData.asStateFlow()

    init {
        if (autoStartPolling) {
            pollingJob = CoroutineScope(Dispatchers.IO).launch {
                while (isActive) {
                    fetchActivePlayer()
                    delay(1_000)
                }
            }
        }
    }

    override fun close() {
        pollingJob?.cancel()
    }

    fun fetchActivePlayer() {
        try {
            client.newCall(activePlayerURL.toRequest()).execute().use { response ->
                val body = response.body.string()
                val bodyObject = Json.parseToJsonElement(body).jsonObject
                val stats = parseStats(bodyObject)
                _activePlayerData.value = stats
            }
        } catch (e: Exception) {
            logger.log(Level.WARNING, "Failed to fetch active player data", e)
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
