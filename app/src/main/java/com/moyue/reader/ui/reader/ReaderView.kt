package com.moyue.reader.ui.reader

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

data class ReaderStyle(
    val backgroundColor: Int = 0xFFF8F4EC.toInt(),
    val backgroundColorAlt: Int = 0xFFF2EADB.toInt(),
    val textColor: Int = 0xFF2B2620.toInt(),
    val secondaryColor: Int = 0xFF8A8173.toInt(),
    val accentColor: Int = 0xFFB5644A.toInt(),
    val fontSp: Float = 19f,
    val lineSpacing: Float = 1.7f,
    val marginDp: Float = 22f,
    val bookTitle: String = "",
    val chapterTitle: String = "",
    val chapterIndex: Int = 0,
    val totalChapters: Int = 1,
    val statusBarInsetPx: Int = 0,
    val navigationBarInsetPx: Int = 0,
    val bottomOverlayPx: Int = 0,
    val pageAnimation: Boolean = true
)

class ReaderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onPageChanged: ((page: Int, pageCount: Int) -> Unit)? = null
    var onNextChapter: (() -> Unit)? = null
    var onPreviousChapter: (() -> Unit)? = null
    var onCenterTap: (() -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val scaledDensity = density * resources.configuration.fontScale
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val velocityTracker = VelocityTracker.obtain()
    private val minFlingVelocity = 650f * density
    private val swipeThresholdFraction = 0.1f

    private val mainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val chromeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var style = ReaderStyle()
    private var chapterText: String = ""
    private var backgroundShader: Shader? = null
    private var textLayout: StaticLayout? = null
    private var pages: List<PageRange> = emptyList()
    private var pageCount = 0
    private var currentPage = 0
    private var pendingInitialPage: Int? = null

    private var contentLeft = 0f
    private var contentTop = 0f
    private var contentRight = 0f
    private var contentBottom = 0f
    private var contentWidth = 0
    private var contentHeight = 0

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var dragOffset = 0f
    private var animator: ValueAnimator? = null
    private var chromeVisible = true

    private data class PageRange(
        val startLine: Int,
        val endLine: Int,
        val startOffset: Int,
        val endOffset: Int
    )

    init {
        mainPaint.color = style.textColor
        headerPaint.textSize = 10.5f * scaledDensity
        chromeLinePaint.strokeWidth = 1f * density
        placeholderPaint.textSize = 17f * scaledDensity
        placeholderPaint.textAlign = Paint.Align.CENTER
        configurePaints()
    }

    fun setChapter(title: String, text: String, initialPage: Int) {
        chapterText = text
        if (title.isNotBlank()) {
            style = style.copy(chapterTitle = title)
        }
        if (width > 0 && height > 0) {
            paginate()
            applyInitialPage(initialPage)
            dragOffset = 0f
            invalidate()
            notifyPageChanged()
        } else {
            pendingInitialPage = initialPage
        }
    }

    private fun applyInitialPage(initialPage: Int) {
        pendingInitialPage = null
        currentPage = if (initialPage < 0) {
            (pageCount - 1).coerceAtLeast(0)
        } else {
            initialPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        }
    }

    fun updateStyle(newStyle: ReaderStyle) {
        if (newStyle == style) return
        val anchorOffset = currentPageRange()?.startOffset ?: 0
        val layoutChanged = newStyle.layoutDiffersFrom(style)
        val colorsChanged = newStyle.backgroundColor != style.backgroundColor ||
            newStyle.backgroundColorAlt != style.backgroundColorAlt
        style = newStyle
        configurePaints()
        if (colorsChanged) backgroundShader = null
        if (layoutChanged && width > 0 && height > 0 && chapterText.isNotEmpty()) {
            paginate()
            currentPage = pageIndexForOffset(anchorOffset)
            dragOffset = 0f
            invalidate()
            notifyPageChanged()
        } else {
            // Title/chrome-only changes just need a redraw, not a re-layout.
            invalidate()
        }
    }

    private fun ReaderStyle.layoutDiffersFrom(previous: ReaderStyle): Boolean =
        fontSp != previous.fontSp ||
            lineSpacing != previous.lineSpacing ||
            marginDp != previous.marginDp ||
            statusBarInsetPx != previous.statusBarInsetPx ||
            navigationBarInsetPx != previous.navigationBarInsetPx ||
            bottomOverlayPx != previous.bottomOverlayPx

    fun goToPage(page: Int) {
        if (pages.isEmpty()) return
        currentPage = page.coerceIn(0, pages.lastIndex)
        dragOffset = 0f
        invalidate()
        notifyPageChanged()
    }

    fun currentPageNumber(): Int = currentPage

    fun setChromeVisible(visible: Boolean) {
        if (chromeVisible != visible) {
            chromeVisible = visible
            invalidate()
        }
    }

    fun currentPageSnippet(): String {
        val page = currentPageRange() ?: return ""
        if (chapterText.isEmpty()) return ""
        val start = page.startOffset.coerceIn(0, chapterText.length)
        val end = page.endOffset.coerceIn(start, chapterText.length)
        return chapterText.substring(start, end)
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(90)
    }

    private fun configurePaints() {
        mainPaint.color = style.textColor
        mainPaint.textSize = style.fontSp * scaledDensity
        mainPaint.typeface = Typeface.create("serif", Typeface.NORMAL)
        headerPaint.color = style.secondaryColor
        chromeLinePaint.color = style.secondaryColor
        chromeLinePaint.alpha = 38
        placeholderPaint.color = style.accentColor
        placeholderPaint.typeface = Typeface.create("serif", Typeface.NORMAL)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        backgroundShader = null
        if (w > 0 && h > 0 && chapterText.isNotEmpty()) {
            val anchor = currentPageRange()?.startOffset ?: 0
            paginate()
            val initial = pendingInitialPage
            if (initial != null) {
                applyInitialPage(initial)
            } else {
                currentPage = pageIndexForOffset(anchor)
            }
            dragOffset = 0f
            notifyPageChanged()
        }
    }

    // Layout.BREAK_STRATEGY_SIMPLE has the same value as
    // android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE; lint only
    // accepts the newer symbolic constant.
    @SuppressLint("WrongConstant")
    private fun paginate() {
        if (width <= 0 || height <= 0 || chapterText.isEmpty()) {
            textLayout = null
            pages = emptyList()
            pageCount = 0
            return
        }
        val margin = style.marginDp * density
        contentLeft = margin
        contentRight = width - margin
        contentWidth = (contentRight - contentLeft).toInt().coerceAtLeast(120)

        val headerTop = style.statusBarInsetPx + 30f * density
        contentTop = headerTop + 34f * density
        contentBottom = height - style.navigationBarInsetPx - 20f * density - style.bottomOverlayPx
        contentHeight = (contentBottom - contentTop).toInt().coerceAtLeast(80)

        val builder = StaticLayout.Builder
            .obtain(chapterText, 0, chapterText.length, mainPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, style.lineSpacing)
            .setIncludePad(false)
            .setBreakStrategy(Layout.BREAK_STRATEGY_SIMPLE)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        val layout = builder.build()
        textLayout = layout

        val result = ArrayList<PageRange>()
        var startLine = 0
        while (startLine < layout.lineCount) {
            val top = layout.getLineTop(startLine)
            var endLine = startLine
            while (endLine + 1 < layout.lineCount &&
                layout.getLineBottom(endLine + 1) - top <= contentHeight
            ) {
                endLine++
            }
            val startOffset = layout.getLineStart(startLine)
            val endOffset = layout.getLineEnd(endLine).coerceAtLeast(startOffset)
            result.add(PageRange(startLine, endLine, startOffset, endOffset))
            startLine = endLine + 1
        }
        if (result.isEmpty()) {
            result.add(PageRange(0, 0, 0, chapterText.length))
        }
        pages = result
        pageCount = result.size
        if (currentPage > pageCount - 1) currentPage = (pageCount - 1).coerceAtLeast(0)
    }

    private fun currentPageRange(): PageRange? = pages.getOrNull(currentPage)

    private fun pageIndexForOffset(offset: Int): Int {
        if (pages.isEmpty()) return 0
        var index = 0
        pages.forEachIndexed { i, page ->
            if (page.startOffset <= offset) index = i
        }
        return index.coerceIn(0, pages.lastIndex)
    }

    private fun notifyPageChanged() {
        val page = currentPage
        val count = pageCount.coerceAtLeast(1)
        post { onPageChanged?.invoke(page, count) }
    }

    override fun onDraw(canvas: Canvas) {
        drawBackground(canvas)
        val layout = textLayout
        if (layout == null || pages.isEmpty()) {
            drawChrome(canvas)
            return
        }

        if (dragOffset != 0f) {
            drawPage(canvas, currentPage, dragOffset)
            if (dragOffset < 0f) {
                drawAdjacent(canvas, currentPage + 1, width + dragOffset)
            } else {
                drawAdjacent(canvas, currentPage - 1, dragOffset - width)
            }
        } else {
            drawPage(canvas, currentPage, 0f)
        }
        drawChrome(canvas)
    }

    private fun drawBackground(canvas: Canvas) {
        val shader = backgroundShader ?: LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            style.backgroundColor,
            style.backgroundColorAlt,
            Shader.TileMode.CLAMP
        ).also { backgroundShader = it }
        backgroundPaint.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
    }

    private fun drawPage(canvas: Canvas, pageIndex: Int, xOffset: Float) {
        val layout = textLayout ?: return
        val page = pages.getOrNull(pageIndex) ?: return
        // The available height can include the top slice of the line after
        // endLine. Clip to the last line of this page as well, otherwise that
        // line is visible at the bottom here and repeated at the top of the
        // next page.
        val pageBottom = contentTop + (
            layout.getLineBottom(page.endLine) - layout.getLineTop(page.startLine)
            )
        canvas.save()
        canvas.clipRect(
            contentLeft + xOffset,
            contentTop,
            contentRight + xOffset,
            pageBottom.coerceAtMost(contentBottom)
        )
        canvas.translate(contentLeft + xOffset, contentTop - layout.getLineTop(page.startLine))
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawAdjacent(canvas: Canvas, pageIndex: Int, xOffset: Float) {
        if (pageIndex in pages.indices) {
            drawPage(canvas, pageIndex, xOffset)
            return
        }
        if (pageIndex < 0) {
            drawChapterPlaceholder(canvas, xOffset, "上一章", hasPreviousChapter())
        } else {
            drawChapterPlaceholder(canvas, xOffset, "下一章", hasNextChapter())
        }
    }

    private fun drawChapterPlaceholder(
        canvas: Canvas,
        xOffset: Float,
        label: String,
        available: Boolean
    ) {
        if (!available) return
        val centerX = width / 2f + xOffset
        val centerY = (contentTop + contentBottom) / 2f
        placeholderPaint.alpha = 210
        placeholderPaint.textSize = 17f * scaledDensity
        canvas.drawText(label, centerX, centerY - 6f * density, placeholderPaint)
        placeholderPaint.alpha = 110
        placeholderPaint.textSize = 12f * scaledDensity
        canvas.drawText(
            if (label == "上一章") "向左滑动返回上一章" else "继续滑动进入下一章",
            centerX,
            centerY + 22f * density,
            placeholderPaint
        )
        placeholderPaint.textSize = 17f * scaledDensity
    }

    private fun drawChrome(canvas: Canvas) {
        if (!chromeVisible || contentWidth <= 0) return
        val titleY = style.statusBarInsetPx + 42f * density
        val half = (contentWidth * 0.48f).toInt()
        drawEllipsized(canvas, style.bookTitle, contentLeft, titleY, half, headerPaint)
        val chapterWidth = (contentWidth * 0.5f).toInt()
        val chapterLabel = TextUtils.ellipsize(
            style.chapterTitle,
            headerPaint,
            chapterWidth.toFloat(),
            TextUtils.TruncateAt.END
        )
        canvas.drawText(
            chapterLabel,
            0,
            chapterLabel.length,
            contentRight - headerPaint.measureText(chapterLabel.toString()),
            titleY,
            headerPaint
        )

        canvas.drawLine(
            contentLeft,
            contentTop - 15f * density,
            contentRight,
            contentTop - 15f * density,
            chromeLinePaint
        )
    }

    private fun drawEllipsized(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        maxWidth: Int,
        paint: TextPaint
    ) {
        if (text.isEmpty() || maxWidth <= 0) return
        val ellipsized = TextUtils.ellipsize(
            text,
            paint,
            maxWidth.toFloat(),
            TextUtils.TruncateAt.END
        )
        canvas.drawText(ellipsized, 0, ellipsized.length, x, y, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                velocityTracker.clear()
                velocityTracker.addMovement(event)
                downX = event.x
                downY = event.y
                dragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker.addMovement(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                if (dragging && style.pageAnimation) {
                    dragOffset = dx.coerceIn(-width.toFloat(), width.toFloat())
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                velocityTracker.addMovement(event)
                velocityTracker.computeCurrentVelocity(1000)
                val velocityX = velocityTracker.xVelocity
                velocityTracker.clear()
                val dx = event.x - downX
                val dy = event.y - downY
                if (dragging) {
                    handleSwipe(dx, velocityX)
                } else if (abs(dx) < touchSlop && abs(dy) < touchSlop) {
                    performClick()
                    handleTap(event.x)
                }
                dragging = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                velocityTracker.clear()
                dragging = false
                animateOffsetTo(0f) { }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun handleSwipe(dx: Float, velocityX: Float) {
        val threshold = width * swipeThresholdFraction
        val flungLeft = velocityX < -minFlingVelocity
        val flungRight = velocityX > minFlingVelocity
        if (style.pageAnimation) {
            when {
                dragOffset <= -threshold || (flungLeft && dragOffset < 0f) ->
                    animateOffsetTo(-width.toFloat()) { turnNext() }
                dragOffset >= threshold || (flungRight && dragOffset > 0f) ->
                    animateOffsetTo(width.toFloat()) { turnPrevious() }
                else -> animateOffsetTo(0f) { }
            }
        } else {
            when {
                dx <= -threshold || (flungLeft && dx < 0f) -> turnNext()
                dx >= threshold || (flungRight && dx > 0f) -> turnPrevious()
                else -> Unit
            }
        }
    }

    private fun animateOffsetTo(target: Float, onEnd: () -> Unit) {
        animator?.cancel()
        if (dragOffset == target) {
            onEnd()
            return
        }
        animator = ValueAnimator.ofFloat(dragOffset, target).apply {
            duration = 210L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                dragOffset = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (cancelled) return
                    if (target == 0f) {
                        dragOffset = 0f
                        invalidate()
                    }
                    onEnd()
                }
            })
            start()
        }
    }

    private fun handleTap(x: Float) {
        when {
            x < width * 0.3f -> turnPrevious()
            x > width * 0.7f -> turnNext()
            else -> onCenterTap?.invoke()
        }
    }

    private fun turnNext() {
        dragOffset = 0f
        if (currentPage < pages.lastIndex) {
            currentPage++
            invalidate()
            notifyPageChanged()
        } else if (hasNextChapter()) {
            onNextChapter?.invoke()
        } else {
            invalidate()
        }
    }

    private fun turnPrevious() {
        dragOffset = 0f
        if (currentPage > 0) {
            currentPage--
            invalidate()
            notifyPageChanged()
        } else if (hasPreviousChapter()) {
            onPreviousChapter?.invoke()
        } else {
            invalidate()
        }
    }

    private fun hasNextChapter(): Boolean = style.chapterIndex < style.totalChapters - 1

    private fun hasPreviousChapter(): Boolean = style.chapterIndex > 0
}
