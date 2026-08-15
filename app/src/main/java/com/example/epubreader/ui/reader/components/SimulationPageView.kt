package com.example.epubreader.ui.reader.components

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.widget.Scroller
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.epubreader.ui.reader.PageElement
import com.example.epubreader.ui.reader.ReaderPage
import java.io.File
import kotlin.math.*

/**
 * 经典高保真 Android 拟真翻页组件（基于业界标准 120Hz 双缓冲贝塞尔引擎）
 * 包含完整的左侧/右侧双向独立真实物理纸张翻折动画
 */
@Composable
fun SimulationPageView(
    pages: List<ReaderPage>,
    currentPageIndex: Int,
    onPageChanged: (Int) -> Unit,
    onToggleToolbars: () -> Unit,
    onOpenToc: () -> Unit,
    bgColor: androidx.compose.ui.graphics.Color,
    textColor: androidx.compose.ui.graphics.Color,
    secondaryTextColor: androidx.compose.ui.graphics.Color,
    textSize: Float,
    lineHeightMult: Float,
    paragraphSpacing: Float,
    customFontFamily: FontFamily?,
    customFontUri: String? = null,
    bookTitle: String,
    topPadding: androidx.compose.ui.unit.Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val topPaddingPx = with(density) { topPadding.toPx() }
    val safeCurrent = currentPageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))

    AndroidView(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor),
        factory = { ctx ->
            SimulationAnimView(ctx).apply {
                this.onPageChangedListener = onPageChanged
                this.onToggleToolbarsListener = onToggleToolbars
            }
        },
        update = { view ->
            view.updateData(
                pages = pages,
                currentPage = safeCurrent,
                bgColor = bgColor.toArgb(),
                textColor = textColor.toArgb(),
                secondaryTextColor = secondaryTextColor.toArgb(),
                textSize = textSize,
                lineHeightMult = lineHeightMult,
                paragraphSpacing = paragraphSpacing,
                customFontUri = customFontUri,
                bookTitle = bookTitle,
                topPaddingPx = topPaddingPx
            )
        }
    )
}

class SimulationAnimView(context: Context) : View(context) {

    var onPageChangedListener: ((Int) -> Unit)? = null
    var onToggleToolbarsListener: (() -> Unit)? = null

    private var mScreenWidth = 0
    private var mScreenHeight = 0

    private var mTouchX = 0.01f
    private var mTouchY = 0.01f
    private var mStartX = 0f
    private var mStartY = 0f

    private var mCornerX = 1
    private var mCornerY = 1

    private val mPath0 = Path()
    private val mPath1 = Path()
    private val mXORPath = Path()

    private val mBezierStart1 = PointF()
    private val mBezierControl1 = PointF()
    private val mBeziervertex1 = PointF()
    private var mBezierEnd1 = PointF()

    private val mBezierStart2 = PointF()
    private val mBezierControl2 = PointF()
    private val mBeziervertex2 = PointF()
    private var mBezierEnd2 = PointF()

    private var mMiddleX = 0f
    private var mMiddleY = 0f
    private var mDegrees = 0f
    private var mTouchToCornerDis = 0f

