package com.traveler.feature.map.renderer

import com.traveler.core.common.geo.GeoPoint
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class MapLandWaterClassificationTest {

    private fun pointInPolygon(point: GeoPoint, ring: List<GeoPoint>): Boolean {
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val xi = ring[i].longitude
            val yi = ring[i].latitude
            val xj = ring[j].longitude
            val yj = ring[j].latitude

            val intersect = ((yi > point.latitude) != (yj > point.latitude)) &&
                    (point.longitude < (xj - xi) * (point.latitude - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private data class BasemapPoly(
        val name: String,
        val isLake: Boolean,
        val rings: List<List<GeoPoint>>
    )

    private fun loadBasemapPolygons(preferRegional: Boolean = true): List<BasemapPoly> {
        val filename = if (preferRegional) "basemap_regional.json" else "basemap_world.json"
        val path = File("src/main/assets/$filename")
        val altPath = File("app/src/main/assets/$filename")
        val file = if (path.exists()) path else if (altPath.exists()) altPath else {
            val fallbackPath = File("app/src/main/assets/basemap_world.json")
            if (fallbackPath.exists()) fallbackPath else File("src/main/assets/basemap_world.json")
        }
        assertTrue("Basemap asset must exist", file.exists())

        val jsonStr = file.readText()
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(jsonStr).jsonObject
        val polysArray = root["polygons"]?.jsonArray ?: JsonArray(emptyList())

        return polysArray.map { elem ->
            val obj = elem.jsonObject
            val name = obj["name"]?.jsonPrimitive?.content ?: "Polygon"
            val type = obj["type"]?.jsonPrimitive?.content ?: "land"
            val isLake = type.equals("lake", ignoreCase = true)
            val rawRings = obj["rings"]?.jsonArray ?: JsonArray(emptyList())
            val rings = rawRings.map { r ->
                r.jsonArray.map { pt ->
                    val coord = pt.jsonArray
                    val lng = coord[0].jsonPrimitive.double
                    val lat = coord[1].jsonPrimitive.double
                    GeoPoint(lat, lng)
                }
            }
            BasemapPoly(name, isLake, rings)
        }
    }

    private fun isClassifiedAsWater(point: GeoPoint, polygons: List<BasemapPoly>): Boolean {
        // Background is ocean water.
        // If inside a land polygon -> LAND (unless inside an inner lake polygon on top of land).
        // If inside a lake/channel polygon -> WATER.
        var inLand = false
        var inLake = false

        for (poly in polygons) {
            val inPoly = poly.rings.any { pointInPolygon(point, it) }
            if (inPoly) {
                if (poly.isLake) {
                    inLake = true
                } else {
                    inLand = true
                }
            }
        }

        if (inLake) return true
        if (inLand) return false
        return true // Ocean background
    }

    @Test
    fun testLandmarkLandWaterClassifications_P1_11() {
        val polygons = loadBasemapPolygons(preferRegional = true)

        // 1. Mandatory LAND landmarks
        val landPoints = mapOf(
            "Times Square" to GeoPoint(40.7580, -73.9855),
            "Wall Street" to GeoPoint(40.7074, -74.0090),
            "Lower Manhattan" to GeoPoint(40.7128, -74.0060),
            "Jersey City" to GeoPoint(40.7178, -74.0431),
            "Hoboken" to GeoPoint(40.7439, -74.0323),
            "Boston Common" to GeoPoint(42.3550, -71.0656)
        )

        for ((name, pt) in landPoints) {
            val isWater = isClassifiedAsWater(pt, polygons)
            assertFalse("Landmark '$name' ($pt) must be classified as LAND, but was WATER", isWater)
        }

        // 2. Mandatory WATER channels & bodies
        val waterPoints = mapOf(
            "Mid-Hudson Channel" to GeoPoint(40.7300, -74.0180),
            "Upper NY Bay Channel" to GeoPoint(40.6800, -74.0300),
            "Long Island Sound" to GeoPoint(41.0000, -73.0000),
            "Lake Erie" to GeoPoint(42.2000, -81.0000),
            "Lake Ontario" to GeoPoint(43.5000, -77.5000)
        )

        for ((name, pt) in waterPoints) {
            val isWater = isClassifiedAsWater(pt, polygons)
            assertTrue("Water body '$name' ($pt) must be classified as WATER, but was LAND", isWater)
        }
    }
}
