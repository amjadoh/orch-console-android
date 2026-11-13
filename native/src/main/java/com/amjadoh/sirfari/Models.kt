package com.amjadoh.sirfari

import org.json.JSONArray
import org.json.JSONObject

// شكل بيانات سيرفري كما يرجعها السيرفر (/api/overview و/api/server). كل حقل اختياري:
// لو السيرفر أقدم أو أحدث، نعرض اللي نعرفه وما نطيح.

data class Problem(val key: String, val title: String, val level: String, val line: String)
data class TaskCard(
    val taskId: String, val projectId: String, val title: String, val status: String, val step: String,
    val first: String? = null, val second: String? = null, val since: Long? = null, val planPending: Boolean = false,
) {
    /** مرحلة الشغل الحقيقية (من phase في المنظم): خطة ← ينفذ ← يراجع ← يفحص */
    val stage get() = when (step) { "approval" -> 0; "first" -> 1; "second" -> 2; "checks" -> 3; "done" -> 4; else -> if (status == "running") 1 else 0 }
}
data class Overview(
    val awaiting: Int, val running: Int, val doneToday: Int, val failed: Int,
    val healthLevel: String, val problems: List<Problem>, val tasks: List<TaskCard>,
    val adminPending: Int, val serverBad: Int, val serverWarn: Int, val maintenanceUntil: Long?,
)

data class Disk(val mount: String, val pct: Double, val usedGb: Double, val totalGb: Double)
data class Mem(val totalMb: Double, val availMb: Double, val swapTotalMb: Double, val swapUsedMb: Double) {
    val usedPct get() = if (totalMb > 0) (totalMb - availMb) / totalMb * 100 else 0.0
    val swapPct get() = if (swapTotalMb > 0) swapUsedMb / swapTotalMb * 100 else 0.0
}
data class Service(val label: String, val group: String, val state: String, val protected: Boolean)
data class Container(val label: String, val state: String, val status: String, val onDemand: Boolean)
data class Timer(val label: String, val kind: String, val result: String, val lastAt: Long?)
data class Alert(val level: String, val text: String)
data class Consumer(val label: String, val rssMb: Double, val protected: Boolean)
data class ServerView(
    val at: Long?, val hostName: String?, val cpuPct: Double?, val load: List<Double>, val uptimeSec: Long?,
    val mem: Mem?, val disks: List<Disk>, val services: List<Service>, val containers: List<Container>,
    val timers: List<Timer>, val alerts: List<Alert>, val consumers: List<Consumer>, val tailscaleOnline: Boolean?,
    val history: List<Point> = emptyList(),
)
data class Point(val at: Long, val cpu: Double?, val mem: Double?)

private fun JSONObject.str(k: String) = if (has(k) && !isNull(k)) optString(k) else null
private fun JSONObject.num(k: String) = if (has(k) && !isNull(k)) optDouble(k).takeIf { !it.isNaN() } else null
private inline fun <T> JSONArray?.items(f: (JSONObject) -> T?): List<T> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it)?.let(f) }

private fun card(it: JSONObject) = TaskCard(
    it.optString("taskId"), it.optString("projectId"), it.optString("title"), it.optString("status"), it.optString("step"),
    it.str("first"), it.str("second"), it.num("phaseSince")?.toLong(), it.optBoolean("planPending"),
)

object Models {
    fun overview(o: JSONObject): Overview {
        val c = o.optJSONObject("counts") ?: JSONObject()
        val h = o.optJSONObject("health") ?: JSONObject()
        val s = o.optJSONObject("server") ?: JSONObject()
        return Overview(
            awaiting = c.optInt("awaiting"), running = c.optInt("running"), doneToday = c.optInt("doneToday"), failed = c.optInt("failed"),
            healthLevel = h.str("level") ?: "unknown",
            problems = h.optJSONArray("problems").items { Problem(it.optString("key"), it.optString("title"), it.optString("level"), it.optString("line")) },
            tasks = o.optJSONArray("tasks").items { card(it) },
            adminPending = o.optInt("adminPending"), serverBad = s.optInt("bad"), serverWarn = s.optInt("warn"),
            maintenanceUntil = h.num("maintenanceUntil")?.toLong(),
        )
    }

