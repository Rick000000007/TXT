package com.tx.terminal.view

import android.content.Context
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.tx.terminal.R

/**
 * Virtual Key Bar for terminal input
 * - Special keys (CTRL, TAB, ALT, ESC, etc.)
 * - Arrow keys
 * - CTRL logic: tap = one-time, long press = lock
 */
class VirtualKeyBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // CTRL state
    private var ctrlActive: Boolean = false
    private var ctrlLocked: Boolean = false

    // Key listener
    private var onKeyListener: ((KeyType, Boolean) -> Unit)? = null

    // Buttons
    private lateinit var btnCtrl: Button
    private lateinit var btnTab: Button
    private lateinit var btnEsc: Button
    private lateinit var btnHome: Button
    private lateinit var btnEnd: Button
    private lateinit var btnPgUp: Button
    private lateinit var btnPgDn: Button
    private lateinit var btnUp: Button
    private lateinit var btnDown: Button
    private lateinit var btnLeft: Button
    private lateinit var btnRight: Button

    enum class KeyType {
        CTRL, TAB, ALT, ESC, HOME, END, PGUP, PGDN, UP, DOWN, LEFT, RIGHT
    }

    init {
        orientation = HORIZONTAL
        setupView()
    }

    private fun setupView() {
        // Inflate layout
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.view_virtual_keybar, this, true)

        // Find buttons
        btnCtrl = view.findViewById(R.id.btn_ctrl)
        btnTab = view.findViewById(R.id.btn_tab)
        btnEsc = view.findViewById(R.id.btn_esc)
        btnHome = view.findViewById(R.id.btn_home)
        btnEnd = view.findViewById(R.id.btn_end)
        btnPgUp = view.findViewById(R.id.btn_pgup)
        btnPgDn = view.findViewById(R.id.btn_pgdn)
        btnUp = view.findViewById(R.id.btn_up)
        btnDown = view.findViewById(R.id.btn_down)
        btnLeft = view.findViewById(R.id.btn_left)
        btnRight = view.findViewById(R.id.btn_right)

        // Setup CTRL button with special logic
        setupCtrlButton()

        // Setup other buttons
        setupButton(btnTab, KeyType.TAB)
        setupButton(btnEsc, KeyType.ESC)
        setupButton(btnHome, KeyType.HOME)
        setupButton(btnEnd, KeyType.END)
        setupButton(btnPgUp, KeyType.PGUP)
        setupButton(btnPgDn, KeyType.PGDN)
        setupButton(btnUp, KeyType.UP)
        setupButton(btnDown, KeyType.DOWN)
        setupButton(btnLeft, KeyType.LEFT)
        setupButton(btnRight, KeyType.RIGHT)
    }

    private fun setupCtrlButton() {
        btnCtrl.apply {
            // Single tap - one-time CTRL
            setOnClickListener {
                if (ctrlLocked) {
                    // Unlock if already locked
                    ctrlLocked = false
                    ctrlActive = false
                    updateCtrlButtonState()
                } else if (ctrlActive) {
                    // Lock if already active (second tap)
                    ctrlLocked = true
                    updateCtrlButtonState()
                } else {
                    // Activate one-time
                    ctrlActive = true
                    updateCtrlButtonState()
                    // Notify
                    onKeyListener?.invoke(KeyType.CTRL, true)
                    // If not locked, deactivate after use
                    if (!ctrlLocked) {
                        postDelayed({
                            ctrlActive = false
                            updateCtrlButtonState()
                        }, 500)
                    }
                }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }

            // Long press - lock CTRL
            setOnLongClickListener {
                ctrlLocked = !ctrlLocked
                ctrlActive = ctrlLocked
                updateCtrlButtonState()
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                true
            }
        }
    }

    private fun setupButton(button: Button, keyType: KeyType) {
        button.setOnClickListener {
            val useCtrl = ctrlActive || ctrlLocked
            onKeyListener?.invoke(keyType, useCtrl)

            // Clear one-time CTRL after use
            if (ctrlActive && !ctrlLocked) {
                ctrlActive = false
                updateCtrlButtonState()
            }

            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private fun updateCtrlButtonState() {
        btnCtrl.apply {
            when {
                ctrlLocked -> {
                    text = "CTRL*"
                    isSelected = true
                    setBackgroundResource(R.drawable.key_background_pressed)
                }
                ctrlActive -> {
                    text = "CTRL"
                    isSelected = true
                    setBackgroundResource(R.drawable.key_background_pressed)
                }
                else -> {
                    text = "CTRL"
                    isSelected = false
                    setBackgroundResource(R.drawable.key_background)
                }
            }
        }
    }

    /**
     * Set the key listener
     */
    fun setOnKeyListener(listener: (KeyType, Boolean) -> Unit) {
        onKeyListener = listener
    }

    /**
     * Check if CTRL is active (one-time or locked)
     */
    fun isCtrlActive(): Boolean = ctrlActive || ctrlLocked

    /**
     * Clear CTRL state
     */
    fun clearCtrl() {
        if (!ctrlLocked) {
            ctrlActive = false
            updateCtrlButtonState()
        }
    }

    /**
     * Force unlock CTRL
     */
    fun unlockCtrl() {
        ctrlLocked = false
        ctrlActive = false
        updateCtrlButtonState()
    }
}
