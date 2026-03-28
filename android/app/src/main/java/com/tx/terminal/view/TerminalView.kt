package com.tx.terminal.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.ActionMode
import android.view.GestureDetector
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.PopupMenu
import android.widget.Scroller
import androidx.core.content.ContextCompat
import com.tx.terminal.R
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Enhanced Terminal View with:
 * - Smooth GPU-friendly rendering
 * - Text selection with proper colors
 * - Copy/Paste support
 * - Long press menu
 * - Momentum scrolling
 * - Monochrome theme
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Paint objects for rendering
    private val textPaint: Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val cursorPaint: Paint = Paint()
    private val selectionPaint: Paint = Paint()
    private val backgroundPaint: Paint = Paint()

    // Buffer and display
    private val buffer = StringBuilder()
    private val lineBuffer = mutableListOf<String>()
    private val spannableBuilder = SpannableStringBuilder()

    // Font metrics
    private var charWidth: Float = 0f
    private var lineHeight: Float = 0f
    private var fontMetrics: Paint.FontMetrics? = null

    // Scrolling
    private val scroller: Scroller = Scroller(context)
    private var scrollY: Float = 0f
    private val maxScrollLines = 10000

    // Selection
    private var selectionStart: Int = -1
    private var selectionEnd: Int = -1
    private var isSelecting: Boolean = false
    private val selectionPath = mutableListOf<Pair<Int, Int>>() // line, col pairs

    // Gesture detectors
    private val gestureDetector: GestureDetector
    private val scaleGestureDetector: ScaleGestureDetector

    // Callbacks
    private var onBufferChangeListener: ((String) -> Unit)? = null
    private var onKeyListener: ((Int, Boolean) -> Unit)? = null // keyCode, ctrl

    // Handler for UI updates
    private val handler = Handler(Looper.getMainLooper())

    // Clipboard
    private val clipboard: ClipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    // Text size and colors
    private var textSizeSp: Float = 14f
    private var textColor: Int = Color.WHITE
    private var bgColor: Int = Color.BLACK
    private var selectionBgColor: Int = Color.WHITE
    private var selectionFgColor: Int = Color.BLACK
    private var cursorColor: Int = Color.WHITE

    // Performance optimization
    private var lastDrawTime: Long = 0
    private val minDrawInterval = 16L // ~60fps
    private var pendingDraw: Boolean = false

    // ANSI color support
    private val ansiColors = mapOf(
        30 to Color.BLACK,
        31 to 0xFFFF5555.toInt(),
        32 to 0xFF55FF55.toInt(),
        33 to 0xFFFFFF55.toInt(),
        34 to 0xFF5555FF.toInt(),
        35 to 0xFFFF55FF.toInt(),
        36 to 0xFF55FFFF.toInt(),
        37 to Color.WHITE,
        90 to Color.DKGRAY,
        91 to 0xFFFF8888.toInt(),
        92 to 0xFF88FF88.toInt(),
        93 to 0xFFFFFF88.toInt(),
        94 to 0xFF8888FF.toInt(),
        95 to 0xFFFF88FF.toInt(),
        96 to 0xFF88FFFF.toInt(),
        97 to Color.WHITE
    )

    init {
        // Initialize paints
        textPaint.apply {
            typeface = Typeface.MONOSPACE
            textSize = textSizeSp * resources.displayMetrics.scaledDensity
            color = textColor
            isAntiAlias = true
            isSubpixelText = true
        }

        cursorPaint.apply {
            color = cursorColor
            style = Paint.Style.FILL
        }

        selectionPaint.apply {
            color = selectionBgColor
            style = Paint.Style.FILL
        }

        backgroundPaint.apply {
            color = bgColor
            style = Paint.Style.FILL
        }

        // Calculate font metrics
        updateFontMetrics()

        // Initialize gesture detectors
        gestureDetector = GestureDetector(context, GestureListener())
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())

        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)

        // Focusable for keyboard input
        isFocusable = true
        isFocusableInTouchMode = true

        // Background
        setBackgroundColor(bgColor)
    }

    private fun updateFontMetrics() {
        fontMetrics = textPaint.fontMetrics
        charWidth = textPaint.measureText("M")
        lineHeight = fontMetrics?.let { it.descent - it.ascent } ?: (textSizeSp * 1.2f)
    }

    /**
     * Append text to the terminal buffer
     */
    fun appendText(text: String) {
        synchronized(buffer) {
            buffer.append(text)

            // Process newlines for line buffer
            val lines = text.split("\n")
            if (lineBuffer.isEmpty()) {
                lineBuffer.addAll(lines)
            } else {
                // Append first part to last line
                lineBuffer[lineBuffer.size - 1] = lineBuffer.last() + lines[0]
                // Add remaining lines
                lineBuffer.addAll(lines.subList(1, lines.size))
            }

            // Limit buffer size
            while (lineBuffer.size > maxScrollLines) {
                lineBuffer.removeAt(0)
            }

            // Update spannable
            spannableBuilder.clear()
            spannableBuilder.append(processAnsiCodes(buffer.toString()))

            // Notify listener
            onBufferChangeListener?.invoke(buffer.toString())
        }

        // Request draw with throttling
        requestDraw()
    }

    /**
     * Append raw bytes to the terminal
     */
    fun appendBytes(bytes: ByteArray) {
        appendText(String(bytes, Charsets.UTF_8))
    }

    /**
     * Clear the terminal
     */
    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            lineBuffer.clear()
            spannableBuilder.clear()
        }
        selectionStart = -1
        selectionEnd = -1
        scrollY = 0f
        invalidate()
    }

    /**
     * Get the current buffer content
     */
    fun getBuffer(): String = synchronized(buffer) { buffer.toString() }

    /**
     * Get selected text
     */
    fun getSelectedText(): String {
        if (selectionStart < 0 || selectionEnd < 0 || selectionStart == selectionEnd) {
            return ""
        }
        return synchronized(buffer) {
            buffer.substring(min(selectionStart, selectionEnd), max(selectionStart, selectionEnd))
        }
    }

    /**
     * Copy selected text to clipboard
     */
    fun copyToClipboard(): Boolean {
        val selected = getSelectedText()
        if (selected.isEmpty()) return false

        val clip = ClipData.newPlainText("Terminal Text", selected)
        clipboard.setPrimaryClip(clip)
        return true
    }

    /**
     * Paste from clipboard
     */
    fun pasteFromClipboard(): String? {
        if (clipboard.hasPrimaryClip()) {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                return clip.getItemAt(0).text?.toString()
            }
        }
        return null
    }

    /**
     * Select all text
     */
    fun selectAll() {
        synchronized(buffer) {
            selectionStart = 0
            selectionEnd = buffer.length
        }
        invalidate()
    }

    /**
     * Clear selection
     */
    fun clearSelection() {
        selectionStart = -1
        selectionEnd = -1
        selectionPath.clear()
        invalidate()
    }

    /**
     * Set the buffer change listener
     */
    fun setOnBufferChangeListener(listener: (String) -> Unit) {
        onBufferChangeListener = listener
    }

    /**
     * Set key listener for virtual keyboard
     */
    fun setOnKeyListener(listener: (Int, Boolean) -> Unit) {
        onKeyListener = listener
    }

    /**
     * Scroll to the bottom
     */
    fun scrollToBottom() {
        val totalHeight = lineBuffer.size * lineHeight
        val viewHeight = height.toFloat()
        scrollY = max(0f, totalHeight - viewHeight)
        invalidate()
    }

    /**
     * Set text size
     */
    fun setTerminalTextSize(sizeSp: Float) {
        textSizeSp = sizeSp
        textPaint.textSize = sizeSp * resources.displayMetrics.scaledDensity
        updateFontMetrics()
        invalidate()
    }

    /**
     * Get current text size
     */
    fun getTerminalTextSize(): Float = textSizeSp

    /**
     * Request draw with throttling for performance
     */
    private fun requestDraw() {
        if (pendingDraw) return

        val currentTime = System.currentTimeMillis()
        val delay = max(0, minDrawInterval - (currentTime - lastDrawTime))

        pendingDraw = true
        handler.postDelayed({
            pendingDraw = false
            lastDrawTime = System.currentTimeMillis()
            invalidate()
        }, delay)
    }

    /**
     * Process ANSI escape codes
     */
    private fun processAnsiCodes(text: String): SpannableStringBuilder {
        val result = SpannableStringBuilder()
        var currentIndex = 0
        var currentColor = textColor

        val ansiPattern = "\u001B\\[([0-9;]*)m".toRegex()

        ansiPattern.findAll(text).forEach { match ->
            // Text before ANSI code
            val beforeText = text.substring(currentIndex, match.range.first)
            if (beforeText.isNotEmpty()) {
                val start = result.length
                result.append(beforeText)
                result.setSpan(
                    ForegroundColorSpan(currentColor),
                    start,
                    result.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Process ANSI codes
            val codes = match.groupValues[1].split(";").filter { it.isNotEmpty() }
            for (code in codes) {
                when (code) {
                    "0" -> currentColor = textColor
                    "1" -> { /* Bold - not implemented */ }
                    in ansiColors.keys.map { it.toString() } -> {
                        currentColor = ansiColors[code.toInt()] ?: textColor
                    }
                }
            }

            currentIndex = match.range.last + 1
        }

        // Remaining text
        if (currentIndex < text.length) {
            val remainingText = text.substring(currentIndex)
            val start = result.length
            result.append(remainingText)
            result.setSpan(
                ForegroundColorSpan(currentColor),
                start,
                result.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return result
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        synchronized(lineBuffer) {
            val startLine = (scrollY / lineHeight).toInt()
            val endLine = min(lineBuffer.size, startLine + (height / lineHeight).toInt() + 1)

            var currentY = paddingTop.toFloat() - (scrollY % lineHeight)

            for (i in startLine until endLine) {
                if (i >= lineBuffer.size) break

                val line = lineBuffer[i]
                val lineStart = getLineStartIndex(i)

                // Draw selection background for this line
                drawSelectionBackground(canvas, i, lineStart, line.length, currentY)

                // Draw text
                canvas.drawText(line, paddingLeft.toFloat(), currentY - (fontMetrics?.ascent ?: 0f), textPaint)

                currentY += lineHeight
            }
        }

        // Draw cursor if at end
        // TODO: Implement cursor drawing
    }

    private fun drawSelectionBackground(canvas: Canvas, lineIndex: Int, lineStart: Int, lineLength: Int, y: Float) {
        if (selectionStart < 0 || selectionEnd < 0) return

        val selStart = min(selectionStart, selectionEnd)
        val selEnd = max(selectionStart, selectionEnd)

        val lineEnd = lineStart + lineLength

        if (selEnd <= lineStart || selStart >= lineEnd) return

        val selStartInLine = max(0, selStart - lineStart)
        val selEndInLine = min(lineLength, selEnd - lineStart)

        val startX = paddingLeft + selStartInLine * charWidth
        val endX = paddingLeft + selEndInLine * charWidth

        // Draw selection background (white)
        canvas.drawRect(
            startX,
            y + (fontMetrics?.ascent ?: 0f),
            endX,
            y + (fontMetrics?.descent ?: 0f),
            selectionPaint
        )
    }

    private fun getLineStartIndex(lineIndex: Int): Int {
        var index = 0
        for (i in 0 until min(lineIndex, lineBuffer.size)) {
            index += lineBuffer[i].length + 1 // +1 for newline
        }
        return index
    }

    private fun getCharIndexFromPoint(x: Float, y: Float): Int {
        val lineIndex = ((y + scrollY - paddingTop) / lineHeight).toInt()
        if (lineIndex < 0 || lineIndex >= lineBuffer.size) return -1

        val colIndex = ((x - paddingLeft) / charWidth).toInt()
        val line = lineBuffer[lineIndex]
        val clampedCol = max(0, min(colIndex, line.length))

        return getLineStartIndex(lineIndex) + clampedCol
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event ?: return false

        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!isSelecting) {
                    val charIndex = getCharIndexFromPoint(event.x, event.y)
                    if (charIndex >= 0) {
                        selectionStart = charIndex
                        selectionEnd = charIndex
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSelecting) {
                    val charIndex = getCharIndexFromPoint(event.x, event.y)
                    if (charIndex >= 0) {
                        selectionEnd = charIndex
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                // Selection complete
            }
        }

        return true
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = scroller.currY.toFloat()
            invalidate()
        }
    }

    /**
     * Show long press context menu
     */
    fun showContextMenu(anchor: View) {
        val popup = PopupMenu(context, anchor)
        popup.menuInflater.inflate(R.menu.terminal_context_menu, popup.menu)

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_copy -> {
                    copyToClipboard()
                    clearSelection()
                    true
                }
                R.id.action_paste -> {
                    val text = pasteFromClipboard()
                    text?.let { onKeyListener?.invoke(-1, false) } // Custom paste key
                    true
                }
                R.id.action_select_all -> {
                    selectAll()
                    true
                }
                else -> false
            }
        }

        popup.show()
    }

    // Gesture listener for scroll and tap
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            requestFocus()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Zoom to default
            setTerminalTextSize(14f)
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            isSelecting = true
            showContextMenu(this@TerminalView)
        }

        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            val maxScroll = max(0f, lineBuffer.size * lineHeight - height)
            scrollY = max(0f, min(maxScroll, scrollY + distanceY))
            invalidate()
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            val maxScroll = max(0f, lineBuffer.size * lineHeight - height)
            scroller.fling(
                0, scrollY.toInt(),
                0, -velocityY.toInt(),
                0, 0,
                0, maxScroll.toInt()
            )
            invalidate()
            return true
        }
    }

    // Scale listener for pinch-to-zoom
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val newSize = max(8f, min(32f, textSizeSp * scaleFactor))
            setTerminalTextSize(newSize)
            return true
        }
    }

    companion object {
        private const val MAX_BUFFER_SIZE = 100000
    }
}