    fun server(o: JSONObject): ServerView {
        val srv = o.optJSONObject("server")
        val host = srv?.optJSONObject("host") ?: JSONObject()
        val m = host.optJSONObject("mem")
        val load = host.optJSONArray("load")
        return ServerView(
            at = srv?.num("at")?.toLong(),
            hostName = host.str("name"),
            cpuPct = host.num("cpuPct"),
            load = if (load == null) emptyList() else (0 until load.length()).map { load.optDouble(it) }.filter { !it.isNaN() },
            uptimeSec = host.num("uptimeSec")?.toLong(),
            mem = m?.let { Mem(it.optDouble("totalMb"), it.optDouble("availMb"), it.optDouble("swapTotalMb"), it.optDouble("swapUsedMb")) },
            disks = host.optJSONArray("disks").items { Disk(it.optString("mount"), it.optDouble("pct"), it.optDouble("usedGb"), it.optDouble("totalGb")) },
            services = srv?.optJSONArray("services").items { Service(it.optString("label"), it.optString("group"), it.optString("state"), it.optBoolean("protected")) },
            containers = o.optJSONObject("docker")?.optJSONArray("containers").items { Container(it.optString("label"), it.optString("state"), it.optString("status"), it.optBoolean("onDemand")) },
            timers = srv?.optJSONArray("timers").items { Timer(it.optString("label"), it.optString("kind"), it.optString("result"), it.num("lastAt")?.toLong()) },
            alerts = o.optJSONArray("alerts").items { Alert(it.optString("level"), it.optString("text")) },
            consumers = host.optJSONArray("consumers").items { Consumer(it.optString("label"), it.optDouble("rssMb"), it.optBoolean("protected")) },
            tailscaleOnline = host.optJSONObject("tailscale")?.optBoolean("online"),
            history = srv?.optJSONArray("history").items { p -> p.num("at")?.let { Point(it.toLong(), p.num("cpu"), p.num("mem")) } },
        )
    }
}

data class Plan(val summary: String, val steps: List<String>, val files: List<String>, val risks: List<String>, val planner: String?)
data class Attempts(val total: Int, val max: Int, val next: String?)
data class ActItem(val kind: String, val tool: String, val path: String?, val cmd: String?)
data class Activity(val live: Boolean, val agent: String?, val at: Long?, val items: List<ActItem>)
data class TaskDetail(
    val taskId: String, val projectId: String, val title: String, val status: String, val step: String?,
    val first: String?, val second: String?, val request: String, val plan: Plan?,
    val planState: String, val planPending: Boolean, val planRevising: Boolean, val revisionsLeft: Int,
    val planFailReason: String?, val branchUrl: String?, val issueUrl: String?, val attempts: Attempts?,
    val activity: Activity?, val summary: String?, val costUsd: Double?, val checksPassed: Boolean?,
) {
    val waitingPlan get() = planPending || planState == "running"
    val planReady get() = planState == "ready" && !waitingPlan
    val canRevise get() = planReady && revisionsLeft > 0
    val canSwap get() = planState != "running" && !planRevising && !second.isNullOrBlank()
    val canRetry get() = (status == "failed" || status == "cancelled") && attempts != null && attempts.next == null && attempts.total < attempts.max
}

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

fun Models.task(o: JSONObject): TaskDetail {
    val p = o.optJSONObject("plan")
    val a = o.optJSONObject("attempts")
    val act = o.optJSONObject("activity")
    return TaskDetail(
        taskId = o.optString("taskId"), projectId = o.optString("projectId"), title = o.optString("title"),
        status = o.optString("status"), step = o.str("step"), first = o.str("first"), second = o.str("second"),
        request = o.optString("request"),
        plan = p?.let { Plan(it.optString("summary"), it.optJSONArray("steps").strings(), it.optJSONArray("files").strings(), it.optJSONArray("risks").strings(), it.str("planner")) },
        planState = o.optString("planState"), planPending = o.optBoolean("planPending"), planRevising = o.optBoolean("planRevising"),
        revisionsLeft = o.optInt("revisionsLeft"), planFailReason = o.str("planFailReason") ?: o.str("failReason"),
        branchUrl = o.str("branchUrl"), issueUrl = o.str("issueUrl"),
        attempts = a?.let { Attempts(it.optInt("total"), it.optInt("max"), it.str("next")) },
        activity = act?.let { Activity(it.optBoolean("live"), it.str("agent"), it.num("at")?.toLong(), it.optJSONArray("items").items { x -> ActItem(x.optString("kind"), x.optString("tool"), x.str("path"), x.str("cmd")) }) },
        summary = o.str("summary"), costUsd = o.num("costUsd"), checksPassed = if (o.has("checksPassed") && !o.isNull("checksPassed")) o.optBoolean("checksPassed") else null,
    )
}

fun Models.taskList(o: JSONObject): List<TaskCard> = o.optJSONArray("tasks").items {
    card(it)
}
