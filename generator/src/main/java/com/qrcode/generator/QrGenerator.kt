package com.qrcode.generator

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.ByteMatrix
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.encoder.QRCode
import java.util.EnumMap

class QrGenerator {
    private val paint = Paint().also {
        it.style = Paint.Style.FILL
        it.isAntiAlias = true
    }

    private val encodingHints = EnumMap<EncodeHintType, Any?>(EncodeHintType::class.java).also {
        it[EncodeHintType.CHARACTER_SET] = UTF_8_ENCODING
    }

    fun generate(content: String, qrConfig: QRConfig): Bitmap? {
        val qrcode = encodeText(content, ErrorCorrectionLevel.H)
        val quiteZoneSize = (content.length * QUITE_ZONE_FACTOR).toInt()
        return generateQrInternal(qrcode, qrConfig, quiteZoneSize)
    }

    private fun encodeText(content: String, errorCorrectionLevel: ErrorCorrectionLevel): QRCode {
        return Encoder.encode(content, errorCorrectionLevel, encodingHints)
    }

    private fun generateQrInternal(qrcode: QRCode, config: QRConfig, quiteZoneSize: Int): Bitmap? {
        val input = qrcode.matrix ?: return null
        val bitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        paint.color = config.color
        val qrWidth = input.width + quiteZoneSize
        val qrHeight = input.height + quiteZoneSize
        val outputWidth = config.width.coerceAtLeast(qrWidth)
        val outputHeight = config.height.coerceAtLeast(qrHeight)
        val multipleReal = (outputWidth / qrWidth).coerceAtMost(
            maximumValue = outputHeight / qrHeight
        )
        val multipleActual = if (multipleReal % 2 == 0) multipleReal else multipleReal + 1
        val point = (multipleReal * SCALE_DOWN_FACTOR).toInt()
        val drawConfig = DrawConfig(
            topPadding = (outputHeight - input.height * multipleActual) / 2 + point,
            leftPadding = (outputWidth - input.width * multipleActual) / 2 + point,
            input = input,
            point = (multipleReal * SCALE_DOWN_FACTOR).toInt(),
            multipleActual = multipleActual
        )
        drawQrDots(canvas, drawConfig)
        drawQrFinders(canvas, drawConfig)
        return bitmap
    }

    private fun drawQrDots(canvas: Canvas, config: DrawConfig) {
        var inputY = 0
        var outputY = config.topPadding
        val rect = Rect()
        while (inputY < config.input.height) {
            var inputX = 0
            var outputX = config.leftPadding
            while (inputX < config.input.width) {
                if (config.input[inputX, inputY].toInt() == 1) {
                    val isInTopLeft = inputX <= FINDER_SIZE && inputY <= FINDER_SIZE
                    val isInTopRight = inputX >= config.input.width - FINDER_SIZE
                            && inputY <= FINDER_SIZE
                    val isInBottomLeft = inputX <= FINDER_SIZE
                            && inputY >= config.input.height - FINDER_SIZE
                    if (!(isInTopLeft || isInTopRight || isInBottomLeft)) {
                        rect.set(
                            outputX - config.point, outputY - config.point,
                            outputX + config.point, outputY + config.point,
                        )
                        canvas.drawRect(rect, paint)
                    }
                }
                inputX++
                outputX += config.multipleActual
            }
            inputY++
            outputY += config.multipleActual
        }
    }

    private fun drawQrFinders(canvas: Canvas, config: DrawConfig) {
        val finderPoint = 2 * config.point
        val topLeftConfig = RoundFinderDrawConfig(
            x = config.getTopLeftX(),
            y = config.getTopLeftY(),
            point = finderPoint,
            type = QRRoundFinder.TOP_LEFT
        )
        prepareRoundedFindersToDraw(canvas, topLeftConfig)

        val topRightConfig = topLeftConfig.copy(
            x = config.getTopRightX(),
            type = QRRoundFinder.TOP_RIGHT
        )
        prepareRoundedFindersToDraw(canvas, topRightConfig)

        val bottomLeftConfig = topLeftConfig.copy(
            y = config.getBottomLeftY(),
            type = QRRoundFinder.BOTTOM_LEFT
        )
        prepareRoundedFindersToDraw(canvas, bottomLeftConfig)
    }

