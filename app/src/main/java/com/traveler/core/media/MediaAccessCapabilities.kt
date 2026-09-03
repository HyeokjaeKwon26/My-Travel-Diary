package com.traveler.core.media

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Detailed capability model for device media and location metadata access (P1-06).
 */
data class MediaAccessCapabilities(
    val images: AccessLevel,
    val videos: AccessLevel,
    val locationMetadata: LocationMetadataAccess
) {
    enum class AccessLevel {
        FULL,
        PARTIAL,
        DENIED
    }

    enum class LocationMetadataAccess {
        AVAILABLE,
        UNAVAILABLE
    }

    val isFullWithLocation: Boolean
        get() = images == AccessLevel.FULL && videos == AccessLevel.FULL && locationMetadata == LocationMetadataAccess.AVAILABLE

    val isQualifiedFullWithoutLocation: Boolean
        get() = images == AccessLevel.FULL && videos == AccessLevel.FULL && locationMetadata == LocationMetadataAccess.UNAVAILABLE

    val isPhotosOnly: Boolean
        get() = images == AccessLevel.FULL && videos != AccessLevel.FULL

    val isPartial: Boolean
        get() = images == AccessLevel.PARTIAL || (images != AccessLevel.DENIED && videos == AccessLevel.PARTIAL)

    val isDenied: Boolean
        get() = images == AccessLevel.DENIED && videos == AccessLevel.DENIED

    companion object {
        fun checkCapabilities(context: Context): MediaAccessCapabilities {
            val locationAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (granted) LocationMetadataAccess.AVAILABLE else LocationMetadataAccess.UNAVAILABLE
            } else {
                LocationMetadataAccess.AVAILABLE
            }

            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                    val imgFull = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    val vidFull = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
                    val userSelected = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED

                    val imgLevel = when {
                        imgFull -> AccessLevel.FULL
                        userSelected -> AccessLevel.PARTIAL
                        else -> AccessLevel.DENIED
                    }
                    val vidLevel = when {
                        vidFull -> AccessLevel.FULL
                        userSelected -> AccessLevel.PARTIAL
                        else -> AccessLevel.DENIED
                    }

                    MediaAccessCapabilities(imgLevel, vidLevel, locationAccess)
                }

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    val imgGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
                    val vidGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED

                    MediaAccessCapabilities(
                        images = if (imgGranted) AccessLevel.FULL else AccessLevel.DENIED,
                        videos = if (vidGranted) AccessLevel.FULL else AccessLevel.DENIED,
                        locationMetadata = locationAccess
                    )
                }

                else -> {
                    val storageGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    val level = if (storageGranted) AccessLevel.FULL else AccessLevel.DENIED
                    MediaAccessCapabilities(level, level, locationAccess)
                }
            }
        }
    }
}
