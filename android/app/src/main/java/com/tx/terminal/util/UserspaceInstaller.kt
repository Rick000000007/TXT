package com.tx.terminal.util

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Handles installation of userspace binaries and libraries from assets
 */
class UserspaceInstaller(private val context: Context) {

    companion object {
        private const val TAG = "UserspaceInstaller"
        private const val ASSETS_USR_PATH = "usr"
        private const val BUFFER_SIZE = 8192
    }

    /**
     * Install userspace from assets to app files directory
     */
    suspend fun installUserspace() = withContext(Dispatchers.IO) {
        val userspaceDir = File(context.filesDir, "usr")
        val binDir = File(userspaceDir, "bin")
        val libDir = File(userspaceDir, "lib")
        val homeDir = File(userspaceDir, "home")
        val tmpDir = File(userspaceDir, "tmp")

        // Create directory structure
        userspaceDir.mkdirs()
        binDir.mkdirs()
        libDir.mkdirs()
        homeDir.mkdirs()
        tmpDir.mkdirs()

        Log.d(TAG, "Created userspace directories")

        // Copy binaries from assets
        copyAssetsDirectory("$ASSETS_USR_PATH/bin", binDir)

        // Copy libraries from assets
        copyAssetsDirectory("$ASSETS_USR_PATH/lib", libDir)

        // Set executable permissions on binaries
        setExecutablePermissions(binDir)

        // Set library permissions
        setLibraryPermissions(libDir)

        // NEW: Ensure path priority for command override
        ensurePathPriority(userspaceDir)

        Log.d(TAG, "Userspace installation complete")
    }

    /**
     * NEW: Ensure userspace bin directory exists for command priority
     */
    fun ensurePathPriority(baseDir: File) {
        val binDir = File(baseDir, "usr/bin")
        if (!binDir.exists()) {
            Log.e("TX_ENV", "usr/bin missing")
        } else {
            Log.d("TX_ENV", "Path priority verified: ${binDir.absolutePath}")
        }
    }

    /**
     * Copy all files from an assets directory to a target directory
     */
    private fun copyAssetsDirectory(assetsPath: String, targetDir: File) {
        try {
            val assetManager = context.assets
            val files = assetManager.list(assetsPath)

            if (files.isNullOrEmpty()) {
                Log.w(TAG, "No files found in assets: $assetsPath")
                return
            }

            for (fileName in files) {
                val assetPath = "$assetsPath/$fileName"
                val targetFile = File(targetDir, fileName)

                // Check if it's a directory
                val subFiles = assetManager.list(assetPath)
                if (!subFiles.isNullOrEmpty()) {
                    // It's a directory, recurse
                    targetFile.mkdirs()
                    copyAssetsDirectory(assetPath, targetFile)
                } else {
                    // It's a file, copy it
                    copyAssetFile(assetPath, targetFile)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error copying assets from $assetsPath", e)
        }
    }

    /**
     * Copy a single asset file to target location
     */
    private fun copyAssetFile(assetPath: String, targetFile: File) {
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output, BUFFER_SIZE)
                }
            }
            Log.d(TAG, "Copied: $assetPath -> ${targetFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset: $assetPath", e)
        }
    }

    /**
     * Set executable permissions (755) on all files in directory
     */
    private fun setExecutablePermissions(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.setExecutable(true, false)
                file.setReadable(true, false)
                file.setWritable(true, true)
                Log.d(TAG, "Set executable permissions on: ${file.name}")
            } else if (file.isDirectory) {
                setExecutablePermissions(file)
            }
        }
    }

    /**
     * Set library permissions (644) on all files in directory
     */
    private fun setLibraryPermissions(dir: File) {
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                file.setReadable(true, false)
                file.setWritable(true, true)
                file.setExecutable(false, false)
                Log.d(TAG, "Set library permissions on: ${file.name}")
            } else if (file.isDirectory) {
                setLibraryPermissions(file)
            }
        }
    }

    /**
     * Extract a zip file from assets
     */
    private suspend fun extractZipAsset(assetPath: String, targetDir: File) = withContext(Dispatchers.IO) {
        try {
            context.assets.open(assetPath).use { input ->
                ZipInputStream(input).use { zipInput ->
                    var entry = zipInput.nextEntry
                    while (entry != null) {
                        val targetFile = File(targetDir, entry.name)

                        if (entry.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            FileOutputStream(targetFile).use { output ->
                                zipInput.copyTo(output, BUFFER_SIZE)
                            }
                        }

                        entry = zipInput.nextEntry
                    }
                }
            }
            Log.d(TAG, "Extracted zip: $assetPath")
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting zip: $assetPath", e)
        }
    }

    /**
     * Check if userspace is installed
     */
    fun isUserspaceInstalled(): Boolean {
        val userspaceDir = File(context.filesDir, "usr")
        val binDir = File(userspaceDir, "bin")
        val shFile = File(binDir, "sh")
        return shFile.exists() && shFile.canExecute()
    }

    /**
     * Get the path to a binary in userspace
     */
    fun getBinaryPath(binaryName: String): String {
        return File(File(context.filesDir, "usr/bin"), binaryName).absolutePath
    }

    /**
     * Get the path to a library in userspace
     */
    fun getLibraryPath(libraryName: String): String {
        return File(File(context.filesDir, "usr/lib"), libraryName).absolutePath
    }
}
