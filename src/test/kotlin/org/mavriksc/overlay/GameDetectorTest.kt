package org.mavriksc.overlay

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GameDetectorTest {
    private val detector = GameDetector()

    @Test
    fun `parse game snapshot marks playable when active player object is present`() {
        val payload = Json.parseToJsonElement(
            """
            {
              "activePlayer": {
                "abilities": {},
                "championStats": {}
              },
              "events": {
                "Events": [
                  {
                    "EventID": 1,
                    "EventName": "GameStart",
                    "EventTime": 12.5
                  }
                ]
              }
            }
            """.trimIndent()
        ).jsonObject

        val snapshot = detector.parseGameSnapshot(payload)

        assertEquals(GameSessionKind.PLAYABLE, snapshot.sessionKind)
        assertNull(snapshot.activePlayerError)
        assertEquals(1, snapshot.events.size)
        assertEquals("GameStart", snapshot.events.single().eventName)
    }

    @Test
    fun `parse game snapshot marks spectator when active player contains error`() {
        val payload = Json.parseToJsonElement(
            """
            {
              "activePlayer": {
                "error": "Spectator mode doesn't currently support this feature"
              },
              "events": {
                "Events": []
              }
            }
            """.trimIndent()
        ).jsonObject

        val snapshot = detector.parseGameSnapshot(payload)

        assertEquals(GameSessionKind.SPECTATOR, snapshot.sessionKind)
        assertEquals("Spectator mode doesn't currently support this feature", snapshot.activePlayerError)
        assertEquals(0, snapshot.events.size)
    }

    @Test
    fun `parse game snapshot marks unknown when active player is missing`() {
        val payload = Json.parseToJsonElement(
            """
            {
              "events": {
                "Events": []
              }
            }
            """.trimIndent()
        ).jsonObject

        val snapshot = detector.parseGameSnapshot(payload)

        assertEquals(GameSessionKind.UNKNOWN, snapshot.sessionKind)
        assertNull(snapshot.activePlayerError)
    }
}
