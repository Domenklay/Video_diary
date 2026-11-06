package com.quietmoments.diary

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** Optional local answers and moods; independent from Firebase. */
data class DailyReflection(val date: String, val question: String, val answer: String, val mood: String, val reason: String = "")
class LocalJournal(context: Context, uid: String) {
    private val root = File(context.filesDir, "local_moments/${uid.replace(Regex("[^A-Za-z0-9_-]"), "_")}").apply { mkdirs() }
    private val target = File(root, "reflections.json")
    @Synchronized fun all(): List<DailyReflection> {
        if (!target.exists() && !File(target.absolutePath + ".bak").exists()) return emptyList()
        val text = AtomicFile(target).openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        val a = JSONArray(text)
        return (0 until a.length()).map { i ->
            val x = a.getJSONObject(i)
            DailyReflection(x.getString("date"), x.getString("question"), x.optString("answer"), x.optString("mood"), x.optString("reason", ""))
        }
    }
    @Synchronized fun save(value: DailyReflection) {
        LocalDate.parse(value.date)
        val updated = all().filterNot { it.date == value.date } + value.copy(answer=value.answer.take(10000), reason=value.reason.take(2000))
        val a = JSONArray()
        updated.sortedBy { it.date }.forEach { d -> a.put(JSONObject().apply {
            put("date", d.date); put("question", d.question); put("answer", d.answer); put("mood", d.mood); put("reason", d.reason)
        }) }
        val atomic = AtomicFile(target)
        val stream = atomic.startWrite()
        try {
            stream.write(a.toString().toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (t: Throwable) {
            atomic.failWrite(stream)
            throw t
        }
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
        fun questionFor(date: LocalDate, topic: String = "Разные темы"): String {
            val bank = when (topic) {
                "Семья и близкие" -> listOf(
                    "Кто сегодня подарил тебе тепло?",
                    "Какой разговор с близким хочется сохранить?",
                    "Чему тебя научили родные?",
                    "Какой семейный обычай особенно дорог?",
                    "За что сегодня хочется поблагодарить близкого?"
                )
                "Мечты" -> listOf(
                    "Какую мечту хочется приблизить?",
                    "Что ты сегодня сделала ради своей цели?",
                    "Какой будет твоя жизнь через год?",
                    "Что бы ты попробовала без страха?",
                    "Чем хочется гордиться в будущем?"
                )
                "Благодарность" -> listOf(
                    "За что ты сегодня благодарна?",
                    "Какой маленький момент был самым приятным?",
                    "Что помогло тебе почувствовать спокойствие?",
                    "Какая хорошая неожиданность случилась?",
                    "Что в себе сегодня хочется отметить?"
                )
                "Воспоминания" -> listOf(
                    "Какое воспоминание сегодня ожило?",
                    "Какой момент ты бы повторила?",
                    "Что хотелось бы рассказать себе в прошлом?",
                    "Кто оставил особенный след в твоей жизни?",
                    "Какая деталь этого дня останется в памяти?"
                )
                else -> questions
            }
            return bank[Math.floorMod(date.toEpochDay(), bank.size.toLong()).toInt()]
        }
    }
}
