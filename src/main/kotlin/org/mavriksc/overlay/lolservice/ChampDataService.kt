package org.mavriksc.overlay.lolservice

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mavriksc.overlay.fileservice.getText
import org.mavriksc.overlay.fileservice.writeToFile


class ChampDataService {
    private val datFile = "data/cache.json"
    private val versionsUrl = "https://ddragon.leagueoflegends.com/api/versions.json"
    private val client = OkHttpClient()
    private var cache: Version? = loadCache()
    private val version = getCurrentVersion()

    private val baseURL = "https://ddragon.leagueoflegends.com/cdn/${version}/data/en_US/"
    private val championsUrl = baseURL + "champion.json"

    //fetches champion list and spell info and will provide spell costs for a champion
    init {
        if (!hasCurrentVersionInfo()) {
            val champs = updateChampionList()
            val champData = champs.map { champ -> getChampionData(champ) }
            cache = Version(version, champData)
            saveCache()
        }
    }

    private fun loadCache(): Version? {
        return datFile.getText()?.let { Json.decodeFromString(it) }
    }

    private fun saveCache() {
        datFile.writeToFile(Json.encodeToString(cache))
    }

    private fun getCurrentVersion(): String {
        val versionCall = Request.Builder().url(versionsUrl).build()
        client.newCall(versionCall).execute().use { response ->
            val body = response.body.string()
            val array: List<String> = Json.decodeFromString(body)
            return array.firstOrNull() ?: ""
        }
    }

    private fun hasCurrentVersionInfo(): Boolean {
        return cache?.let { it.version == version } ?: false
    }

    private fun updateChampionList(): List<String> {
        val champsCall = Request.Builder().url(championsUrl).build()
        client.newCall(champsCall).execute().use { response ->
            val body = response.body.string()
            val data = Json.parseToJsonElement(body).jsonObject["data"]!! as JsonObject
            return data.keys.toList()
        }
    }

    private fun getChampionData(champ: String): Champion {
        val champCall = Request.Builder().url(baseURL + "champion/$champ.json").build()
        client.newCall(champCall).execute().use { response ->
            val body = response.body.string()
            val abilities = getAbilityData(body, champ)
            return Champion(champ, abilities)
        }
    }

    private fun getAbilityData(body: String, champ: String): List<Ability> {
        val data = Json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject[champ]!! as JsonObject
        val spells = data["spells"]!! as JsonArray
        return spells.map { spell -> parseSpell(spell as JsonObject) }
    }

    private fun parseSpell(spell: JsonObject): Ability {
        val name = spell["name"]!!.jsonPrimitive.content
        val cooldown = spell["cooldown"]!!.jsonArray.map { it.jsonPrimitive.content.toFloat() }
        val cost = spell["cost"]!!.jsonArray.map { it.jsonPrimitive.content.toFloat() }
        val costBurn = spell["costBurn"]!!.jsonPrimitive.content.split("/").map { it.toFloat() }
        return Ability(name, cooldown, cost, costBurn)
    }

    fun getChampion(champ: String): Champion? {
        return cache?.champions?.find { it.name == champ }
    }

    fun getChampionList(): List<String> = cache?.champions?.map { it.name } ?: emptyList()
}

fun main() {
    ChampDataService()
}
