package com.anshu.system

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class Quest(
    val id: Long, val title: String, val date: String, val deadline: Long,
    val reward: Int, val penalty: Int, var status: String, var runStart: Long,
    val sessions: MutableList<LongArray>
)

fun Quest.spent(now: Long): Long =
    sessions.sumOf { it[1] - it[0] } + (if (runStart > 0) now - runStart else 0L)

fun fmt(ms: Long): String {
    val s = maxOf(0L, ms) / 1000
    return "%02d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
}

fun hm(ms: Long): String =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

object Store {
    private fun sp(c: Context) = c.getSharedPreferences("sys", Context.MODE_PRIVATE)

    fun quests(c: Context): MutableList<Quest> {
        val a = JSONArray(sp(c).getString("q", "[]"))
        return MutableList(a.length()) { i ->
            val o = a.getJSONObject(i)
            val s = o.getJSONArray("s")
            Quest(
                o.getLong("id"), o.getString("t"), o.getString("d"), o.getLong("dl"),
                o.getInt("r"), o.getInt("p"), o.getString("st"), o.getLong("rs"),
                MutableList(s.length()) { j ->
                    longArrayOf(s.getJSONArray(j).getLong(0), s.getJSONArray(j).getLong(1))
                }
            )
        }
    }

    private fun save(c: Context, l: List<Quest>) {
        val a = JSONArray()
        l.forEach { q ->
            val s = JSONArray()
            q.sessions.forEach { s.put(JSONArray().put(it[0]).put(it[1])) }
            a.put(
                JSONObject().put("id", q.id).put("t", q.title).put("d", q.date)
                    .put("dl", q.deadline).put("r", q.reward).put("p", q.penalty)
                    .put("st", q.status).put("rs", q.runStart).put("s", s)
            )
        }
        sp(c).edit().putString("q", a.toString()).apply()
    }

    fun balance(c: Context): Long = sp(c).getLong("bal", 0L)
    fun setBalance(c: Context, v: Long) = sp(c).edit().putLong("bal", maxOf(0L, v)).apply()
    fun penalty(c: Context): Int = sp(c).getInt("pen", 0)
    private fun setPenalty(c: Context, v: Int) = sp(c).edit().putInt("pen", v).apply()
    fun blocked(c: Context): Set<String> = sp(c).getStringSet("blk", emptySet()) ?: emptySet()
    fun setBlocked(c: Context, s: Set<String>) = sp(c).edit().putStringSet("blk", HashSet(s)).apply()

    /** Deadline nikal gaya => FAILED + penalty. 7 din purana data delete. */
    fun settle(c: Context) {
        val now = System.currentTimeMillis()
        val l = quests(c)
        var pen = penalty(c)
        var changed = false
        l.forEach { q ->
            if (q.status == "PENDING" && q.deadline < now) {
                if (q.runStart > 0) { q.sessions.add(longArrayOf(q.runStart, q.deadline)); q.runStart = 0 }
                q.status = "FAILED"; pen += q.penalty; changed = true
            }
        }
        val cut = now - 7 * 86400000L
        if (l.removeAll { it.deadline < cut }) changed = true
        if (changed) { setPenalty(c, pen); save(c, l) }
    }

    fun add(c: Context, t: String, date: String, dl: Long, r: Int, p: Int) {
        val l = quests(c)
        l.add(Quest(System.currentTimeMillis(), t, date, dl, r, p, "PENDING", 0L, mutableListOf()))
        save(c, l)
    }

    fun toggle(c: Context, id: Long) {
        val l = quests(c)
        val q = l.firstOrNull { it.id == id } ?: return
        val now = System.currentTimeMillis()
        if (q.runStart > 0) { q.sessions.add(longArrayOf(q.runStart, now)); q.runStart = 0 } else q.runStart = now
        save(c, l)
    }

    fun complete(c: Context, id: Long) {
        val l = quests(c)
        val q = l.firstOrNull { it.id == id } ?: return
        if (q.status != "PENDING") return
        val now = System.currentTimeMillis()
        if (q.runStart > 0) { q.sessions.add(longArrayOf(q.runStart, now)); q.runStart = 0 }
        q.status = "DONE"
        val pen = penalty(c)
        val cut = minOf(pen, q.reward)
        setPenalty(c, pen - cut)
        setBalance(c, balance(c) + (q.reward - cut) * 60000L)
        save(c, l)
    }

    /** Sirf future din ke quests delete ho sakte hain (cheating roko). */
    fun delete(c: Context, id: Long) {
        val l = quests(c)
        l.removeAll { it.id == id && it.date > LocalDate.now().toString() }
        save(c, l)
    }
}
