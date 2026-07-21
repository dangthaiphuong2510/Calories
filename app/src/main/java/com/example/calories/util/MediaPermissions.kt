package com.example.calories.util

import android.Manifest
import android.os.Build

object MediaPermissions {
    const val CAMERA = Manifest.permission.CAMERA

    /**
     * Gallery picking via [androidx.activity.result.contract.ActivityResultContracts.GetContent]
     * does not require storage permission below API 33.
     */
    val galleryPermission: String?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            null
        }
}
