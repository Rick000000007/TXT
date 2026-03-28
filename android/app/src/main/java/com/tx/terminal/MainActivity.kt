package com.tx.terminal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import com.tx.terminal.native.NativeTerminal
import com.tx.terminal.session.TerminalSession
import com.tx.terminal.session.TerminalSessionManager
import com.tx.terminal.view.SessionTabView
import com.tx.terminal.view.TerminalView
import com.tx.terminal.view.VirtualKeyBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main Activity for TX Terminal
 * - Multi-session support
 * - Virtual key bar
 * - Modern monochrome UI
 */
class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var toolbar: Toolbar
    private lateinit var tabContainer: LinearLayout
    private lateinit var tabScrollView: HorizontalScrollView
    private lateinit var terminalView: TerminalView
    private lateinit var commandInput: EditText
    private lateinit var executeButton: ImageButton
    private lateinit var promptText: TextView
    private lateinit var virtualKeyBar: VirtualKeyBar
    private lateinit var btnNewTab: ImageButton
    private lateinit var btnToggleKeys: ImageButton

    // Session management
    private val sessionManager = TerminalSessionManager.getInstance()
    private val tabViews = mutableMapOf<String, SessionTabView>()

    // Command history
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = -1

    companion object {
        private const val TAG = "MainActivity"
        private const val DEFAULT_SHELL = "/system/bin/sh"
        private const val LINKER_PATH = "/system/bin/linker64"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupToolbar()
        setupViews()
        setupSessionManager()
        setupVirtualKeyBar()

        // Create initial session
        createNewSession()
    }

    private fun setupToolbar() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = getString(R.string.app_name)
    }

    private fun setupViews() {
        // Session tabs
        tabContainer = findViewById(R.id.tab_container)
        tabScrollView = findViewById(R.id.tab_scroll)
        btnNewTab = findViewById(R.id.btn_new_tab)
        btnNewTab.setOnClickListener { createNewSession() }

        // Terminal view
        terminalView = findViewById(R.id.terminal_view)
        terminalView.setOnKeyListener { keyCode, ctrl ->
    handleVirtualKey(keyCode, ctrl)
}

        // Command input
        commandInput = findViewById(R.id.command_input)
        executeButton = findViewById(R.id.execute_button)
        promptText = findViewById(R.id.prompt_text)

        // Execute button
        executeButton.setOnClickListener { executeCurrentCommand() }

        // Command input handling
        commandInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                executeCurrentCommand()
                true
            } else {
                false
            }
        }

        // History navigation
        commandInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        navigateHistory(-1)
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        navigateHistory(1)
                        true
                    }
                    else -> false
                }
            } else {
                false
            }
        }

        // Toggle virtual keys
        btnToggleKeys = findViewById(R.id.btn_toggle_keys)
        btnToggleKeys.setOnClickListener {
            toggleVirtualKeys()
        }

        // Virtual key bar
        virtualKeyBar = findViewById(R.id.virtual_key_bar)
    }

    private fun setupSessionManager() {
        // Observe session changes
        sessionManager.sessionsLiveData.observe(this) { sessions ->
            updateTabs(sessions)
        }

        sessionManager.currentSessionLiveData.observe(this) { session ->
            session?.let {
                updateActiveTab(it.id)
                // Clear and restore session buffer
                terminalView.clear()
                terminalView.appendText(it.getBuffer())
            }
        }
    }

    private fun setupVirtualKeyBar() {
        virtualKeyBar.setOnKeyListener { keyType, ctrl ->
            handleVirtualKey(keyType, ctrl)
        }
    }

    private fun handleVirtualKey(keyType: VirtualKeyBar.KeyType, ctrl: Boolean) {
        when (keyType) {
            VirtualKeyBar.KeyType.CTRL -> {
                // CTRL is handled internally by VirtualKeyBar
            }
            VirtualKeyBar.KeyType.TAB -> sendKey(9, ctrl)
            VirtualKeyBar.KeyType.ESC -> sendKey(27, ctrl)
            VirtualKeyBar.KeyType.HOME -> sendInput("\u001B[H")
            VirtualKeyBar.KeyType.END -> sendInput("\u001B[F")
            VirtualKeyBar.KeyType.PGUP -> sendInput("\u001B[5~")
            VirtualKeyBar.KeyType.PGDN -> sendInput("\u001B[6~")
            VirtualKeyBar.KeyType.UP -> sendInput("\u001B[A")
            VirtualKeyBar.KeyType.DOWN -> sendInput("\u001B[B")
            VirtualKeyBar.KeyType.LEFT -> sendInput("\u001B[D")
            VirtualKeyBar.KeyType.RIGHT -> sendInput("\u001B[C")
            else -> {}
        }
    }

    private fun sendKey(keyCode: Int, ctrl: Boolean) {
        val key = when {
            ctrl && keyCode == 3 -> "\u0003"  // CTRL+C
            ctrl && keyCode == 4 -> "\u0004"  // CTRL+D
            ctrl && keyCode == 12 -> "\u000C" // CTRL+L
            keyCode == 9 -> "\t"               // TAB
            keyCode == 27 -> "\u001B"          // ESC
            else -> ""
        }
        if (key.isNotEmpty()) {
            sendInput(key)
        }
    }

    private fun sendInput(input: String) {
        sessionManager.sendInputToCurrentSession(input)
    }

    private fun createNewSession() {
        val session = sessionManager.createSession()
        session?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                startShell(it)
            }
        }
    }

    private fun closeSession(sessionId: String) {
        sessionManager.closeSession(sessionId)
    }

    private fun switchToSession(sessionId: String) {
        sessionManager.switchToSession(sessionId)
    }

    private fun updateTabs(sessions: List<TerminalSession>) {
        // Remove tabs for closed sessions
        val currentIds = sessions.map { it.id }.toSet()
        val removedIds = tabViews.keys.filter { it !in currentIds }
        removedIds.forEach { id ->
            tabViews.remove(id)?.let { tabContainer.removeView(it) }
        }

        // Add/update tabs
        sessions.forEach { session ->
            val isActive = session.id == sessionManager.currentSessionLiveData.value?.id

            val tabView = tabViews[session.id]
            if (tabView == null) {
                // Create new tab
                val newTab = SessionTabView(this)
                newTab.bind(session, isActive)
                newTab.setOnTabClickListener { switchToSession(it) }
                newTab.setOnTabCloseListener { closeSession(it) }
                tabViews[session.id] = newTab
                tabContainer.addView(newTab)
            } else {
                // Update existing tab
                tabView.bind(session, isActive)
            }
        }

        // Scroll to active tab
        sessionManager.currentSessionLiveData.value?.let { session ->
            tabViews[session.id]?.let { tab ->
                tabScrollView.post {
                    tabScrollView.smoothScrollTo(tab.left, 0)
                }
            }
        }
    }

    private fun updateActiveTab(sessionId: String) {
        tabViews.forEach { (id, tab) ->
            val session = sessionManager.getSession(id)
            session?.let {
                tab.bind(it, id == sessionId)
            }
        }
    }

    private suspend fun startShell(session: TerminalSession) {
        val useUserspace = TXApplication.getInstance().isUserspaceEnabled()
        val userspaceDir = TXApplication.getInstance().getUserspaceDir()
        val shellPath = if (useUserspace) {
            "$userspaceDir/bin/sh"
        } else {
            DEFAULT_SHELL
        }

        // Set output callback for this session
        session.setOutputCallback { data ->
            runOnUiThread {
                if (session.id == sessionManager.getCurrentSession()?.id) {
                    terminalView.appendBytes(data)
                }
            }
        }

        val success = if (useUserspace && java.io.File(shellPath).exists()) {
            NativeTerminal.executeWithLinker(LINKER_PATH, shellPath, arrayOf("-l"))
        } else {
            NativeTerminal.executeCommand(DEFAULT_SHELL)
        }

        if (!success) {
            runOnUiThread {
	                terminalView.appendText("Warning: Could not start shell\n")
            }
        }
    }
	
    private fun executeCurrentCommand() {
        val command = commandInput.text.toString().trim()
        if (command.isEmpty()) return

        // Add to history
        commandHistory.add(command)
        historyIndex = commandHistory.size

        // Display command
        terminalView.appendText("$ $command\n")

        // Clear input
        commandInput.text.clear()

        // Execute
        lifecycleScope.launch(Dispatchers.IO) {
            executeCommand(command)
        }
    }

    private fun executeCommand(command: String) {
        // Handle built-in commands
        when {
            command == "help" -> {
                runOnUiThread { showHelp() }
                return
            }
            command == "clear" -> {
                runOnUiThread { terminalView.clear() }
                return
            }
            command == "exit" -> {
                sessionManager.getCurrentSession()?.killProcess(15)
                return
            }
        }

        // Send to current session
        sessionManager.executeInCurrentSession(command)
    }

    private fun navigateHistory(direction: Int) {
    if (commandHistory.isEmpty()) return

    historyIndex += direction
    historyIndex = historyIndex.coerceIn(0, commandHistory.size - 1)

    if (historyIndex in commandHistory.indices) {
        commandInput.setText(commandHistory[historyIndex])
        commandInput.setSelection(commandInput.text.length)
    } else {
        commandInput.text.clear()
    }
}
    private fun toggleVirtualKeys() {
        virtualKeyBar.visibility = if (virtualKeyBar.visibility == View.VISIBLE) {
            View.GONE
        } else {
            View.VISIBLE
        }
    }

    private fun showHelp() {
        val helpText = """
            |TX Terminal Commands:
            |  help              - Show this help
            |  clear             - Clear screen
            |  exit              - Close session
            |
            |Userspace:
            |  hello             - Demo program
            |  echo              - Print text
            |  setup-tx-storage  - Setup storage
            |
            |Sessions:
            |  + button          - New session
            |  x on tab          - Close session
            |  Tap tab           - Switch session
            |
            |Keys:
            |  Up/Down           - History
            |  Keyboard button   - Toggle virtual keys
            |
        """.trimMargin()
        terminalView.appendText(helpText + "\n")
    }

        // Handle keys from TerminalView

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear -> {
                terminalView.clear()
                true
            }
            R.id.action_keyboard -> {
                toggleKeyboard()
                true
            }
            R.id.action_settings -> {
                openSettings()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        if (imm.isActive(commandInput)) {
            imm.hideSoftInputFromWindow(commandInput.windowToken, 0)
        } else {
            imm.showSoftInput(commandInput, 0)
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionManager.closeAllSessions()
    }

    // Extension for TextView
    private fun TextView.appendBytes(bytes: ByteArray) {
        append(String(bytes, Charsets.UTF_8))
    }
}
