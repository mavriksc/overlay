package org.mavriksc.overlay.lolservice

import kotlinx.serialization.Serializable

@Serializable
data class Ability(val name: String, val cooldowns: List<Float>, val cost: List<Float>?, val costBurn: List<Float>?)

@Serializable
data class Champion(val name: String, val abilities: List<Ability>)

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