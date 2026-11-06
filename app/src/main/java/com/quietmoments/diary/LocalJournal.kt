package com.quietmoments.diary

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Optional local answers and moods; independent from Firebase. */
data class DailyReflection(val date: String, val question: String, val answer: String, val mood: String)
class LocalJournal(context: Context, uid: String) {
    private val root = File(context.filesDir, "local_moments/${uid.replace(Regex("[^A-Za-z0-9_-]"), "_")}").apply { mkdirs() }
    private val target = File(root, "reflections.json")
    @Synchronized fun all(): List<DailyReflection> {
        if (!target.exists()) return emptyList()
        val a = JSONArray(target.readText(Charsets.UTF_8))
        return (0 until a.length()).map { i ->
            val x = a.getJSONObject(i)
            DailyReflection(x.getString("date"), x.getString("question"), x.optString("answer"), x.optString("mood"))
        }
    }
    @Synchronized fun save(value: DailyReflection) {
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(value.date))
        val updated = all().filterNot { it.date == value.date } + value.copy(answer=value.answer.take(10000))
        val a = JSONArray()
        updated.sortedBy { it.date }.forEach { d -> a.put(JSONObject().apply {
            put("date", d.date); put("question", d.question); put("answer", d.answer); put("mood", d.mood)
        }) }
        val temp = File(root,"reflections.pending")
        temp.writeText(a.toString(),Charsets.UTF_8)
        if (!temp.renameTo(target)) {temp.copyTo(target,overwrite=true);temp.delete()}
    }
    companion object {
        val questions = listOf(
            "Что сегодня тебя порадовало?",
            "За что ты благодарна этому дню?",
            "Что нового ты узнала?",
            "Какое событие хочется запомнить?",
            "Что помогло тебе восстановить силы?",
            "О чём ты мечтаешь сейчас?",
            "Какой момент хотелось бы повторить?"
        )
        fun questionFor(date: LocalDate): String = questions[(date.toEpochDay() % questions.size).toInt().let { if (it < 0) it + questions.size else it }]
    }
}
