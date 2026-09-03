package com.traveler.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class RoomMigrationTest {

    private lateinit var db: SupportSQLiteDatabase
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create Schema Version 1
                    db.execSQL("""
                        CREATE TABLE `trips` (
                            `id` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `startDateIso` TEXT NOT NULL,
                            `endDateIso` TEXT NOT NULL,
                            `totalDistanceMeters` REAL NOT NULL,
                            `citiesJson` TEXT NOT NULL,
                            `countriesJson` TEXT NOT NULL,
                            `totalMediaCount` INTEGER NOT NULL,
                            `createdAtEpochMs` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE `visits` (
                            `id` TEXT NOT NULL,
                            `tripId` TEXT NOT NULL,
                            `placeName` TEXT,
                            `placeAddress` TEXT,
                            `placeId` TEXT,
                            `latitude` REAL NOT NULL,
                            `longitude` REAL NOT NULL,
                            `startTimestampEpochMs` INTEGER NOT NULL,
                            `endTimestampEpochMs` INTEGER NOT NULL,
                            `confidence` REAL NOT NULL,
                            `isUserOverride` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE `movement_segments` (
                            `id` TEXT NOT NULL,
                            `tripId` TEXT NOT NULL,
                            `startTimestampEpochMs` INTEGER NOT NULL,
                            `endTimestampEpochMs` INTEGER NOT NULL,
                            `startLat` REAL NOT NULL,
                            `startLng` REAL NOT NULL,
                            `endLat` REAL NOT NULL,
                            `endLng` REAL NOT NULL,
                            `distanceMeters` REAL NOT NULL,
                            `durationMillis` INTEGER NOT NULL,
                            `predictedTransportMode` TEXT NOT NULL,
                            `predictedConfidence` REAL NOT NULL,
                            `predictedReason` TEXT NOT NULL,
                            `userOverrideTransportMode` TEXT,
                            `polylineJson` TEXT NOT NULL,
                            `isUserOverride` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE `media_items` (
                            `id` TEXT NOT NULL,
                            `tripId` TEXT NOT NULL,
                            `contentUriString` TEXT NOT NULL,
                            `fileName` TEXT NOT NULL,
                            `mimeType` TEXT NOT NULL,
                            `timestampEpochMs` INTEGER,
                            `timestampConfidence` TEXT NOT NULL,
                            `latitude` REAL,
                            `longitude` REAL,
                            `locationConfidence` TEXT NOT NULL,
                            `confidenceScore` REAL NOT NULL,
                            `matchedVisitId` TEXT,
                            `matchedSegmentId` TEXT,
                            `isRepresentative` INTEGER NOT NULL,
                            `isUserLocationOverride` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE `user_overrides` (
                            `id` TEXT NOT NULL,
                            `targetType` TEXT NOT NULL,
                            `targetId` TEXT NOT NULL,
                            `overrideValue` TEXT NOT NULL,
                            `overriddenAtEpochMs` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        helper = FrameworkSQLiteOpenHelperFactory().create(config)
        db = helper.writableDatabase
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun testMigration1To2_UpgradesTablesAndPreservesData() {
        // Insert sample v1 data
        db.execSQL("INSERT INTO `visits` (`id`, `tripId`, `placeName`, `latitude`, `longitude`, `startTimestampEpochMs`, `endTimestampEpochMs`, `confidence`, `isUserOverride`) VALUES ('v1', 't1', 'Seoul Station', 37.55, 126.97, 1000, 2000, 0.9, 0)")
        db.execSQL("INSERT INTO `movement_segments` (`id`, `tripId`, `startTimestampEpochMs`, `endTimestampEpochMs`, `startLat`, `startLng`, `endLat`, `endLng`, `distanceMeters`, `durationMillis`, `predictedTransportMode`, `predictedConfidence`, `predictedReason`, `polylineJson`, `isUserOverride`) VALUES ('s1', 't1', 2000, 3000, 37.55, 126.97, 37.56, 126.98, 1000.0, 1000, 'WALK', 0.9, 'reason', '[]', 0)")
        db.execSQL("INSERT INTO `media_items` (`id`, `tripId`, `contentUriString`, `fileName`, `mimeType`, `timestampEpochMs`, `timestampConfidence`, `locationConfidence`, `confidenceScore`, `isRepresentative`, `isUserLocationOverride`) VALUES ('m1', 't1', 'uri://1', 'photo.jpg', 'image/jpeg', 1500, 'EXIF_LOCAL', 'UNKNOWN', 0.8, 0, 0)")
        db.execSQL("INSERT INTO `user_overrides` (`id`, `targetType`, `targetId`, `overrideValue`, `overriddenAtEpochMs`) VALUES ('o1', 'SEGMENT_TRANSPORT', 's1', 'BUS', 5000)")

        // Execute MIGRATION_1_2
        TravelerDatabase.MIGRATION_1_2.migrate(db)

        // Verify visits table
        db.query("SELECT `tripId`, `sourceId`, `placeName` FROM `visits` WHERE `tripId`='t1'").use { cursor ->
            assertTrue("Visits must have migrated row", cursor.moveToFirst())
            assertEquals("t1", cursor.getString(0))
            assertEquals("v1", cursor.getString(1))
            assertEquals("Seoul Station", cursor.getString(2))
        }

        // Verify movement_segments table
        db.query("SELECT `tripId`, `sourceId`, `predictedTransportMode` FROM `movement_segments` WHERE `tripId`='t1'").use { cursor ->
            assertTrue("Movement segments must have migrated row", cursor.moveToFirst())
            assertEquals("t1", cursor.getString(0))
            assertEquals("s1", cursor.getString(1))
            assertEquals("WALK", cursor.getString(2))
        }

        // Verify trip_media table
        db.query("SELECT `tripId`, `mediaKey`, `fileName` FROM `trip_media` WHERE `tripId`='t1'").use { cursor ->
            assertTrue("Trip media must have migrated row", cursor.moveToFirst())
            assertEquals("t1", cursor.getString(0))
            assertEquals("m1", cursor.getString(1))
            assertEquals("photo.jpg", cursor.getString(2))
        }

        // Verify user_overrides table has targetSourceId
        db.query("SELECT `id`, `targetType`, `targetSourceId`, `overrideValue` FROM `user_overrides` WHERE `id`='o1'").use { cursor ->
            assertTrue("User overrides must have migrated row", cursor.moveToFirst())
            assertEquals("o1", cursor.getString(0))
            assertEquals("SEGMENT_TRANSPORT", cursor.getString(1))
            assertEquals("s1", cursor.getString(2))
            assertEquals("BUS", cursor.getString(3))
        }
    }

    @Test
    fun testMigration3To4_AddsVisitAndMovementTimezoneColumns() {
        // Migrate to v2 then v3
        TravelerDatabase.MIGRATION_1_2.migrate(db)
        TravelerDatabase.MIGRATION_2_3.migrate(db)

        assertFalse("timezoneId should not exist before MIGRATION_3_4", TravelerDatabase.columnExists(db, "visits", "timezoneId"))
        assertFalse("startTimezoneId should not exist before MIGRATION_3_4", TravelerDatabase.columnExists(db, "movement_segments", "startTimezoneId"))
        assertFalse("endTimezoneId should not exist before MIGRATION_3_4", TravelerDatabase.columnExists(db, "movement_segments", "endTimezoneId"))

        // Execute MIGRATION_3_4
        TravelerDatabase.MIGRATION_3_4.migrate(db)

        assertTrue("timezoneId must exist in visits after MIGRATION_3_4", TravelerDatabase.columnExists(db, "visits", "timezoneId"))
        assertTrue("startTimezoneId must exist in movement_segments after MIGRATION_3_4", TravelerDatabase.columnExists(db, "movement_segments", "startTimezoneId"))
        assertTrue("endTimezoneId must exist in movement_segments after MIGRATION_3_4", TravelerDatabase.columnExists(db, "movement_segments", "endTimezoneId"))

        // Insert and verify query with new columns
        db.execSQL("INSERT INTO `visits` (`tripId`, `sourceId`, `placeName`, `latitude`, `longitude`, `startTimestampEpochMs`, `endTimestampEpochMs`, `confidence`, `isUserOverride`, `timezoneId`) VALUES ('t3', 'v3', 'Boston', 42.35, -71.08, 1000, 2000, 0.95, 0, 'America/New_York')")
        db.execSQL("INSERT INTO `movement_segments` (`tripId`, `sourceId`, `startTimestampEpochMs`, `endTimestampEpochMs`, `startLat`, `startLng`, `endLat`, `endLng`, `distanceMeters`, `durationMillis`, `predictedTransportMode`, `predictedConfidence`, `predictedReason`, `polylineJson`, `isUserOverride`, `startTimezoneId`, `endTimezoneId`) VALUES ('t3', 's3', 2000, 3000, 42.35, -71.08, 43.08, -79.08, 690000.0, 10000, 'AIRPLANE', 0.95, 'Flight', '[]', 0, 'America/New_York', 'America/Toronto')")

        db.query("SELECT `sourceId`, `timezoneId` FROM `visits` WHERE `sourceId`='v3'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("v3", cursor.getString(0))
            assertEquals("America/New_York", cursor.getString(1))
        }

        db.query("SELECT `sourceId`, `startTimezoneId`, `endTimezoneId` FROM `movement_segments` WHERE `sourceId`='s3'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s3", cursor.getString(0))
            assertEquals("America/New_York", cursor.getString(1))
            assertEquals("America/Toronto", cursor.getString(2))
        }
    }

    @Test
    fun testMigration4To5_AddsGeometryProvenanceAndDayAssignmentFields() {
        // Run migration chain 1 -> 2 -> 3 -> 4 first
        TravelerDatabase.MIGRATION_1_2.migrate(db)
        TravelerDatabase.MIGRATION_2_3.migrate(db)
        TravelerDatabase.MIGRATION_3_4.migrate(db)

        // Insert sample v4 data
        db.execSQL("INSERT INTO `movement_segments` (`tripId`, `sourceId`, `startTimestampEpochMs`, `endTimestampEpochMs`, `startLat`, `startLng`, `endLat`, `endLng`, `distanceMeters`, `durationMillis`, `predictedTransportMode`, `predictedConfidence`, `predictedReason`, `polylineJson`, `isUserOverride`, `startTimezoneId`, `endTimezoneId`) VALUES ('t4', 's4', 2000, 3000, 42.35, -71.08, 43.08, -79.08, 690000.0, 10000, 'AIRPLANE', 0.95, 'Flight', '[]', 0, 'America/New_York', 'America/Toronto')")
        db.execSQL("INSERT INTO `trip_media` (`tripId`, `mediaKey`, `contentUriString`, `fileName`, `mimeType`, `timestampEpochMs`, `timestampConfidence`, `captureTimezoneId`, `locationConfidence`, `confidenceScore`, `isRepresentative`, `isUserLocationOverride`) VALUES ('t4', 'm4', 'uri://media4', 'pic4.jpg', 'image/jpeg', 2500, 'EXIF_EXACT', 'America/New_York', 'GPS_EXACT', 0.99, 1, 0)")

        // Run MIGRATION_4_5
        TravelerDatabase.MIGRATION_4_5.migrate(db)

        // Verify new columns exist
        assertTrue("geometryProvenance must exist in movement_segments", TravelerDatabase.columnExists(db, "movement_segments", "geometryProvenance"))
        assertTrue("assignedDayIso must exist in trip_media", TravelerDatabase.columnExists(db, "trip_media", "assignedDayIso"))
        assertTrue("dayAssignmentConfidence must exist in trip_media", TravelerDatabase.columnExists(db, "trip_media", "dayAssignmentConfidence"))
        assertTrue("dayAssignmentProvenance must exist in trip_media", TravelerDatabase.columnExists(db, "trip_media", "dayAssignmentProvenance"))

        // Verify existing rows have defaults
        db.query("SELECT `sourceId`, `geometryProvenance` FROM `movement_segments` WHERE `sourceId`='s4'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s4", cursor.getString(0))
            assertEquals("UNKNOWN", cursor.getString(1))
        }

        db.query("SELECT `mediaKey`, `assignedDayIso`, `dayAssignmentConfidence` FROM `trip_media` WHERE `mediaKey`='m4'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("m4", cursor.getString(0))
            assertNull(cursor.getString(1))
            assertEquals("UNKNOWN", cursor.getString(2))
        }
    }

    @Test
    fun testMigration1To5_FullChainPreservesAllFields() {
        // Insert sample v1 data
        db.execSQL("INSERT INTO `visits` (`id`, `tripId`, `placeName`, `latitude`, `longitude`, `startTimestampEpochMs`, `endTimestampEpochMs`, `confidence`, `isUserOverride`) VALUES ('v_all', 't_all', 'Tokyo Station', 35.68, 139.76, 1000, 2000, 0.9, 0)")
        db.execSQL("INSERT INTO `movement_segments` (`id`, `tripId`, `startTimestampEpochMs`, `endTimestampEpochMs`, `startLat`, `startLng`, `endLat`, `endLng`, `distanceMeters`, `durationMillis`, `predictedTransportMode`, `predictedConfidence`, `predictedReason`, `polylineJson`, `isUserOverride`) VALUES ('s_all', 't_all', 2000, 3000, 35.68, 139.76, 37.77, -122.41, 8000000.0, 36000000, 'AIRPLANE', 0.98, 'Flight', '[]', 0)")
        db.execSQL("INSERT INTO `media_items` (`id`, `tripId`, `contentUriString`, `fileName`, `mimeType`, `timestampEpochMs`, `timestampConfidence`, `locationConfidence`, `confidenceScore`, `isRepresentative`, `isUserLocationOverride`) VALUES ('m_all', 't_all', 'uri://tokyo', 'fuji.jpg', 'image/jpeg', 1500, 'EXIF_EXACT', 'GPS_EXACT', 0.99, 1, 0)")

        // Run entire migration chain: 1 -> 2 -> 3 -> 4 -> 5
        TravelerDatabase.MIGRATION_1_2.migrate(db)
        TravelerDatabase.MIGRATION_2_3.migrate(db)
        TravelerDatabase.MIGRATION_3_4.migrate(db)
        TravelerDatabase.MIGRATION_4_5.migrate(db)

        // Verify all tables exist and have new schema
        db.query("SELECT `tripId`, `sourceId`, `placeName` FROM `visits` WHERE `tripId`='t_all'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("t_all", cursor.getString(0))
            assertEquals("v_all", cursor.getString(1))
            assertEquals("Tokyo Station", cursor.getString(2))
        }

        db.query("SELECT `tripId`, `sourceId`, `predictedTransportMode`, `geometryProvenance` FROM `movement_segments` WHERE `tripId`='t_all'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("t_all", cursor.getString(0))
            assertEquals("s_all", cursor.getString(1))
            assertEquals("AIRPLANE", cursor.getString(2))
            assertEquals("UNKNOWN", cursor.getString(3))
        }

        db.query("SELECT `tripId`, `mediaKey`, `fileName`, `dayAssignmentConfidence` FROM `trip_media` WHERE `tripId`='t_all'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("t_all", cursor.getString(0))
            assertEquals("m_all", cursor.getString(1))
            assertEquals("fuji.jpg", cursor.getString(2))
            assertEquals("UNKNOWN", cursor.getString(3))
        }
    }
}
