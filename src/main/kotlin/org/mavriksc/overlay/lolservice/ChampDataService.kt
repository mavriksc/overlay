package org.mavriksc.overlay.lolservice

import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import org.mavriksc.overlay.getTextFromFile
import org.mavriksc.overlay.toRequest
import org.mavriksc.overlay.writeToFile
import org.mavriksc.overlay.AppLogging
import java.util.logging.Logger


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
        return datFile.getTextFromFile()?.let { Json.decodeFromString(it) }
    }

    private fun saveCache() {
        datFile.writeToFile(Json.encodeToString(cache))
    }

    private fun getCurrentVersion(): String {
        val versionCall = versionsUrl.toRequest()
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
        val champsCall = championsUrl.toRequest()
        client.newCall(champsCall).execute().use { response ->
            val body = response.body.string()
            val data = Json.parseToJsonElement(body).jsonObject["data"]!! as JsonObject
            return data.keys.toList()
        }
    }

    private fun getChampionData(champ: String): Champion {
        val champCall = "${baseURL}champion/$champ.json".toRequest()
        client.newCall(champCall).execute().use { response ->
            val body = response.body.string()
            val data = Json.parseToJsonElement(body).jsonObject["data"]!!.jsonObject[champ]!! as JsonObject
            val abilities = getAbilityData(data)
            val stats = getStats(data)
            return Champion(champ, stats, abilities)
        }
    }

    private fun getStats(data: JsonObject): Stats {
        val stats = data["stats"]!! as JsonObject
        val mp = stats["mp"]!!.jsonPrimitive.float
        val mpPerLevel = stats["mpperlevel"]!!.jsonPrimitive.float
        val mpRegen = stats["mpregen"]!!.jsonPrimitive.float
        val mpRegenPerLevel = stats["mpregenperlevel"]!!.jsonPrimitive.float
        return Stats(mp, mpPerLevel, mpRegen, mpRegenPerLevel)
    }

    private fun getAbilityData(data: JsonObject): List<Ability> {
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
    AppLogging.initialize()
    val cdService = ChampDataService()
    val randChamp = cdService.getChampionList().random()
    Logger.getLogger(ChampDataService::class.java.name)
        .info("Champion data for $randChamp: ${cdService.getChampion(randChamp)}")
}
