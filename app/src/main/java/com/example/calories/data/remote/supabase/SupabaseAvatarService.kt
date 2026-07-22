package com.example.calories.data.remote.supabase

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.storage.storage
import io.ktor.http.ContentType
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAvatarService @Inject constructor(
    private val supabase: SupabaseClient,
) {

    suspend fun uploadAvatar(userId: String, imageFile: File): String {
        val authUserId = supabase.auth.currentUserOrNull()?.id
        if (authUserId == null) {
            throw IllegalStateException("Cannot upload avatar: user is not authenticated")
        }
        if (authUserId != userId) {
            Log.w(TAG, "uploadAvatar userId mismatch: param=$userId auth=$authUserId")
        }
        check(imageFile.exists()) {
            "Avatar file does not exist: ${imageFile.absolutePath}"
        }

        val path = avatarPath(userId)
        val bytes = imageFile.readBytes()
        Log.d(TAG, "Uploading avatar bucket=$BUCKET_NAME path=$path bytes=${bytes.size}")

        val bucket = supabase.storage.from(BUCKET_NAME)
        try {
            bucket.upload(path, bytes) {
                upsert = true
                contentType = ContentType.Image.JPEG
            }
            Log.d(TAG, "Storage upload succeeded for path=$path")
        } catch (e: Exception) {
            Log.e(TAG, "Storage upload failed for path=$path", e)
            throw e
        }

        val publicUrl = bucket.publicUrl(path)
        Log.d(TAG, "Generated public URL: $publicUrl")
        check(publicUrl.startsWith("http://") || publicUrl.startsWith("https://")) {
            "Invalid public URL returned from storage: $publicUrl"
        }
        return publicUrl
    }

    suspend fun deleteAvatar(userId: String) {
        val path = avatarPath(userId)
        try {
            supabase.storage.from(BUCKET_NAME).delete(path)
            Log.d(TAG, "Deleted avatar from storage path=$path")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete avatar from storage path=$path", e)
            throw e
        }
    }

    private fun avatarPath(userId: String): String = "$userId.jpg"

    private companion object {
        const val TAG = "AvatarUpload"
        const val BUCKET_NAME = "avatars"
    }
}
