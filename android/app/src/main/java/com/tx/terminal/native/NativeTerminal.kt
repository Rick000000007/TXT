package com.tx.terminal.native

/**
 * JNI bridge for native terminal operations.
 * Provides access to PTY, process execution, and environment management.
 */
object NativeTerminal {

    init {
        System.loadLibrary("txterminal")
    }

    /**
     * Callback interface for terminal output
     */
    interface OutputCallback {
        fun onOutput(data: ByteArray)
    }

    // Native methods
    @JvmStatic
    external fun initialize()

    @JvmStatic
    external fun setUserspacePath(path: String)

    @JvmStatic
    external fun setOutputCallback(callback: OutputCallback?)

    @JvmStatic
    external fun executeCommand(command: String): Boolean

    @JvmStatic
    external fun executeWithLinker(linker: String, executable: String, args: Array<String>?): Boolean

    @JvmStatic
    external fun sendInput(input: String): Boolean

    @JvmStatic
    external fun sendRawData(data: ByteArray, length: Int): Boolean

    @JvmStatic
    external fun resizeTerminal(rows: Int, cols: Int): Boolean

    @JvmStatic
    external fun isProcessRunning(): Boolean

    @JvmStatic
    external fun getCurrentPid(): Int

    @JvmStatic
    external fun killProcess(signal: Int): Boolean

    @JvmStatic
    external fun addEnvironmentVariable(name: String, value: String)

    @JvmStatic
    external fun clearEnvironmentVariables()

    @JvmStatic
    external fun setUserspaceEnabled(enabled: Boolean)

    @JvmStatic
    external fun isUserspaceEnabled(): Boolean

    @JvmStatic
    external fun getLastError(): String

    @JvmStatic
    external fun cleanup()
}
