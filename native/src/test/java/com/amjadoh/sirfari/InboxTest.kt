package com.amjadoh.sirfari

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxTest {
    private val now = 1_700_000_000_000L
    private val overview = JSONObject("""
        {"tasks":[
          {"taskId":"MSR-0002","title":"تصليح","status":"awaiting","planState":"ready","planPending":false,"revisions":0},
          {"taskId":"MSR-0003","title":"خطة","status":"awaiting","planState":"running","planPending":true,"revisions":0},
          {"taskId":"MSR-0001","title":"قديمة","status":"done","finishedAt":${now - 3 * 86_400_000L}},
          {"taskId":"ORC-0009","title":"جديدة","status":"failed","finishedAt":${now - 60_000}}
        ]}
    """)
    private val admin = JSONObject("""{"requests":[{"id":"adm-1","state":"pending","summary":"تثبيت"},{"id":"adm-0","state":"done","summary":"x"}]}""")

    @Test
    fun eventsOnlyWhatNeedsYou() {
        val keys = Inbox.events(overview, admin, now).map { it.key }
        assertEquals(listOf("await:MSR-0002:0", "end:ORC-0009", "admin:adm-1"), keys)
    }

    @Test
    fun firstRunIsSilentThenOnlyNew() {
        val ev = Inbox.events(overview, admin, now)
        val (first, seen) = Inbox.diff(ev, emptySet(), firstRun = true)
        assertTrue(first.isEmpty())
        val (again, _) = Inbox.diff(ev, seen, firstRun = false)
        assertTrue("نفس الحدث ما يتكرر", again.isEmpty())
        val revised = ev.map { if (it.key.startsWith("await:")) it.copy(key = "await:MSR-0002:1") else it }
        val (fresh, _) = Inbox.diff(revised, seen, firstRun = false)
        assertEquals(listOf("await:MSR-0002:1"), fresh.map { it.key })
    }

    @Test
    fun overflowIsNotLost() {
        val many = (1..8).map { Inbox.Event("end:T-$it", Inbox.CH_TASKS, "t", "x", null) }
        val (shown, seen) = Inbox.diff(many, setOf("old"), firstRun = false)
        assertEquals(5, shown.size)
        val (rest, _) = Inbox.diff(many, seen, firstRun = false)
        assertEquals("الباقي يطلع المرة الجاية", listOf("end:T-6", "end:T-7", "end:T-8"), rest.map { it.key })
    }

    @Test
    fun overviewWithoutPlanStateStillNotifies() {
        val o = JSONObject("""{"tasks":[{"taskId":"MSR-0004","projectId":"masroof","title":"x","status":"awaiting"}]}""")
        assertEquals(listOf("await:MSR-0004:0"), Inbox.events(o, null, now).map { it.key })
    }
}
