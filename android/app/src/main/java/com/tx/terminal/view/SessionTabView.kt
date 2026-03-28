package com.tx.terminal.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import com.tx.terminal.R
import com.tx.terminal.session.TerminalSession

/**
 * Session Tab View for multi-session terminal
 * - Shows session name
 * - Running indicator
 * - Close button
 */
class SessionTabView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private lateinit var tvSessionName: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var indicator: View

    private var sessionId: String = ""
    private var isActive: Boolean = false

    private var onTabClickListener: ((String) -> Unit)? = null
    private var onTabCloseListener: ((String) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        setupView()
    }

    private fun setupView() {
        LayoutInflater.from(context).inflate(R.layout.view_session_tab, this, true)

        tvSessionName = findViewById(R.id.tv_session_name)
        btnClose = findViewById(R.id.btn_close)
        indicator = findViewById(R.id.running_indicator)

        // Tab click - switch to session
        setOnClickListener {
            onTabClickListener?.invoke(sessionId)
        }

        // Close button
        btnClose.setOnClickListener {
            onTabCloseListener?.invoke(sessionId)
        }
    }

    /**
     * Bind session data to this tab
     */
    fun bind(session: TerminalSession, isActive: Boolean) {
        this.sessionId = session.id
        this.isActive = isActive

        tvSessionName.text = session.name

        // Update visual state
        updateAppearance()

        // Show/hide running indicator
        indicator.visibility = if (session.isProcessRunning()) View.VISIBLE else View.INVISIBLE
    }

    /**
     * Update the tab appearance based on active state
     */
    private fun updateAppearance() {
        if (isActive) {
            setBackgroundResource(R.drawable.tab_background_active)
            tvSessionName.setTextColor(context.getColor(R.color.tab_active))
            btnClose.visibility = View.VISIBLE
        } else {
            setBackgroundResource(R.drawable.tab_background_inactive)
            tvSessionName.setTextColor(context.getColor(R.color.tab_inactive))
            btnClose.visibility = View.GONE
        }
    }

    /**
     * Set tab click listener
     */
    fun setOnTabClickListener(listener: (String) -> Unit) {
        onTabClickListener = listener
    }

    /**
     * Set tab close listener
     */
    fun setOnTabCloseListener(listener: (String) -> Unit) {
        onTabCloseListener = listener
    }

    /**
     * Get the session ID
     */
    fun getSessionId(): String = sessionId
}
