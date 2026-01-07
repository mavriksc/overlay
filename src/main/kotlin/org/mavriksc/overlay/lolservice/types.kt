package org.mavriksc.overlay.lolservice

import kotlinx.serialization.Serializable

@Serializable
data class Ability(val name: String, val cooldowns: List<Float>, val cost: List<Float>?, val costBurn: List<Float>?)

@Serializable
data class Champion(val name: String, val abilities: List<Ability>)

@Serializable
data class Version(val version: String, val champions: List<Champion>)
