package com.example.data.network

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.jivesoftware.smack.XMPPConnection
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager
import java.io.File
import java.io.FileOutputStream

class HttpFileUploadManager(private val context: Context) {

    private val client = OkHttpClient()

    suspend fun uploadFile(
        connection: XMPPConnection,
        uri: Uri,
        fileName: String,
        fileSize: Long,
        mimeType: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val manager = HttpFileUploadManager.getInstanceFor(connection)
            if (!manager.isUploadServiceDiscovered) {
                manager.discoverUploadService()
            }

            if (!manager.isUploadServiceDiscovered) {
                return@withContext Result.failure(Exception("HTTP File Upload service not discovered on server"))
            }

            // Request upload slot
            val slot = manager.requestSlot(fileName, fileSize, mimeType)
            val putUrl = slot.putUrl.toString()
            val getUrl = slot.getUrl.toString()

            // Copy URI content to a temporary file for OkHttp
            val tempFile = File(context.cacheDir, "upload_tmp_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Upload via HTTP PUT
            val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
            val request = Request.Builder()
                .url(putUrl)
                .put(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                tempFile.delete()
                if (response.isSuccessful) {
                    Result.success(getUrl)
                } else {
                    Result.failure(Exception("Upload failed with code ${response.code}: ${response.message}"))
                }
            }
        } catch (e: Exception) {
            Log.e("HttpFileUpload", "Error uploading file", e)
            Result.failure(e)
        }
    }
}
