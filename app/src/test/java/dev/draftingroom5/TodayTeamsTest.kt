package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class TodayTeamsTest {
    private val response = """{
      "team":{"id":"8","logo":"https://a.espncdn.com/i/teamlogos/mlb/500/mil.png"},
      "events":[
        {"date":"2026-10-01T18:00Z","timeValid":true,"competitions":[{"status":{"type":{"state":"post"}},
          "competitors":[{"id":"8","homeAway":"home"},{"id":"25","team":{"shortDisplayName":"Padres"}}]}]},
        {"date":"2026-10-04T00:30Z","timeValid":true,"competitions":[{"status":{"type":{"state":"pre"}},
          "competitors":[{"id":"8","homeAway":"home"},{"id":"25","team":{"shortDisplayName":"Padres",
          "logos":[{"href":"https://a.espncdn.com/i/teamlogos/mlb/500/sd.png"}]}}]}]}
      ]
    }"""

    @Test fun choosesNextScheduledGameWithCorrectOpponentAndVenueSide() {
        val game = parseTodayNextGame(TodayTeam.BREWERS, response, Instant.parse("2026-10-02T00:00:00Z"))!!
        assertEquals("Padres", game.opponent)
        assertEquals(true, game.home)
        assertEquals(Instant.parse("2026-10-04T00:30:00Z"), game.startsAt)
        assertEquals("https://a.espncdn.com/i/teamlogos/mlb/500/sd.png", game.opponentLogo)
    }

    @Test fun returnsNoGameWhenAllEventsHavePassed() {
        assertNull(parseTodayNextGame(TodayTeam.BREWERS, response, Instant.parse("2026-10-05T00:00:00Z")))
    }

    @Test fun reorderMovesOnlyWithinBounds() {
        val teams = listOf(TodayTeam.BREWERS, TodayTeam.PACKERS, TodayTeam.INDIANA_FOOTBALL)
        assertEquals(listOf(TodayTeam.PACKERS, TodayTeam.BREWERS, TodayTeam.INDIANA_FOOTBALL),
            moveTodayItem(teams, 1, -1))
        assertEquals(teams, moveTodayItem(teams, 0, -1))
    }
}
