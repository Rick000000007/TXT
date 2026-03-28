package com.tx.terminal.session

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.tx.terminal.native.NativeTerminal
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Manages multiple terminal sessions
 * - Max 10 sessions
 * - Session switching
 * - State maintenance
 */
class TerminalSessionManager private constructor() {

    companion object {
        private const val TAG = "SessionManager"
        private const val MAX_SESSIONS = 10

        @Volatile
        private var instance: TerminalSessionManager? = null

        fun getInstance(): TerminalSessionManager {
            return instance ?: synchronized(this) {
                instance ?: TerminalSessionManager().also { instance = it }
            }
        }
    }

    // Session list (thread-safe)
    private val sessions = CopyOnWriteArrayList<TerminalSession>()

    // Current active session
    private var currentSessionId: String? = null

    // LiveData for UI updates
    private val _sessionsLiveData = MutableLiveData<List<TerminalSession>>(emptyList())
    val sessionsLiveData: LiveData<List<TerminalSession>> = _sessionsLiveData

    private val _currentSessionLiveData = MutableLiveData<TerminalSession?>(null)
    val currentSessionLiveData: LiveData<TerminalSession?> = _currentSessionLiveData

    // Output callback that routes to current session
    private val outputCallback = object : NativeTerminal.OutputCallback {
        override fun onOutput(data: ByteArray) {
            currentSessionId?.let { id ->
                getSession(id)?.onOutput(data)
            }
        }
    }

    init {
        NativeTerminal.setOutputCallback(outputCallback)
    }

    /**
     * Create a new session
     */
    fun createSession(name: String? = null): TerminalSession? {
        if (sessions.size >= MAX_SESSIONS) {
            Log.w(TAG, "Maximum sessions reached ($MAX_SESSIONS)")
            return null
        }

        val session = TerminalSession(name = name ?: "Session ${sessions.size + 1}")
        sessions.add(session)

        updateLiveData()

        // Switch to new session
        switchToSession(session.id)

        Log.d(TAG, "Created session: ${session.name} (total: ${sessions.size})")
        return session
    }

    /**
     * Close a session
     */
    fun closeSession(sessionId: String): Boolean {
        val session = getSession(sessionId) ?: return false

        // Kill any running process
        if (session.isProcessRunning()) {
            session.killProcess(9)
        }

        sessions.remove(session)

        // Switch to another session if this was the current one
        if (currentSessionId == sessionId) {
            currentSessionId = sessions.firstOrNull()?.id
            _currentSessionLiveData.postValue(getSession(currentSessionId))
        }

        updateLiveData()

        Log.d(TAG, "Closed session: ${session.name} (remaining: ${sessions.size})")
        return true
    }

    /**
     * Switch to a session
     */
    fun switchToSession(sessionId: String): Boolean {
        val session = getSession(sessionId) ?: return false

        // Deactivate current session
        getSession(currentSessionId)?.isActive = false

        // Activate new session
        currentSessionId = sessionId
        session.isActive = true

        _currentSessionLiveData.postValue(session)

        Log.d(TAG, "Switched to session: ${session.name}")
        return true
    }

    /**
     * Get a session by ID
     */
    fun getSession(sessionId: String?): TerminalSession? {
        if (sessionId == null) return null
        return sessions.find { it.id == sessionId }
    }

    /**
     * Get the current session
     */
    fun getCurrentSession(): TerminalSession? {
        return getSession(currentSessionId)
    }

    /**
     * Get all sessions
     */
    fun getAllSessions(): List<TerminalSession> {
        return sessions.toList()
    }

    /**
     * Get session count
     */
    fun getSessionCount(): Int = sessions.size

    /**
     * Rename a session
     */
    fun renameSession(sessionId: String, newName: String): Boolean {
        val session = getSession(sessionId) ?: return false
        session.name = newName
        updateLiveData()
        return true
    }

    /**
     * Close all sessions
     */
    fun closeAllSessions() {
        sessions.forEach { session ->
            if (session.isProcessRunning()) {
                session.killProcess(9)
            }
        }
        sessions.clear()
        currentSessionId = null
        updateLiveData()
        _currentSessionLiveData.postValue(null)

        Log.d(TAG, "All sessions closed")
    }

    /**
     * Update LiveData for UI
     */
    private fun updateLiveData() {
        _sessionsLiveData.postValue(sessions.toList())
    }

    /**
     * Execute command in current session
     */
    fun executeInCurrentSession(command: String): Boolean {
        return getCurrentSession()?.executeCommand(command) ?: false
    }

    /**
     * Send input to current session
     */
    fun sendInputToCurrentSession(input: String): Boolean {
        return getCurrentSession()?.sendInput(input) ?: false
    }

    /**
     * Send key to current session
     */
    fun sendKeyToCurrentSession(keyCode: Int, ctrl: Boolean = false): Boolean {
        return getCurrentSession()?.sendKey(keyCode, ctrl) ?: false
    }
}
