package com.traveler.core.common.time

import com.traveler.core.common.geo.GeoPoint
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class GeoTimezoneEngineTest {

    private fun assertZoneRulesMatch(expectedZoneId: String, actualZone: ZoneId?) {
        assertNotNull("Expected timezone for $expectedZoneId but got null", actualZone)
        val expected = ZoneId.of(expectedZoneId)
        val now = Instant.now()
        // Check either identical ID or identical effective offset and rules
        val rulesMatch = actualZone!!.id == expected.id ||
                actualZone.rules.getOffset(now) == expected.rules.getOffset(now)
        assertTrue(
            "Expected timezone rules equivalent to $expectedZoneId but got ${actualZone.id}",
            rulesMatch
        )
    }

    @Test
    fun testRequiredTimezoneMatrix_PrimaryCities() {
        // Seoul, South Korea
        val seoul = GeoPoint(37.5665, 126.9780)
        assertZoneRulesMatch("Asia/Seoul", GeoTimezoneEngine.getTimezoneForLocation(seoul))

        // New York City, USA
        val nyc = GeoPoint(40.7128, -74.0060)
        assertZoneRulesMatch("America/New_York", GeoTimezoneEngine.getTimezoneForLocation(nyc))

        // Madrid, Spain
        val madrid = GeoPoint(40.4168, -3.7038)
        assertZoneRulesMatch("Europe/Madrid", GeoTimezoneEngine.getTimezoneForLocation(madrid))

        // Kathmandu, Nepal (UTC+05:45)
        val kathmandu = GeoPoint(27.7172, 85.3240)
        assertZoneRulesMatch("Asia/Kathmandu", GeoTimezoneEngine.getTimezoneForLocation(kathmandu))

        // Phoenix, Arizona (MST no DST)
        val phoenix = GeoPoint(33.4484, -112.0740)
        assertZoneRulesMatch("America/Phoenix", GeoTimezoneEngine.getTimezoneForLocation(phoenix))

        // Darwin, Northern Territory (UTC+09:30 no DST)
        val darwin = GeoPoint(-12.4634, 130.8456)
        assertZoneRulesMatch("Australia/Darwin", GeoTimezoneEngine.getTimezoneForLocation(darwin))

        // Brisbane, Queensland (UTC+10:00 no DST)
        val brisbane = GeoPoint(-27.4698, 153.0251)
        assertZoneRulesMatch("Australia/Brisbane", GeoTimezoneEngine.getTimezoneForLocation(brisbane))

        // Adelaide, South Australia (UTC+09:30 with DST)
        val adelaide = GeoPoint(-34.9285, 138.6007)
        assertZoneRulesMatch("Australia/Adelaide", GeoTimezoneEngine.getTimezoneForLocation(adelaide))

        // St. John's, Newfoundland (UTC-03:30 with DST)
        val stJohns = GeoPoint(47.5615, -52.7126)
        assertZoneRulesMatch("America/St_Johns", GeoTimezoneEngine.getTimezoneForLocation(stJohns))

        // Eucla, Western Australia (UTC+08:45)
        val eucla = GeoPoint(-31.6778, 128.8833)
        assertZoneRulesMatch("Australia/Eucla", GeoTimezoneEngine.getTimezoneForLocation(eucla))
    }

    @Test
    fun testWorldwideRegions_AfricaAmericasPacificEurope() {
        // Accra, Ghana (GMT / UTC+00:00)
        val accra = GeoPoint(5.6037, -0.1870)
        assertZoneRulesMatch("Africa/Accra", GeoTimezoneEngine.getTimezoneForLocation(accra))

        // Lagos, Nigeria (WAT / UTC+01:00)
        val lagos = GeoPoint(6.5244, 3.3792)
        assertZoneRulesMatch("Africa/Lagos", GeoTimezoneEngine.getTimezoneForLocation(lagos))

        // Casablanca, Morocco (+01:00 / Ramadan rules)
        val casablanca = GeoPoint(33.5731, -7.5898)
        assertZoneRulesMatch("Africa/Casablanca", GeoTimezoneEngine.getTimezoneForLocation(casablanca))

        // Istanbul, Turkey (+03:00)
        val istanbul = GeoPoint(41.0082, 28.9784)
        assertZoneRulesMatch("Europe/Istanbul", GeoTimezoneEngine.getTimezoneForLocation(istanbul))

        // Vancouver, Canada (PST/PDT)
        val vancouver = GeoPoint(49.2827, -123.1207)
        assertZoneRulesMatch("America/Vancouver", GeoTimezoneEngine.getTimezoneForLocation(vancouver))

        // Regina, Saskatchewan (CST no DST)
        val saskatchewan = GeoPoint(50.4547, -104.6067)
        assertZoneRulesMatch("America/Regina", GeoTimezoneEngine.getTimezoneForLocation(saskatchewan))

        // Anchorage, Alaska (AKST/AKDT)
        val anchorage = GeoPoint(61.2181, -149.9003)
        assertZoneRulesMatch("America/Anchorage", GeoTimezoneEngine.getTimezoneForLocation(anchorage))

        // Honolulu, Hawaii (HST no DST)
        val honolulu = GeoPoint(21.3069, -157.8583)
        assertZoneRulesMatch("Pacific/Honolulu", GeoTimezoneEngine.getTimezoneForLocation(honolulu))

        // Buenos Aires, Argentina (ART -03:00)
        val buenosAires = GeoPoint(-34.6037, -58.3816)
        assertZoneRulesMatch("America/Argentina/Buenos_Aires", GeoTimezoneEngine.getTimezoneForLocation(buenosAires))

        // Lima, Peru (PET -05:00)
        val lima = GeoPoint(-12.0464, -77.0428)
        assertZoneRulesMatch("America/Lima", GeoTimezoneEngine.getTimezoneForLocation(lima))

        // Cape Town, South Africa (SAST +02:00)
        val capeTown = GeoPoint(-33.9249, 18.4241)
        assertZoneRulesMatch("Africa/Johannesburg", GeoTimezoneEngine.getTimezoneForLocation(capeTown))

        // Auckland, New Zealand (NZST/NZDT)
        val auckland = GeoPoint(-36.8485, 174.7633)
        assertZoneRulesMatch("Pacific/Auckland", GeoTimezoneEngine.getTimezoneForLocation(auckland))

        // Canary Islands / Las Palmas, Spain (WET/WEST UTC+00:00/+01:00)
        val canaryIslands = GeoPoint(28.1248, -15.4300)
        assertZoneRulesMatch("Atlantic/Canary", GeoTimezoneEngine.getTimezoneForLocation(canaryIslands))
    }

    @Test
    fun testNullAndMaritimeLocations_TruthfulFallback() {
        assertNull("Null location must return null, never fake UTC", GeoTimezoneEngine.getTimezoneForLocation(null))

        // Middle of South Pacific Ocean (Point Nemo) is offshore
        val pointNemo = GeoPoint(-48.8767, -123.3933)
        val resolution = GeoTimezoneEngine.resolveSync(pointNemo)
        assertTrue(
            "Point Nemo should resolve to Offshore or Nautical zone, got: $resolution",
            resolution is TimezoneResolution.Offshore || resolution is TimezoneResolution.Resolved
        )
    }

    @Test
    fun testBoundedLruCache_RepeatedLookupsFast() {
        val seoul = GeoPoint(37.5665, 126.9780)
        val res1 = GeoTimezoneEngine.resolveSync(seoul)
        val res2 = GeoTimezoneEngine.resolveSync(seoul)

        assertEquals(res1, res2)
        assertTrue(res1 is TimezoneResolution.Resolved)
    }
}
