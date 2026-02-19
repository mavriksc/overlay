package org.mavriksc.overlay.lolservice

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Ability(val name: String, val cooldowns: List<Float>, val cost: List<Float>?, val costBurn: List<Float>?)

@Serializable
data class Stats(val mp: Float, val mpPerLevel: Float, val mpRegen: Float, val mpRegenPerLevel: Float)

@Serializable
data class Champion(val name: String, val stats: Stats, val abilities: List<Ability>)

@Serializable
data class Version(val version: String, val champions: List<Champion>)

@Serializable
data class ActivePlayerData(
    val championName: String,
    val maxResources: Float,
    val currentResources: Float,
    val regenerationRate: Float,
    val abilityHase: Float,
    val spellLevels: Map<String, Int>
)

data class LiveClientEvent(
    val eventId: Int,
    val eventName: String,
    val eventTime: Float?,
    val data: JsonObject
)