    private fun prepareRoundedFindersToDraw(canvas: Canvas, config: RoundFinderDrawConfig) {
        val floatPoint = config.point.toFloat()
        val outerConfig = RectDrawConfig(
            x = config.x.toFloat(),
            y = config.y.toFloat(),
            size = floatPoint * FINDER_SIZE,
            offset = LARGE_RECT_OFFSET_FACTOR * floatPoint
        )
        val middleConfig = RectDrawConfig(
            x = outerConfig.x + config.point,
            y = outerConfig.y + config.point,
            size = outerConfig.size - 2 * floatPoint,
            offset = MEDIUM_RECT_OFFSET_FACTOR * floatPoint
        )
        val smallConfig = RectDrawConfig(
            x = middleConfig.x + config.point,
            y = middleConfig.y + config.point,
            size = middleConfig.size - 2 * floatPoint,
            offset = SMALL_RECT_OFFSET_FACTOR * floatPoint
        )
        drawRoundedFinder(canvas, outerConfig, middleConfig, smallConfig, config.type)
    }

    private fun drawRoundedFinder(
        canvas: Canvas,
        outConf: RectDrawConfig,
        middleConf: RectDrawConfig,
        innerConf: RectDrawConfig,
        type: QRRoundFinder
    ) {
        when (type) {
            QRRoundFinder.TOP_LEFT -> drawTopLeftFinder(canvas, outConf, middleConf, innerConf)
            QRRoundFinder.TOP_RIGHT -> drawTopRightFinder(canvas, outConf, middleConf, innerConf)
            QRRoundFinder.BOTTOM_LEFT -> drawBottomLeftFinder(
                canvas,
                outConf,
                middleConf,
                innerConf
            )
        }
    }

    private fun drawTopLeftFinder(
        canvas: Canvas,
        outConf: RectDrawConfig,
        middleConf: RectDrawConfig,
        innerConf: RectDrawConfig
    ) {
        canvas.drawTopLeftFinderRect(outConf, TOP_LEFT_ARC_START_ANGLE)
        canvas.drawTopLeftFinderRect(middleConf, TOP_LEFT_ARC_START_ANGLE, Color.WHITE)
        canvas.drawTopLeftFinderRect(innerConf, TOP_LEFT_ARC_START_ANGLE)
    }

    private fun Canvas.drawTopLeftFinderRect(
        config: RectDrawConfig,
        startAngle: Float,
        color: Int = config.color
    ) {
        paint.color = color
        val path = Path()
        path.applyTopLeftRectDrawConfig(config, startAngle)
        drawPath(path, paint)
    }

    private fun Path.applyTopLeftRectDrawConfig(config: RectDrawConfig, startAngle: Float) {
        moveTo(config.x, config.y + config.offset)
        arcTo(
            RectF(
                config.x,
                config.y,
                config.x + config.offset,
                config.y + config.offset
            ),
            startAngle, SWEEP_ANGLE
        )
        lineTo(config.x + config.size, config.y)
        lineTo(config.x + config.size, config.y + config.size)
        lineTo(config.x, config.y + config.size)
        lineTo(config.x, config.y)
    }

    private fun drawTopRightFinder(
        canvas: Canvas,
        outConf: RectDrawConfig,
        middleConf: RectDrawConfig,
        innerConf: RectDrawConfig
    ) {
        canvas.drawTopRightFinderRect(outConf, TOP_RIGHT_ARC_START_ANGLE)
        canvas.drawTopRightFinderRect(middleConf, TOP_RIGHT_ARC_START_ANGLE, Color.WHITE)
        canvas.drawTopRightFinderRect(innerConf, TOP_RIGHT_ARC_START_ANGLE)
    }

