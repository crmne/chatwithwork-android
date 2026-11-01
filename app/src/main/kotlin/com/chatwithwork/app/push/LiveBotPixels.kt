package com.chatwithwork.app.push

import android.graphics.RectF

/**
 * The bot on the Live Update's card: the app's mark on its 16 by 16 grid, the
 * same drawing the web app and the iOS app draw (`BotPixels.swift` there), so
 * it stays square at any size. Each rectangle is "x,y,width,height" in squares.
 */
object LiveBotPixels {
    val body = rects(
        "1,2,14,1,14 0,3,16,1,16 0,4,16,1,16 0,5,16,1,16 0,6,16,1,16 0,7,16,1,16 0,8,16,1,16 " +
            "0,9,16,1,16 0,10,16,1,16 1,11,14,1,14 3,12,4,1,4 3,13,3,1,3 3,14,2,1,2"
    )

    fun face(face: LivePresentation.Face): List<RectF> = rects(
        when (face) {
            LivePresentation.Face.OPEN ->
                "4,5,2,3,2 10,5,2,3,2"

            LivePresentation.Face.CLOSED ->
                "4,7,2,1,2 10,7,2,1,2"

            LivePresentation.Face.RIGHT ->
                "6,5,2,3,2 12,5,2,3,2"

            LivePresentation.Face.LEFT ->
                "2,5,2,3,2 8,5,2,3,2"

            LivePresentation.Face.DOWN ->
                "5,7,2,2,2 11,7,2,2,2"

            LivePresentation.Face.UP ->
                "5,4,2,2,2 11,4,2,2,2"

            LivePresentation.Face.FOCUS ->
                "3,5,3,1,3 4,6,2,2,2 10,5,3,1,3 10,6,2,2,2"

            LivePresentation.Face.WIDE ->
                "3,4,1,1,1 4,4,1,1,1 5,4,1,1,1 3,5,1,1,1 5,5,1,1,1 3,6,1,1,1 4,6,1,1,1 5,6,1,1,1 " +
                    "3,7,1,1,1 4,7,1,1,1 5,7,1,1,1 10,4,1,1,1 11,4,1,1,1 12,4,1,1,1 10,5,1,1,1 12,5,1,1,1 " +
                    "10,6,1,1,1 11,6,1,1,1 12,6,1,1,1 10,7,1,1,1 11,7,1,1,1 12,7,1,1,1"

            LivePresentation.Face.BROW ->
                "4,5,2,3,2 10,6,2,2,2 9,4,3,1,3"

            LivePresentation.Face.HAPPY ->
                "3,7,1,1,1 4,6,1,1,1 5,6,1,1,1 6,7,1,1,1 9,7,1,1,1 10,6,1,1,1 11,6,1,1,1 12,7,1,1,1"

            LivePresentation.Face.FLAT ->
                "3,7,3,1,3 10,7,3,1,3"
        }
    )

    data class Drawing(val rects: List<RectF>, val width: Int, val height: Int)

