package com.recall.android.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotfBotTriggerTest {
    @Test
    fun parsesEnglishChapichapiTrigger() {
        assertEquals(
            NotfBotTriggerResult("summarize this", "Draft message"),
            NotfBotTrigger.parseReady("Draft message @ChapiChapi summarize this"),
        )
    }

    @Test
    fun parsesChapichapiTrigger() {
        assertEquals(
            NotfBotTriggerResult("予定を教えて", ""),
            NotfBotTrigger.parseReady("@ちゃぴちゃぴ 予定を教えて"),
        )
    }

    @Test
    fun parsesFullWidthChapichapiTrigger() {
        assertEquals(
            NotfBotTriggerResult("返信を考えて", "明日なら空いています。"),
            NotfBotTrigger.parseReady("明日なら空いています。 ＠ ちゃぴちゃぴ 返信を考えて"),
        )
    }

    @Test
    fun parsesAsciiTrigger() {
        assertEquals(
            NotfBotTriggerResult("丁寧な返信にして", ""),
            NotfBotTrigger.parseReady("@notfbot 丁寧な返信にして"),
        )
    }

    @Test
    fun parsesFullWidthTriggerAndKeepsDraft() {
        assertEquals(
            NotfBotTriggerResult("英語にして", "明日伺います。"),
            NotfBotTrigger.parseReady("明日伺います。 ＠notfbot 英語にして"),
        )
    }

    @Test
    fun acceptsCaseInsensitiveTrigger() {
        assertEquals(
            NotfBotTriggerResult("summarize", ""),
            NotfBotTrigger.parseReady("@NotfBot summarize"),
        )
    }

    @Test
    fun armsBeforeCommandIsComplete() {
        assertEquals(
            NotfBotTriggerResult("", ""),
            NotfBotTrigger.parseDraft("@notfbot "),
        )
        assertNull(NotfBotTrigger.parseReady("@notfbot "))
    }

    @Test
    fun acceptsWhitespaceAfterAtSignButIgnoresEmbeddedTrigger() {
        assertEquals(
            NotfBotTriggerResult("要約して", ""),
            NotfBotTrigger.parseReady("＠ notfbot 要約して"),
        )
        assertNull(NotfBotTrigger.parseDraft("mail@notfbot test"))
    }
}