    private fun Canvas.drawTopRightFinderRect(
        config: RectDrawConfig,
        startAngle: Float,
        color: Int = config.color
    ) {
        paint.color = color
        val path = Path()
        path.applyTopRightRectDrawConfig(config, startAngle)
        drawPath(path, paint)
    }

    private fun Path.applyTopRightRectDrawConfig(config: RectDrawConfig, startAngle: Float) {
        moveTo(config.x, config.y)
        lineTo(config.x + config.size - config.offset, config.y)
        arcTo(
            RectF(
                config.x + config.size - config.offset,
                config.y,
                config.x + config.size,
                config.y + config.offset
            ),
            startAngle, SWEEP_ANGLE
        )
        lineTo(config.x + config.size, config.y + config.size)
        lineTo(config.x, config.y + config.size)
        lineTo(config.x, config.y)
    }

    private fun drawBottomLeftFinder(
        canvas: Canvas,
        outConf: RectDrawConfig,
        middleConf: RectDrawConfig,
        innerConf: RectDrawConfig
    ) {
        canvas.drawBottomLeftFinderRect(outConf, BOTTOM_LEFT_ARC_START_ANGLE)
        canvas.drawBottomLeftFinderRect(middleConf, BOTTOM_LEFT_ARC_START_ANGLE, Color.WHITE)
        canvas.drawBottomLeftFinderRect(innerConf, BOTTOM_LEFT_ARC_START_ANGLE)
    }

    private fun Canvas.drawBottomLeftFinderRect(
        config: RectDrawConfig,
        startAngle: Float,
        color: Int = config.color
    ) {
        paint.color = color
        val path = Path()
        path.applyBottomLeftRectDrawConfig(config, startAngle)
        drawPath(path, paint)
    }

    private fun Path.applyBottomLeftRectDrawConfig(config: RectDrawConfig, startAngle: Float) {
        moveTo(config.x, config.y)
        lineTo(config.x + config.size, config.y)
        lineTo(config.x + config.size, config.y + config.size)
        lineTo(config.x + config.offset, config.y + config.size)
        arcTo(
            RectF(
                config.x,
                config.y + config.size - config.offset,
                config.x + config.offset,
                config.y + config.size
            ),
            startAngle, SWEEP_ANGLE
        )
        lineTo(config.x, config.y)
    }

    private companion object {
        const val UTF_8_ENCODING = "UTF-8"
        const val FINDER_SIZE = 7
        const val SCALE_DOWN_FACTOR = 0.5f
        const val QUITE_ZONE_FACTOR = 0.1f
        const val SWEEP_ANGLE = 90f
        const val TOP_LEFT_ARC_START_ANGLE = 180f
        const val TOP_RIGHT_ARC_START_ANGLE = 270f
        const val BOTTOM_LEFT_ARC_START_ANGLE = 90f
        const val LARGE_RECT_OFFSET_FACTOR = 4
        const val MEDIUM_RECT_OFFSET_FACTOR = 3
        const val SMALL_RECT_OFFSET_FACTOR = 2
    }

    data class DrawConfig(
        val topPadding: Int,
        val leftPadding: Int,
        val input: ByteMatrix,
        val point: Int,
        val multipleActual: Int,
    )

    data class RoundFinderDrawConfig(
        val x: Int,
        val y: Int,
        val point: Int,
        val type: QRRoundFinder
    )

    data class RectDrawConfig(
        val x: Float,
        val y: Float,
        val size: Float,
        val offset: Float,
        val color: Int = Color.BLACK,
    )

    private fun DrawConfig.getTopLeftX(): Int {
        return leftPadding - point
    }

    private fun DrawConfig.getTopLeftY(): Int {
        return topPadding - point
    }

    private fun DrawConfig.getTopRightX(): Int {
        return leftPadding + (input.width - FINDER_SIZE) * multipleActual - point
    }

    private fun DrawConfig.getBottomLeftY(): Int {
        return topPadding + (input.height - FINDER_SIZE) * multipleActual - point
    }
}