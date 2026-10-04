package dev.draftingroom5

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TodayWeatherTest {
    @Test fun mapsOpenMeteoWeatherCodesToDistinctScenes() {
        assertEquals(TodayWeatherKind.CLEAR, weatherKind(0))
        assertEquals(TodayWeatherKind.CLOUDY, weatherKind(48))
        assertEquals(TodayWeatherKind.RAIN, weatherKind(61))
        assertEquals(TodayWeatherKind.SNOW, weatherKind(75))
        assertEquals(TodayWeatherKind.STORM, weatherKind(95))
        assertEquals(TodayWeatherKind.UNKNOWN, weatherKind(999))
        assertEquals(R.drawable.today_rain_night, todaySceneResource(TodayWeatherKind.RAIN, TodayDaypart.NIGHT))
        assertEquals(R.drawable.today_night, todaySceneResource(TodayWeatherKind.CLEAR, TodayDaypart.NIGHT))
        assertEquals(R.drawable.today_cloudy_day, todaySceneResource(TodayWeatherKind.CLOUDY, TodayDaypart.DAY))
        assertEquals(R.drawable.today_cloudy_night, todaySceneResource(TodayWeatherKind.CLOUDY, TodayDaypart.NIGHT))
    }

    @Test fun daypartTransitionsCoverWholeDay() {
        assertEquals(TodayDaypart.NIGHT, daypartForHour(0))
        assertEquals(TodayDaypart.DAWN, daypartForHour(6))
        assertEquals(TodayDaypart.DAY, daypartForHour(12))
        assertEquals(TodayDaypart.DUSK, daypartForHour(18))
        assertEquals(TodayDaypart.NIGHT, daypartForHour(22))
    }

    @Test fun seasonChangesAtCalendarBoundaries() {
        assertEquals(TodaySeason.WINTER, seasonForDate(LocalDate.of(2026, 2, 28)))
        assertEquals(TodaySeason.SPRING, seasonForDate(LocalDate.of(2026, 3, 1)))
        assertEquals(TodaySeason.SUMMER, seasonForDate(LocalDate.of(2026, 6, 1)))
        assertEquals(TodaySeason.AUTUMN, seasonForDate(LocalDate.of(2026, 9, 1)))
        assertEquals(TodaySeason.WINTER, seasonForDate(LocalDate.of(2026, 12, 1)))
    }
}