    fun tool(tool: LivePresentation.Tool): Drawing = when (tool) {
        LivePresentation.Tool.SEARCH -> Drawing(
            rects(
                "1,0,1,1,1 2,0,1,1,1 3,0,1,1,1 0,1,1,1,1 4,1,1,1,1 0,2,1,1,1 4,2,1,1,1 0,3,1,1,1 " +
                    "4,3,1,1,1 1,4,1,1,1 2,4,1,1,1 3,4,1,1,1 4,5,1,1,1 5,6,1,1,1"
            ),
            7,
            7
        )

        LivePresentation.Tool.READ -> Drawing(
            rects(
                "0,0,1,1,1 1,0,1,1,1 2,0,1,1,1 3,0,1,1,1 4,0,1,1,1 5,0,1,1,1 0,1,1,1,1 5,1,1,1,1 " +
                    "0,2,1,1,1 2,2,1,1,1 3,2,1,1,1 5,2,1,1,1 0,3,1,1,1 5,3,1,1,1 0,4,1,1,1 2,4,1,1,1 " +
                    "3,4,1,1,1 5,4,1,1,1 0,5,1,1,1 5,5,1,1,1 0,6,1,1,1 1,6,1,1,1 2,6,1,1,1 3,6,1,1,1 " +
                    "4,6,1,1,1 5,6,1,1,1"
            ),
            6,
            7
        )

        LivePresentation.Tool.LIST -> Drawing(
            rects(
                "0,0,1,1,1 1,0,1,1,1 2,0,1,1,1 0,1,1,1,1 1,1,1,1,1 2,1,1,1,1 3,1,1,1,1 4,1,1,1,1 " +
                    "5,1,1,1,1 6,1,1,1,1 0,2,1,1,1 6,2,1,1,1 0,3,1,1,1 6,3,1,1,1 0,4,1,1,1 6,4,1,1,1 " +
                    "0,5,1,1,1 1,5,1,1,1 2,5,1,1,1 3,5,1,1,1 4,5,1,1,1 5,5,1,1,1 6,5,1,1,1"
            ),
            7,
            6
        )

        LivePresentation.Tool.USE -> Drawing(
            rects(
                "2,0,1,1,1 4,0,1,1,1 2,1,1,1,1 4,1,1,1,1 1,2,1,1,1 2,2,1,1,1 3,2,1,1,1 4,2,1,1,1 " +
                    "5,2,1,1,1 1,3,1,1,1 2,3,1,1,1 3,3,1,1,1 4,3,1,1,1 5,3,1,1,1 2,4,1,1,1 3,4,1,1,1 " +
                    "4,4,1,1,1 3,5,1,1,1 3,6,1,1,1"
            ),
            7,
            7
        )

        LivePresentation.Tool.PLAN -> Drawing(
            rects(
                "0,0,1,1,1 2,0,1,1,1 3,0,1,1,1 4,0,1,1,1 5,0,1,1,1 0,2,1,1,1 2,2,1,1,1 3,2,1,1,1 " +
                    "4,2,1,1,1 5,2,1,1,1 0,4,1,1,1 2,4,1,1,1 3,4,1,1,1 4,4,1,1,1"
            ),
            6,
            5
        )

        LivePresentation.Tool.RUN -> Drawing(
            rects(
                "3,0,1,1,1 1,1,1,1,1 3,1,1,1,1 5,1,1,1,1 2,2,1,1,1 3,2,1,1,1 4,2,1,1,1 0,3,1,1,1 " +
                    "1,3,1,1,1 2,3,1,1,1 4,3,1,1,1 5,3,1,1,1 6,3,1,1,1 2,4,1,1,1 3,4,1,1,1 4,4,1,1,1 " +
                    "1,5,1,1,1 3,5,1,1,1 5,5,1,1,1 3,6,1,1,1"
            ),
            7,
            7
        )

        LivePresentation.Tool.WRITE -> Drawing(
            rects(
                "5,0,1,1,1 6,0,1,1,1 4,1,1,1,1 6,1,1,1,1 3,2,1,1,1 5,2,1,1,1 2,3,1,1,1 4,3,1,1,1 " +
                    "1,4,1,1,1 3,4,1,1,1 0,5,1,1,1 1,5,1,1,1 0,6,1,1,1"
            ),
            7,
            7
        )

        LivePresentation.Tool.ASK -> Drawing(
            rects(
                "1,0,1,1,1 2,0,1,1,1 3,0,1,1,1 0,1,1,1,1 4,1,1,1,1 3,2,1,1,1 2,3,1,1,1 2,5,1,1,1"
            ),
            5,
            6
        )
    }

    private fun rects(encoded: String): List<RectF> = encoded.split(' ').map { rect ->
        val (x, y, width, height) = rect.split(',').map(String::toFloat)
        RectF(x, y, x + width, y + height)
    }
}
