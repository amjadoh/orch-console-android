package com.amjadoh.sirfari

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** «أمر جديد»: لما المنظّم ما يعرف المشروع، تختار مشروع والنص ينرسل له */
class CommandPickTest {
    @Test
    fun namesFromServerMessage() {
        val m = "ما عرفت المشروع. المشاريع: مصمم روبلوكس، الأخطبوط، SAB Visa، TV Remote، المصروف، اسطبل الأثيل"
        assertEquals(listOf("مصمم روبلوكس", "الأخطبوط", "SAB Visa", "TV Remote", "المصروف", "اسطبل الأثيل"), projectNames(m))
        assertEquals(emptyList<String>(), projectNames("ما فهمت الأمر. اكتب: الأوامر"))
    }

    @Test
    fun whenToAskForProject() {
        assertTrue(needsProject(JSONObject("""{"sendable":false,"kind":"error","message":"ما فهمت الأمر. اكتب: الأوامر"}""")))
        assertTrue(needsProject(JSONObject("""{"sendable":false,"kind":"error","message":"ما عرفت المشروع. المشاريع: المصروف"}""")))
        assertFalse(needsProject(JSONObject("""{"sendable":false,"kind":"error","message":"الأمر فيه أكثر من مشروع"}""")))
        assertFalse(needsProject(JSONObject("""{"sendable":true,"kind":"open","summary":"جلسة للمصروف"}""")))
        assertFalse(needsProject(null))
    }

    @Test
    fun composedText() {
        assertEquals("غير لون الزر", composed("  غير لون الزر ", null))
        assertEquals("المصروف: غير لون الزر", composed("غير لون الزر", "المصروف"))
    }
}
