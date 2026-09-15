package com.scrolla.share

import com.scrolla.model.DistanceFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BUILD_PLAN 3.2.
 *
 * What is worth asserting about a picture is not how it looks — that is a job
 * for eyes — but that the words on it are the same words the product uses
 * everywhere else. The caption is the half that can silently disagree with the
 * card: a figure formatted one way in the image and another in the text is a
 * real defect and not one anybody would catch by looking at either.
 */
class RecapCardTest {

    @Test
    fun `the caption carries the same figure the app formats`() {
        val km = 1.234f
        assertTrue(
            RecapCard.shareText(km).contains(DistanceFormatter.formatDistance(km))
        )
    }

    @Test
    fun `the caption explains the game`() {
        // The image lands in front of people who have never heard of a
        // leaderboard you win by losing. Without this clause the post reads as
        // a boast about the wrong thing, which is the opposite of the loop this
        // card exists to start.
        assertTrue(RecapCard.shareText(1f).contains("lowest number wins"))
    }

    @Test
    fun `a week of nothing still formats`() {
        // The recap screen gates on hasData, but shareText must not be the
        // thing that decides that by crashing.
        assertEquals(
            RecapCard.shareText(0f).contains(DistanceFormatter.formatDistance(0f)),
            true
        )
    }

    @Test
    fun `the card is four by five`() {
        // Instagram crops anything taller and letterboxes anything shorter, and
        // a cropped card loses the footer that explains the game.
        assertEquals(4f / 5f, RecapCard.WIDTH_PX.toFloat() / RecapCard.HEIGHT_PX, 0.001f)
    }
}