    private var mColorMatrixFilter: ColorMatrixColorFilter? = null
    private val mMatrix = Matrix()
    private val mMatrixArray = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1.0f)

    private var mIsRTandLB = false
    private var mMaxLength = 0f

    private var mBackShadowDrawableLR: GradientDrawable? = null
    private var mBackShadowDrawableRL: GradientDrawable? = null
    private var mFolderShadowDrawableLR: GradientDrawable? = null
    private var mFolderShadowDrawableRL: GradientDrawable? = null
    private var mFrontShadowDrawableHBT: GradientDrawable? = null
    private var mFrontShadowDrawableHTB: GradientDrawable? = null
    private var mFrontShadowDrawableVLR: GradientDrawable? = null
    private var mFrontShadowDrawableVRL: GradientDrawable? = null

    private val mPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val mScroller = Scroller(context)
    private var mVelocityTracker: VelocityTracker? = null

    enum class Direction { NONE, PREV, NEXT }
    private var mDirection = Direction.NONE
    private var mIsCancel = false
    private var mIsRunningAnim = false

    // Book Data
    private var mPages: List<ReaderPage> = emptyList()
    private var mCurrentPage = 0
    private var mBgColor = android.graphics.Color.WHITE
    private var mTextColor = android.graphics.Color.BLACK
    private var mSecondaryTextColor = android.graphics.Color.GRAY
    private var mTextSize = 18f
    private var mLineHeightMult = 1.6f
    private var mParagraphSpacing = 12f
    private var mCustomFontUri: String? = null
    private var mBookTitle = ""
    private var mTypeface: Typeface = Typeface.DEFAULT
    private var mTopPaddingPx = 0f

    // Hardware Bitmaps
    private var mCurBitmap: Bitmap? = null
    private var mNextBitmap: Bitmap? = null
    private var mPrevBitmap: Bitmap? = null

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        createDrawable()
        val cm = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        mColorMatrixFilter = ColorMatrixColorFilter(cm)
    }

    private fun createDrawable() {
        val color = intArrayOf(0x333333, 0xb0333333.toInt())
        mFolderShadowDrawableRL = GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, color).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
        mFolderShadowDrawableLR = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, color).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }

        val backShadowColors = intArrayOf(0xff111111.toInt(), 0x111111)
        mBackShadowDrawableRL = GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, backShadowColors).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
        mBackShadowDrawableLR = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, backShadowColors).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }

        val frontShadowColors = intArrayOf(0x80111111.toInt(), 0x111111)
        mFrontShadowDrawableVLR = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, frontShadowColors).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
        mFrontShadowDrawableVRL = GradientDrawable(GradientDrawable.Orientation.RIGHT_LEFT, frontShadowColors).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
        mFrontShadowDrawableHTB = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, frontShadowColors).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
        mFrontShadowDrawableHBT = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, frontShadowColors).apply {
            gradientType = GradientDrawable.LINEAR_GRADIENT
        }
    }

    fun updateData(
        pages: List<ReaderPage>,
        currentPage: Int,
        bgColor: Int,
        textColor: Int,
        secondaryTextColor: Int,
        textSize: Float,
        lineHeightMult: Float,
        paragraphSpacing: Float,
        customFontUri: String?,
        bookTitle: String,
        topPaddingPx: Float = 0f
    ) {
        val fontChanged = mCustomFontUri != customFontUri
        if (fontChanged) {
            mCustomFontUri = customFontUri
            mTypeface = if (!customFontUri.isNullOrEmpty() && File(customFontUri).exists()) {
                try {
                    Typeface.createFromFile(customFontUri)
                } catch (e: Exception) {
                    Typeface.DEFAULT
                }
            } else {
                Typeface.DEFAULT
            }
        }

        val needsRedraw = mPages != pages || mCurrentPage != currentPage || mBgColor != bgColor ||
                mTextColor != textColor || mTextSize != textSize || mLineHeightMult != lineHeightMult || fontChanged ||
                mTopPaddingPx != topPaddingPx

        this.mPages = pages
        this.mCurrentPage = currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
        this.mBgColor = bgColor
        this.mTextColor = textColor
        this.mSecondaryTextColor = secondaryTextColor
        this.mTextSize = textSize
        this.mLineHeightMult = lineHeightMult
        this.mParagraphSpacing = paragraphSpacing
        this.mBookTitle = bookTitle
        this.mTopPaddingPx = topPaddingPx

        if (needsRedraw && mScreenWidth > 0 && mScreenHeight > 0) {
            prepareBitmaps()
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mScreenWidth = w
        mScreenHeight = h
        mMaxLength = hypot(w.toFloat(), h.toFloat())
        prepareBitmaps()
    }

    private fun prepareBitmaps() {
        if (mScreenWidth <= 0 || mScreenHeight <= 0 || mPages.isEmpty()) return

        if (mCurBitmap == null || mCurBitmap?.width != mScreenWidth || mCurBitmap?.height != mScreenHeight) {
            mCurBitmap = Bitmap.createBitmap(mScreenWidth, mScreenHeight, Bitmap.Config.ARGB_8888)
            mNextBitmap = Bitmap.createBitmap(mScreenWidth, mScreenHeight, Bitmap.Config.ARGB_8888)
            mPrevBitmap = Bitmap.createBitmap(mScreenWidth, mScreenHeight, Bitmap.Config.ARGB_8888)
        }

        mPages.getOrNull(mCurrentPage)?.let { page ->
            mCurBitmap?.let { renderPageToBitmap(page, it) }
        }
        mPages.getOrNull(mCurrentPage + 1)?.let { page ->
            mNextBitmap?.let { renderPageToBitmap(page, it) }
        }
        mPages.getOrNull(mCurrentPage - 1)?.let { page ->
            mPrevBitmap?.let { renderPageToBitmap(page, it) }
        }
    }

    private fun renderPageToBitmap(page: ReaderPage, bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        canvas.drawColor(mBgColor)

        val density = resources.displayMetrics.density
        val scaledDensity = resources.displayMetrics.scaledDensity
        val paddingLeft = 18f * density
        val paddingRight = 18f * density
        val paddingTop = if (mTopPaddingPx > 0f) mTopPaddingPx else 20f * density
        val paddingBottom = 20f * density
        val contentWidth = (mScreenWidth - paddingLeft - paddingRight).toInt().coerceAtLeast(100)

        var curY = paddingTop

        val bodyFontSizePx = mTextSize * scaledDensity
        val bodyLineHeightPx = (mTextSize * mLineHeightMult).coerceAtLeast(mTextSize * 1.15f) * scaledDensity
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mTextColor
            textSize = bodyFontSizePx
            typeface = mTypeface
        }
        val fontMetrics = textPaint.fontMetrics
        val fontHeight = fontMetrics.descent - fontMetrics.ascent
        val extraSpacing = bodyLineHeightPx - fontHeight

        val titleFontSizePx = (mTextSize * 1.32f) * scaledDensity
        val titleLineHeightPx = (mTextSize * 1.32f * 1.32f) * scaledDensity
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mTextColor
            textSize = titleFontSizePx
            typeface = Typeface.create(mTypeface, Typeface.BOLD)
        }
        val titleFontMetrics = titlePaint.fontMetrics
        val titleFontHeight = titleFontMetrics.descent - titleFontMetrics.ascent
        val titleExtraSpacing = titleLineHeightPx - titleFontHeight

        for (element in page.elements) {
            when (element) {
                is PageElement.Title -> {
                    val layout = StaticLayout.Builder.obtain(
                        element.title, 0, element.title.length, titlePaint, contentWidth
                    ).setLineSpacing(titleExtraSpacing, 1.0f)
                     .setIncludePad(false)
                     .build()

                    curY += (4f * density)
                    canvas.save()
                    canvas.translate(paddingLeft, curY)
                    layout.draw(canvas)
                    canvas.restore()
                    curY += layout.height + (12f * density)
                }
                is PageElement.Paragraph -> {
                    val displayText = if (!element.isContinuation && !element.text.text.startsWith("　") && !element.text.text.startsWith("  ")) {
                        "　　" + element.text.text
                    } else {
                        element.text.text
                    }

                    val layout = StaticLayout.Builder.obtain(
                        displayText, 0, displayText.length, textPaint, contentWidth
                    ).setLineSpacing(extraSpacing, 1.0f)
                     .setIncludePad(false)
                     .build()

                    canvas.save()
                    canvas.translate(paddingLeft, curY)
                    layout.draw(canvas)
                    canvas.restore()
                    curY += layout.height + (mParagraphSpacing * density)
                }
                is PageElement.Image -> {
                    val bmp = element.bitmap?.asAndroidBitmap()
                    if (bmp != null) {
                        val destWidth = contentWidth.toFloat()
                        val destHeight = destWidth * (bmp.height.toFloat() / bmp.width.toFloat().coerceAtLeast(1f))
                        val rect = RectF(paddingLeft, curY, paddingLeft + destWidth, curY + destHeight)
                        canvas.drawBitmap(bmp, null, rect, mPaint)
                        curY += destHeight + (12f * density)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mPages.isEmpty()) return super.onTouchEvent(event)

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain()
        }
        mVelocityTracker?.addMovement(event)

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mStartX = x
                mStartY = y
                mTouchX = x
                mTouchY = y
                mDirection = Direction.NONE
                mIsRunningAnim = false

                if (!mScroller.isFinished) {
                    mScroller.abortAnimation()
                }
                calcCornerXY(x, y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = x - mStartX
                val dy = y - mStartY

                if (mDirection == Direction.NONE && (abs(dx) > 10 || abs(dy) > 10)) {
                    if (dx < 0 && mCurrentPage < mPages.size - 1) {
                        mDirection = Direction.NEXT
                        calcCornerXY(mScreenWidth.toFloat(), y)
                    } else if (dx > 0 && mCurrentPage > 0) {
                        mDirection = Direction.PREV
                        calcCornerXY(0f, y) // Set origin corner to left side for true previous page peel
                    } else {
                        return true
                    }
                }

                if (mDirection != Direction.NONE) {
                    mTouchX = x
                    mTouchY = y

                    // Middle Y touch adjustment for smooth vertical peel
                    if (mStartY > mScreenHeight / 3 && mStartY < mScreenHeight * 2 / 3) {
                        mTouchY = mScreenHeight.toFloat()
                    }
                    if (mTouchX <= 0) mTouchX = 1f
                    if (mTouchX >= mScreenWidth) mTouchX = mScreenWidth - 1f

                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                mVelocityTracker?.computeCurrentVelocity(1000)
                val vx = mVelocityTracker?.xVelocity ?: 0f

                if (mDirection == Direction.NONE) {
                    // Tap detection
                    when {
                        x < mScreenWidth * 0.28f -> {
                            if (mCurrentPage > 0) {
                                startAutoTurn(false)
                            }
                        }
                        x > mScreenWidth * 0.72f -> {
                            if (mCurrentPage < mPages.size - 1) {
                                startAutoTurn(true)
                            }
                        }
                        else -> {
                            onToggleToolbarsListener?.invoke()
                        }
                    }
                } else {
                    // Drag release
                    if (mDirection == Direction.NEXT) {
                        mIsCancel = !(mTouchX < mScreenWidth * 0.65f || vx < -400f)
                    } else {
                        mIsCancel = !(mTouchX > mScreenWidth * 0.35f || vx > 400f)
                    }
                    startAnim()
                }

                mVelocityTracker?.recycle()
                mVelocityTracker = null
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startAutoTurn(isNext: Boolean) {
        mDirection = if (isNext) Direction.NEXT else Direction.PREV
        mIsCancel = false
        if (isNext) {
            mStartX = mScreenWidth.toFloat() - 10f
            mStartY = mScreenHeight.toFloat() - 10f
            mTouchX = mStartX
            mTouchY = mStartY
            calcCornerXY(mScreenWidth.toFloat(), mStartY)
        } else {
            mStartX = 10f
            mStartY = mScreenHeight.toFloat() - 10f
            mTouchX = mStartX
            mTouchY = mStartY
            calcCornerXY(0f, mStartY)
        }
        startAnim()
    }

    private fun startAnim() {
        mIsRunningAnim = true
        var dx: Int
        var dy: Int

        if (mIsCancel) {
            if (mDirection == Direction.NEXT) {
                dx = if (mCornerX > 0) (mScreenWidth - mTouchX).toInt() else -mTouchX.toInt()
            } else {
                dx = -mTouchX.toInt()
            }
            dy = if (mCornerY > 0) {
                (mScreenHeight - mTouchY).toInt()
            } else {
                -mTouchY.toInt()
            }
        } else {
            if (mDirection == Direction.NEXT) {
                dx = if (mCornerX > 0) -(mScreenWidth + mTouchX).toInt() else (mScreenWidth - mTouchX + mScreenWidth).toInt()
            } else {
                // For PREV from left corner (0), animate touch point all the way to right side
                dx = if (mCornerX == 0) (mScreenWidth - mTouchX + mScreenWidth).toInt() else -(mScreenWidth + mTouchX).toInt()
            }
            dy = if (mCornerY > 0) {
                (mScreenHeight - mTouchY).toInt()
            } else {
                (1 - mTouchY).toInt()
            }
        }

        mScroller.startScroll(mTouchX.toInt(), mTouchY.toInt(), dx, dy, 320)
        invalidate()
    }

    override fun computeScroll() {
        if (mScroller.computeScrollOffset()) {
            mTouchX = mScroller.currX.toFloat()
            mTouchY = mScroller.currY.toFloat()
            invalidate()

            if (mScroller.isFinished) {
                mIsRunningAnim = false
                if (!mIsCancel) {
                    if (mDirection == Direction.NEXT && mCurrentPage < mPages.size - 1) {
                        mCurrentPage++
                        mPrevBitmap = mCurBitmap
                        mCurBitmap = mNextBitmap
                        mNextBitmap = Bitmap.createBitmap(mScreenWidth, mScreenHeight, Bitmap.Config.ARGB_8888)
                        mPages.getOrNull(mCurrentPage + 1)?.let { page ->
                            mNextBitmap?.let { renderPageToBitmap(page, it) }
                        }
                        onPageChangedListener?.invoke(mCurrentPage)
                    } else if (mDirection == Direction.PREV && mCurrentPage > 0) {
                        mCurrentPage--
                        mNextBitmap = mCurBitmap
                        mCurBitmap = mPrevBitmap
                        mPrevBitmap = Bitmap.createBitmap(mScreenWidth, mScreenHeight, Bitmap.Config.ARGB_8888)
                        mPages.getOrNull(mCurrentPage - 1)?.let { page ->
                            mPrevBitmap?.let { renderPageToBitmap(page, it) }
                        }
                        onPageChangedListener?.invoke(mCurrentPage)
                    }
                }
                mDirection = Direction.NONE
                mTouchX = 0.01f
                mTouchY = 0.01f
                invalidate()
            }
        }
    }

    private fun calcCornerXY(x: Float, y: Float) {
        mCornerX = if (x <= mScreenWidth / 2) 0 else mScreenWidth
        mCornerY = if (y <= mScreenHeight / 2) 0 else mScreenHeight
        mIsRTandLB = (mCornerX == 0 && mCornerY == mScreenHeight) || (mCornerX == mScreenWidth && mCornerY == 0)
    }

    private fun calcPoints() {
        mMiddleX = (mTouchX + mCornerX) / 2
        mMiddleY = (mTouchY + mCornerY) / 2
        mBezierControl1.x = mMiddleX - (mCornerY - mMiddleY) * (mCornerY - mMiddleY) / (mCornerX - mMiddleX)
        mBezierControl1.y = mCornerY.toFloat()
        mBezierControl2.x = mCornerX.toFloat()

        val f4 = mCornerY - mMiddleY
        mBezierControl2.y = if (f4 == 0f) {
            mMiddleY - (mCornerX - mMiddleX) * (mCornerX - mMiddleX) / 0.1f
        } else {
            mMiddleY - (mCornerX - mMiddleX) * (mCornerX - mMiddleX) / (mCornerY - mMiddleY)
        }
        mBezierStart1.x = mBezierControl1.x - (mCornerX - mBezierControl1.x) / 2
        mBezierStart1.y = mCornerY.toFloat()

        if (mTouchX > 0 && mTouchX < mScreenWidth) {
            if (mBezierStart1.x < 0 || mBezierStart1.x > mScreenWidth) {
                if (mBezierStart1.x < 0) {
                    mBezierStart1.x = mScreenWidth - mBezierStart1.x
                }
                val f1 = abs(mCornerX - mTouchX)
                val f2 = mScreenWidth * f1 / mBezierStart1.x
                mTouchX = abs(mCornerX - f2)

                val f3 = abs(mCornerX - mTouchX) * abs(mCornerY - mTouchY) / f1
                mTouchY = abs(mCornerY - f3)

                mMiddleX = (mTouchX + mCornerX) / 2
                mMiddleY = (mTouchY + mCornerY) / 2

                mBezierControl1.x = mMiddleX - (mCornerY - mMiddleY) * (mCornerY - mMiddleY) / (mCornerX - mMiddleX)
                mBezierControl1.y = mCornerY.toFloat()
                mBezierControl2.x = mCornerX.toFloat()

                val f5 = mCornerY - mMiddleY
                mBezierControl2.y = if (f5 == 0f) {
                    mMiddleY - (mCornerX - mMiddleX) * (mCornerX - mMiddleX) / 0.1f
                } else {
                    mMiddleY - (mCornerX - mMiddleX) * (mCornerX - mMiddleX) / (mCornerY - mMiddleY)
                }
                mBezierStart1.x = mBezierControl1.x - (mCornerX - mBezierControl1.x) / 2
            }
        }
        mBezierStart2.x = mCornerX.toFloat()
        mBezierStart2.y = mBezierControl2.y - (mCornerY - mBezierControl2.y) / 2

        mTouchToCornerDis = hypot((mTouchX - mCornerX), (mTouchY - mCornerY))

        mBezierEnd1 = getCross(PointF(mTouchX, mTouchY), mBezierControl1, mBezierStart1, mBezierStart2)
        mBezierEnd2 = getCross(PointF(mTouchX, mTouchY), mBezierControl2, mBezierStart1, mBezierStart2)

        mBeziervertex1.x = (mBezierStart1.x + 2 * mBezierControl1.x + mBezierEnd1.x) / 4
        mBeziervertex1.y = (2 * mBezierControl1.y + mBezierStart1.y + mBezierEnd1.y) / 4
        mBeziervertex2.x = (mBezierStart2.x + 2 * mBezierControl2.x + mBezierEnd2.x) / 4
        mBeziervertex2.y = (2 * mBezierControl2.y + mBezierStart2.y + mBezierEnd2.y) / 4
    }

    private fun getCross(P1: PointF, P2: PointF, P3: PointF, P4: PointF): PointF {
        val crossP = PointF()
        val a1 = (P2.y - P1.y) / (P2.x - P1.x)
        val b1 = ((P1.x * P2.y) - (P2.x * P1.y)) / (P1.x - P2.x)

        val a2 = (P4.y - P3.y) / (P4.x - P3.x)
        val b2 = ((P3.x * P4.y) - (P4.x * P3.y)) / (P3.x - P4.x)
        crossP.x = (b2 - b1) / (a1 - a2)
        crossP.y = a1 * crossP.x + b1
        return crossP
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mScreenWidth <= 0 || mScreenHeight <= 0 || mPages.isEmpty()) return

        canvas.drawColor(mBgColor)

        val curBmp = mCurBitmap ?: return
        val nextBmp = mNextBitmap ?: curBmp
        val prevBmp = mPrevBitmap ?: curBmp

        if (mDirection == Direction.NONE && !mIsRunningAnim) {
            canvas.drawBitmap(curBmp, 0f, 0f, null)
            return
        }

        calcPoints()

        when (mDirection) {
            Direction.NEXT -> {
                drawCurrentPageArea(canvas, curBmp, mPath0)
                drawNextPageAreaAndShadow(canvas, nextBmp)
                drawCurrentPageShadow(canvas)
                drawCurrentBackArea(canvas, curBmp)
            }
            Direction.PREV -> {
                // The flat main screen remains current page (curBmp), peeling corner unfolding from left reveals previous page (prevBmp)
                drawCurrentPageArea(canvas, curBmp, mPath0)
                drawNextPageAreaAndShadow(canvas, prevBmp)
                drawCurrentPageShadow(canvas)
                drawCurrentBackArea(canvas, prevBmp)
            }
            else -> {
                canvas.drawBitmap(curBmp, 0f, 0f, null)
            }
        }
    }

    private fun drawCurrentPageArea(canvas: Canvas, bitmap: Bitmap, path: Path) {
        mPath0.reset()
        mPath0.moveTo(mBezierStart1.x, mBezierStart1.y)
        mPath0.quadTo(mBezierControl1.x, mBezierControl1.y, mBezierEnd1.x, mBezierEnd1.y)
        mPath0.lineTo(mTouchX, mTouchY)
        mPath0.lineTo(mBezierEnd2.x, mBezierEnd2.y)
        mPath0.quadTo(mBezierControl2.x, mBezierControl2.y, mBezierStart2.x, mBezierStart2.y)
        mPath0.lineTo(mCornerX.toFloat(), mCornerY.toFloat())
        mPath0.close()

        canvas.save()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            mXORPath.reset()
            mXORPath.moveTo(0f, 0f)
            mXORPath.lineTo(canvas.width.toFloat(), 0f)
            mXORPath.lineTo(canvas.width.toFloat(), canvas.height.toFloat())
            mXORPath.lineTo(0f, canvas.height.toFloat())
            mXORPath.close()

            mXORPath.op(path, Path.Op.XOR)
            canvas.clipPath(mXORPath)
        } else {
            canvas.clipPath(path, Region.Op.XOR)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        try {
            canvas.restore()
        } catch (e: Exception) {}
    }

    private fun drawNextPageAreaAndShadow(canvas: Canvas, bitmap: Bitmap) {
        mPath1.reset()
        mPath1.moveTo(mBezierStart1.x, mBezierStart1.y)
        mPath1.lineTo(mBeziervertex1.x, mBeziervertex1.y)
        mPath1.lineTo(mBeziervertex2.x, mBeziervertex2.y)
        mPath1.lineTo(mBezierStart2.x, mBezierStart2.y)
        mPath1.lineTo(mCornerX.toFloat(), mCornerY.toFloat())
        mPath1.close()

        mDegrees = Math.toDegrees(atan2((mBezierControl1.x - mCornerX).toDouble(), (mBezierControl2.y - mCornerY).toDouble())).toFloat()
        val leftx: Int
        val rightx: Int
        val backShadowDrawable: GradientDrawable?
        if (mIsRTandLB) {
            leftx = mBezierStart1.x.toInt()
            rightx = (mBezierStart1.x + mTouchToCornerDis / 4).toInt()
            backShadowDrawable = mBackShadowDrawableLR
        } else {
            leftx = (mBezierStart1.x - mTouchToCornerDis / 4).toInt()
            rightx = mBezierStart1.x.toInt()
            backShadowDrawable = mBackShadowDrawableRL
        }
        canvas.save()
        try {
            canvas.clipPath(mPath0)
            canvas.clipPath(mPath1, Region.Op.INTERSECT)
        } catch (e: Exception) {}

        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.rotate(mDegrees, mBezierStart1.x, mBezierStart1.y)
        backShadowDrawable?.setBounds(leftx, mBezierStart1.y.toInt(), rightx, (mMaxLength + mBezierStart1.y).toInt())
        backShadowDrawable?.draw(canvas)
        canvas.restore()
    }

    private fun drawCurrentPageShadow(canvas: Canvas) {
        val degree = if (mIsRTandLB) {
            Math.PI / 4 - atan2((mBezierControl1.y - mTouchY).toDouble(), (mTouchX - mBezierControl1.x).toDouble())
        } else {
            Math.PI / 4 - atan2((mTouchY - mBezierControl1.y).toDouble(), (mTouchX - mBezierControl1.x).toDouble())
        }
        val d1 = (25f * 1.414f * cos(degree)).toFloat()
        val d2 = (25f * 1.414f * sin(degree)).toFloat()
        val x = mTouchX + d1
        val y = if (mIsRTandLB) mTouchY + d2 else mTouchY - d2

        mPath1.reset()
        mPath1.moveTo(x, y)
        mPath1.lineTo(mTouchX, mTouchY)
        mPath1.lineTo(mBezierControl1.x, mBezierControl1.y)
        mPath1.lineTo(mBezierStart1.x, mBezierStart1.y)
        mPath1.close()

        canvas.save()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mXORPath.reset()
                mXORPath.moveTo(0f, 0f)
                mXORPath.lineTo(canvas.width.toFloat(), 0f)
                mXORPath.lineTo(canvas.width.toFloat(), canvas.height.toFloat())
                mXORPath.lineTo(0f, canvas.height.toFloat())
                mXORPath.close()
                mXORPath.op(mPath0, Path.Op.XOR)
                canvas.clipPath(mXORPath)
            } else {
                canvas.clipPath(mPath0, Region.Op.XOR)
            }
            canvas.clipPath(mPath1, Region.Op.INTERSECT)
        } catch (e: Exception) {}

        val leftx: Int
        val rightx: Int
        val currentPageShadow: GradientDrawable?
        if (mIsRTandLB) {
            leftx = mBezierControl1.x.toInt()
            rightx = (mBezierControl1.x + 25).toInt()
            currentPageShadow = mFrontShadowDrawableVLR
        } else {
            leftx = (mBezierControl1.x - 25).toInt()
            rightx = (mBezierControl1.x + 1).toInt()
            currentPageShadow = mFrontShadowDrawableVRL
        }

        val rotateDegrees = Math.toDegrees(atan2((mTouchX - mBezierControl1.x).toDouble(), (mBezierControl1.y - mTouchY).toDouble())).toFloat()
        canvas.rotate(rotateDegrees, mBezierControl1.x, mBezierControl1.y)
        currentPageShadow?.setBounds(leftx, (mBezierControl1.y - mMaxLength).toInt(), rightx, mBezierControl1.y.toInt())
        currentPageShadow?.draw(canvas)
        canvas.restore()
    }

    private fun drawCurrentBackArea(canvas: Canvas, bitmap: Bitmap) {
        val i = (mBezierStart1.x + mBezierControl1.x).toInt() / 2
        val f1 = abs(i - mBezierControl1.x)
        val i1 = (mBezierStart2.y + mBezierControl2.y).toInt() / 2
        val f2 = abs(i1 - mBezierControl2.y)
        val f3 = min(f1, f2)

        mPath1.reset()
        mPath1.moveTo(mBeziervertex2.x, mBeziervertex2.y)
        mPath1.lineTo(mBeziervertex1.x, mBeziervertex1.y)
        mPath1.lineTo(mBezierEnd1.x, mBezierEnd1.y)
        mPath1.lineTo(mTouchX, mTouchY)
        mPath1.lineTo(mBezierEnd2.x, mBezierEnd2.y)
        mPath1.close()

        val folderShadowDrawable: GradientDrawable?
        val left: Int
        val right: Int
        if (mIsRTandLB) {
            left = (mBezierStart1.x - 1).toInt()
            right = (mBezierStart1.x + f3 + 1).toInt()
            folderShadowDrawable = mFolderShadowDrawableLR
        } else {
            left = (mBezierStart1.x - f3 - 1).toInt()
            right = (mBezierStart1.x + 1).toInt()
            folderShadowDrawable = mFolderShadowDrawableRL
        }

        canvas.save()
        try {
            canvas.clipPath(mPath0)
            canvas.clipPath(mPath1, Region.Op.INTERSECT)
        } catch (e: Exception) {}

        mPaint.colorFilter = mColorMatrixFilter
        val color = try {
            bitmap.getPixel(1.coerceAtMost(bitmap.width - 1), 1.coerceAtMost(bitmap.height - 1))
        } catch (e: Exception) {
            mBgColor
        }
        val red = (color and 0xff0000) shr 16
        val green = (color and 0x00ff00) shr 8
        val blue = (color and 0x0000ff)
        val tempColor = Color.argb(200, red, green, blue)

        val dis = hypot((mCornerX - mBezierControl1.x), (mBezierControl2.y - mCornerY))
        val f8 = (mCornerX - mBezierControl1.x) / dis
        val f9 = (mBezierControl2.y - mCornerY) / dis
        mMatrixArray[0] = 1 - 2 * f9 * f9
        mMatrixArray[1] = 2 * f8 * f9
        mMatrixArray[3] = mMatrixArray[1]
        mMatrixArray[4] = 1 - 2 * f8 * f8
        mMatrix.reset()
        mMatrix.setValues(mMatrixArray)
        mMatrix.preTranslate(-mBezierControl1.x, -mBezierControl1.y)
        mMatrix.postTranslate(mBezierControl1.x, mBezierControl1.y)

        canvas.drawBitmap(bitmap, mMatrix, mPaint)
        canvas.drawColor(tempColor)
        mPaint.colorFilter = null

        canvas.rotate(mDegrees, mBezierStart1.x, mBezierStart1.y)
        folderShadowDrawable?.setBounds(left, mBezierStart1.y.toInt(), right, (mBezierStart1.y + mMaxLength).toInt())
        folderShadowDrawable?.draw(canvas)
        canvas.restore()
    }
}
