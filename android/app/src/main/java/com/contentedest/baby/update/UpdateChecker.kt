package com.contentedest.baby.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import com.contentedest.baby.BuildConfig
import com.contentedest.baby.net.ApiService
import com.contentedest.baby.net.UpdateInfoResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    private val apiService: ApiService,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "UpdateChecker"
        private const val APK_FILE_NAME = "update.apk"
    }

    /**
     * Checks if an update is available by comparing current version with server version.
     * Returns UpdateInfoResponse if update is available, null otherwise.
     */
    suspend fun checkForUpdate(): UpdateInfoResponse? {
        return try {
            val updateInfo = apiService.getUpdateInfo()
            val currentVersionCode = BuildConfig.VERSION_CODE
            
            Log.d(TAG, "Current version: $currentVersionCode, Server version: ${updateInfo.versionCode}")
            
            if (updateInfo.versionCode > currentVersionCode) {
                Log.d(TAG, "Update available: ${updateInfo.versionName}")
                updateInfo
            } else {
                Log.d(TAG, "App is up to date")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for update", e)
            null
        }
    }

    /**
     * Downloads the APK from the given URL and saves it to the app's cache directory.
     * Returns the File if successful, null otherwise.
     */
    suspend fun downloadApk(context: Context, downloadUrl: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(downloadUrl)
                    .build()

                val response = okHttpClient.newCall(request).execute()
                
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed to download APK: ${response.code}")
                    return@withContext null
                }

                val body = response.body ?: run {
                    Log.e(TAG, "Response body is null")
                    return@withContext null
                }

                // Save to cache directory
                val cacheDir = context.cacheDir
                val apkFile = File(cacheDir, APK_FILE_NAME)
                
                // Delete existing APK if present
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                // Write downloaded content to file
                body.byteStream().use { inputStream ->
                    FileOutputStream(apkFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                Log.d(TAG, "APK downloaded successfully: ${apkFile.absolutePath}, size: ${apkFile.length()}")
                apkFile
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download APK", e)
                null
            }
        }
    }

    /**
     * Installs the APK file using PackageInstaller (Android 5.0+) or Intent (older versions).
     * Returns true if installation was initiated successfully.
     */
    fun installApk(context: Context, apkFile: File): Boolean {
        return try {
            // Verify file exists and is readable
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file does not exist: ${apkFile.absolutePath}")
                return false
            }
            
            if (!apkFile.canRead()) {
                Log.e(TAG, "APK file is not readable: ${apkFile.absolutePath}")
                return false
            }
            
            Log.d(TAG, "Preparing to install APK: ${apkFile.absolutePath}, size: ${apkFile.length()}")
            
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Use FileProvider for Android 7.0+
                try {
                    val fileUri = FileProvider.getUriForFile(
                        context,
                        "${BuildConfig.APPLICATION_ID}.fileprovider",
                        apkFile
                    )
                    Log.d(TAG, "FileProvider URI created: $fileUri")
                    fileUri
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to create FileProvider URI", e)
                    return false
                }
            } else {
                // Use file:// URI for older versions
                Uri.fromFile(apkFile)
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }

            // Check if intent can be resolved
            val packageManager = context.packageManager
            if (intent.resolveActivity(packageManager) == null) {
                Log.e(TAG, "No activity found to handle installation intent")
                Log.e(TAG, "URI: $uri, MIME type: application/vnd.android.package-archive")
                return false
            }

            Log.d(TAG, "Starting installation intent with URI: $uri")
            context.startActivity(intent)
            Log.d(TAG, "Installation intent started successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install APK", e)
            Log.e(TAG, "Exception details: ${e.message}", e)
            false
        }
    }

    /**
     * Complete update flow: check, download, and install.
     * Returns UpdateResult indicating success or failure.
     */
    suspend fun performUpdate(context: Context): UpdateResult {
        val updateInfo = checkForUpdate() ?: return UpdateResult.NoUpdateAvailable
        
        val apkFile = downloadApk(context, updateInfo.downloadUrl)
            ?: return UpdateResult.DownloadFailed
        
        val installStarted = installApk(context, apkFile)
        return if (installStarted) {
            UpdateResult.InstallStarted(updateInfo)
        } else {
            UpdateResult.InstallFailed
        }
    }
}

sealed class UpdateResult {
    object NoUpdateAvailable : UpdateResult()
    object DownloadFailed : UpdateResult()
    object InstallFailed : UpdateResult()
    data class InstallStarted(val updateInfo: UpdateInfoResponse) : UpdateResult()
}

