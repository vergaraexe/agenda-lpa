package cl.lpa.agenda

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * Guarda cada dia en su propia carpeta dentro del almacenamiento de la app:
 *   files/agenda/2026-11-07/page_1.json, page_2.json, ...
 * Los puntos se guardan en dp, asi la pagina se ve igual en cualquier orientacion.
 */
class DayStore(context: Context) {
    private val root = File(context.filesDir, "agenda").apply { mkdirs() }

    fun dayDir(d: LocalDate) = File(root, d.toString())

    private fun pageFile(d: LocalDate, page: Int) = File(dayDir(d), "page_$page.json")

    fun pageCount(d: LocalDate): Int {
        val n = dayDir(d).listFiles { f -> f.name.matches(Regex("page_\\d+\\.json")) }?.size ?: 0
        return maxOf(1, n)
    }

    fun addPage(d: LocalDate): Int {
        val next = pageCount(d) + 1
        save(d, next, emptyList())
        // si la pagina 1 aun no existe en disco, se crea para mantener la numeracion continua
        if (!pageFile(d, 1).exists()) save(d, 1, emptyList())
        return next
    }

    fun load(d: LocalDate, page: Int): List<InkStroke> {
        val f = pageFile(d, page)
        if (!f.exists()) return emptyList()
        return try {
            val arr = JSONObject(f.readText()).getJSONArray("strokes")
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val p = o.getJSONArray("p")
                InkStroke(
                    points = List(p.length() / 2) { k ->
                        Offset(p.getDouble(2 * k).toFloat(), p.getDouble(2 * k + 1).toFloat())
                    },
                    color = Color(o.getInt("c")),
                    width = o.getDouble("w").toFloat(),
                    highlighter = o.getBoolean("h")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(d: LocalDate, page: Int, strokes: List<InkStroke>) {
        val arr = JSONArray()
        strokes.forEach { s ->
            val pts = JSONArray()
            s.points.forEach { pts.put(it.x.toDouble()); pts.put(it.y.toDouble()) }
            arr.put(
                JSONObject()
                    .put("c", s.color.toArgb())
                    .put("w", s.width.toDouble())
                    .put("h", s.highlighter)
                    .put("p", pts)
            )
        }
        val dir = dayDir(d).apply { mkdirs() }
        val tmp = File(dir, "page_$page.tmp")
        tmp.writeText(JSONObject().put("strokes", arr).toString())
        tmp.renameTo(pageFile(d, page)) // escritura atomica: no se corrompe si se apaga la tablet
    }

    /** Dias que tienen contenido (lo usara la exportacion a Word). */
    fun daysWithContent(): List<LocalDate> =
        root.listFiles()?.mapNotNull { runCatching { LocalDate.parse(it.name) }.getOrNull() }?.sorted()
            ?: emptyList()
}
