package org.mavriksc.overlay.lolservice

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.*
import org.mavriksc.overlay.getOkHttpClientForGameClient
import org.mavriksc.overlay.toRequest
import java.io.Closeable
import java.util.concurrent.TimeUnit

class LiveClientService : Closeable {
    // We're just going to kill this when the window closes, and that will work.
    // this should poll for game start event and game end event or add that to the detector.
    // but since its info is from hitting game with api maybe here is better.

    private val client = getOkHttpClientForGameClient(5, TimeUnit.SECONDS)
    private val activePlayerURL = "https://localhost:2999/liveclientdata/activeplayer"
    private val eventDataURL = "https://localhost:2999/liveclientdata/eventdata"
    private val abilityKeys = listOf("Q", "W", "E", "R")
    private val _activePlayerData = MutableStateFlow<ActivePlayerData?>(null)
    private val _events = MutableSharedFlow<LiveClientEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var pollingJob: Job? = null
    private var lastEventId = -1
    val activePlayerData: StateFlow<ActivePlayerData?> = _activePlayerData.asStateFlow()
    val events: SharedFlow<LiveClientEvent> = _events.asSharedFlow()

    init {
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                fetchActivePlayer()
                fetchEvents()
                delay(1_000)
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
            println("Failed to fetch active player data: ${e.message}")
        }
    }

    private fun fetchEvents() {
        try {
            client.newCall(eventDataURL.toRequest()).execute().use { response ->
                val body = response.body.string()
                val bodyObject = Json.parseToJsonElement(body).jsonObject
                val eventsArray = bodyObject["Events"]?.jsonArray ?: return
                val newEvents = eventsArray.mapNotNull { eventElement ->
                    val eventObject = eventElement.jsonObject
                    val eventId = eventObject["EventID"]?.jsonPrimitive?.int ?: return@mapNotNull null
                    if (eventId <= lastEventId) return@mapNotNull null
                    val eventName = eventObject["EventName"]?.jsonPrimitive?.content ?: "Unknown"
                    val eventTime = eventObject["EventTime"]?.jsonPrimitive?.floatOrNull
                    LiveClientEvent(eventId, eventName, eventTime, eventObject)
                }.sortedBy { it.eventId }
                if (newEvents.isNotEmpty()) {
                    lastEventId = newEvents.maxOf { it.eventId }
                    newEvents.forEach { _events.tryEmit(it) }
                }
            }
        } catch (e: Exception) {
            println("Failed to fetch live client events: ${e.message}")
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
