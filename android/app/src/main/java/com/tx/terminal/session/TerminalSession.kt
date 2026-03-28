package com.tx.terminal.session

import android.util.Log
import com.tx.terminal.native.NativeTerminal
import java.util.UUID

/**
 * Represents a single terminal session
 */
class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Session",
    var isActive: Boolean = false
) {
    companion object {
        private const val TAG = "TerminalSession"
        private var sessionCounter = 0
    }

    val sessionNumber: Int = ++sessionCounter
    val createdAt: Long = System.currentTimeMillis()

    // Buffer for this session's output
    private val outputBuffer = StringBuilder()

    // Callback for output
    private var outputCallback: ((ByteArray) -> Unit)? = null

    init {
        if (name == "Session") {
            name = "Session $sessionNumber"
        }
    }

    /**
     * Set the output callback for this session
     */
    fun setOutputCallback(callback: (ByteArray) -> Unit) {
        outputCallback = callback
    }

    /**
     * Handle output data
     */
    fun onOutput(data: ByteArray) {
        synchronized(outputBuffer) {
            outputBuffer.append(String(data, Charsets.UTF_8))
        }
        outputCallback?.invoke(data)
    }

    /**
     * Get the session's output buffer
     */
    fun getBuffer(): String = synchronized(outputBuffer) { outputBuffer.toString() }

    /**
     * Clear the session buffer
     */
    fun clearBuffer() {
        synchronized(outputBuffer) {
            outputBuffer.clear()
        }
    }

    /**
     * Execute a command in this session
     */
    fun executeCommand(command: String): Boolean {
        return if (isActive) {
            NativeTerminal.sendInput("$command\n")
        } else {
            NativeTerminal.executeCommand(command)
        }
    }

    /**
     * Send raw input to this session
     */
    fun sendInput(input: String): Boolean {
        return NativeTerminal.sendInput(input)
    }

    /**
     * Send a key press to this session
     */
    fun sendKey(keyCode: Int, ctrl: Boolean = false): Boolean {
        val key = when (keyCode) {
            3 -> if (ctrl) "\u0003" else ""  // CTRL+C
            4 -> if (ctrl) "\u0004" else ""  // CTRL+D
            12 -> if (ctrl) "\u000C" else "" // CTRL+L
            9 -> "\t"                        // TAB
            27 -> "\u001B"                   // ESC
            else -> ""
        }
        return if (key.isNotEmpty()) {
            NativeTerminal.sendInput(key)
        } else {
            false
        }
    }

    /**
     * Resize the terminal for this session
     */
    fun resize(rows: Int, cols: Int): Boolean {
        return NativeTerminal.resizeTerminal(rows, cols)
    }

    /**
     * Check if a process is running in this session
     */
    fun isProcessRunning(): Boolean {
        return NativeTerminal.isProcessRunning()
    }

    /**
     * Kill the running process in this session
     */
    fun killProcess(signal: Int = 15): Boolean {
        return NativeTerminal.killProcess(signal)
    }

    override fun toString(): String {
        return "TerminalSession(id=$id, name=$name, isActive=$isActive)"
    }
}
