package com.tx.terminal

import android.app.Application
import android.content.Context
import android.util.Log
import com.tx.terminal.native.NativeTerminal
import com.tx.terminal.util.UserspaceInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Main Application class for TX Terminal.
 * Handles initialization of userspace and native components.
 */
class TXApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "TXApplication"
        private const val PREFS_NAME = "tx_terminal_prefs"
        private const val KEY_USERSPACE_INITIALIZED = "userspace_initialized"
        private const val KEY_USE_USERSPACE = "use_userspace"

        @Volatile
        private var instance: TXApplication? = null

        fun getInstance(): TXApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        Log.d(TAG, "TX Terminal Application starting...")

        // Initialize native terminal
        NativeTerminal.initialize()

        // Initialize userspace in background
        applicationScope.launch {
            initializeUserspace()
        }
    }

    /**
     * Initialize the userspace environment
     */
    private suspend fun initializeUserspace() {
        try {
            val installer = UserspaceInstaller(this)

            // Check if we need to install/update userspace
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val isInitialized = prefs.getBoolean(KEY_USERSPACE_INITIALIZED, false)

            if (!isInitialized || shouldUpdateUserspace()) {
                Log.d(TAG, "Installing userspace...")
                installer.installUserspace()
                prefs.edit().putBoolean(KEY_USERSPACE_INITIALIZED, true).apply()
                Log.d(TAG, "Userspace installed successfully")
            } else {
                Log.d(TAG, "Userspace already initialized")
            }

            // Set up native environment
            setupNativeEnvironment()

            // NEW: Auto-initialize storage if not exists
            initializeStorage()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize userspace", e)
        }
    }

    /**
     * NEW: Auto-initialize storage directories
     */
    private fun initializeStorage() {
        val storageDir = java.io.File(filesDir, "storage")
        if (!storageDir.exists()) {
            Log.d(TAG, "Auto-initializing storage...")
            try {
                // Run setup-tx-storage script
                val setupScript = java.io.File(getUserspaceDir(), "bin/setup-tx-storage")
                if (setupScript.exists()) {
                    Runtime.getRuntime().exec(
                        arrayOf("/system/bin/sh", "-c", setupScript.absolutePath)
                    )
                    Log.d(TAG, "Storage auto-initialization triggered")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-initialize storage", e)
            }
        }
    }

    /**
     * Check if userspace should be updated
     */
    private fun shouldUpdateUserspace(): Boolean {
        // TODO: Implement version checking logic
        return false
    }

    /**
     * Set up the native environment
     */
    private fun setupNativeEnvironment() {
        val userspaceDir = getUserspaceDir()

        // Set userspace path in native layer
        NativeTerminal.setUserspacePath(userspaceDir.absolutePath)

        // Check if userspace is enabled
        val useUserspace = isUserspaceEnabled()
        NativeTerminal.setUserspaceEnabled(useUserspace)

        Log.d(TAG, "Native environment configured. Userspace enabled: $useUserspace")
    }

    /**
     * Get the userspace directory
     */
    fun getUserspaceDir(): java.io.File {
        return java.io.File(filesDir, "usr")
    }

    /**
     * Get the storage directory
     */
    fun getStorageDir(): java.io.File {
        return java.io.File(filesDir, "storage")
    }

    /**
     * Check if userspace is enabled
     */
    fun isUserspaceEnabled(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_USE_USERSPACE, true)
    }

    /**
     * Set userspace enabled/disabled
     */
    fun setUserspaceEnabled(enabled: Boolean) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_USE_USERSPACE, enabled).apply()
        NativeTerminal.setUserspaceEnabled(enabled)
    }

    /**
     * Get the feature flag for userspace
     */
    fun getUseUserspaceFlag(): Boolean {
        return isUserspaceEnabled()
    }

    override fun onTerminate() {
        super.onTerminate()
        NativeTerminal.cleanup()
        instance = null
    }
}
