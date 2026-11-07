package cl.lpa.agenda

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// ---------- Modelo (coordenadas y grosores en dp) ----------
enum class Tool { PEN, HIGHLIGHTER, ERASER }

data class InkStroke(
    val points: List<Offset>,
    val color: Color,
    val width: Float,
    val highlighter: Boolean
)

private const val PEN_WIDTH = 2.6f
private const val HL_WIDTH = 26f
private const val ERASER_RADIUS = 14f

private val penColors = listOf(
    Color(0xFF111111), Color(0xFF1E3A8A), Color(0xFFD32F2F), Color(0xFF2E7D32)
)
private val highlightColors = listOf(
    Color(0xFFFFEB3B), Color(0xFF76FF03), Color(0xFFFF80AB), Color(0xFFFFAB40)
)
private val navy = Color(0xFF1E2A5A)

// ---------- Pantalla principal ----------
@Composable
fun AgendaScreen() {
    val context = LocalContext.current
    val store = remember { DayStore(context) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var page by remember { mutableIntStateOf(1) }
    var pageCount by remember { mutableIntStateOf(store.pageCount(date)) }

    val strokes = remember(date, page) {
        mutableStateListOf<InkStroke>().apply { addAll(store.load(date, page)) }
    }
    fun commit() = store.save(date, page, strokes)
    fun goTo(d: LocalDate) { date = d; page = 1; pageCount = store.pageCount(d) }

    var tool by remember { mutableStateOf(Tool.PEN) }
    var penColor by remember { mutableStateOf(penColors[0]) }
    var hlColor by remember { mutableStateOf(highlightColors[0]) }

    Row(Modifier.fillMaxSize().background(Color(0xFFE9E6DF))) {
        Sidebar(
            tool = tool, penColor = penColor, hlColor = hlColor,
            onTool = { tool = it },
            onPenColor = { penColor = it; tool = Tool.PEN },
            onHlColor = { hlColor = it; tool = Tool.HIGHLIGHTER },
            onUndo = { if (strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex); commit() } },
            onClear = { strokes.clear(); commit() }
        )
        Column(
            Modifier
                .weight(1f).fillMaxHeight().padding(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFFFFFEFA))
        ) {
            Header(date)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                PaperCanvas(strokes, tool, penColor, hlColor, onChange = ::commit)
            }
            PageBar(
                date = date, page = page, pageCount = pageCount,
                onPrevDay = { goTo(date.minusDays(1)) },
                onNextDay = { goTo(date.plusDays(1)) },
                onToday = { goTo(LocalDate.now()) },
                onPrevPage = { if (page > 1) page-- },
                onNextPage = { if (page < pageCount) page++ },
                onNewPage = { page = store.addPage(date); pageCount = page }
            )
        }
    }
}

// ---------- Encabezado: dia a la izquierda, logo arriba a la derecha ----------
@Composable
fun Header(date: LocalDate) {
    val dia = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("es", "CL"))
        .replaceFirstChar { it.uppercase() }
    val fecha = "%02d/%02d/%d".format(date.dayOfMonth, date.monthValue, date.year)

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(dia, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = navy)
            Text(fecha, fontSize = 18.sp, color = Color(0xFF555555))
        }
        Image(
            painter = painterResource(R.drawable.logo_lpa),
            contentDescription = "Logo Liceo Politécnico Arica",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(84.dp)
        )
    }
    Box(Modifier.fillMaxWidth().height(2.dp).background(navy))
}

// ---------- Barra inferior: dias y paginas ----------
@Composable
fun PageBar(
    date: LocalDate, page: Int, pageCount: Int,
    onPrevDay: () -> Unit, onNextDay: () -> Unit, onToday: () -> Unit,
    onPrevPage: () -> Unit, onNextPage: () -> Unit, onNewPage: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFF1EFE8)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        NavButton("◀ Día", true, onPrevDay)
        if (date != LocalDate.now()) NavButton("Hoy", true, onToday)
        NavButton("Día ▶", true, onNextDay)
        Spacer(Modifier.weight(1f))
        NavButton("◀", page > 1, onPrevPage)
        Text("Página $page de $pageCount", fontSize = 15.sp, color = navy, fontWeight = FontWeight.Medium)
        NavButton("▶", page < pageCount, onNextPage)
        NavButton("＋ Nueva página", true, onNewPage)
    }
}

@Composable
private fun NavButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 15.sp,
        color = if (enabled) Color.White else Color(0x88FFFFFF),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) navy else Color(0xFF9AA0B5))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

// ---------- Hoja cuadriculada + dibujo con lapiz ----------
@Composable
fun PaperCanvas(
    strokes: MutableList<InkStroke>,
    tool: Tool,
    penColor: Color,
    hlColor: Color,
    onChange: () -> Unit
) {
    val current = remember { mutableStateListOf<Offset>() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(strokes, tool, penColor, hlColor) {
                val d = density
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Solo lapiz: el dedo y la palma se ignoran
                    if (down.type != PointerType.Stylus && down.type != PointerType.Eraser) return@awaitEachGesture

                    val erasing = tool == Tool.ERASER || down.type == PointerType.Eraser
                    var erased = false

                    fun erase(p: Offset) {
                        if (strokes.removeAll { s -> s.points.any { (it - p).getDistance() < ERASER_RADIUS } }) erased = true
                    }

                    down.consume()
                    val start = down.position / d
                    if (erasing) erase(start) else { current.clear(); current.add(start) }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val p = change.position / d
                        if (erasing) erase(p) else current.add(p)
                        change.consume()
                    }

                    if (erasing) {
                        if (erased) onChange()
                    } else if (current.isNotEmpty()) {
                        val hl = tool == Tool.HIGHLIGHTER
                        strokes.add(
                            InkStroke(
                                current.toList(),
                                if (hl) hlColor.copy(alpha = 0.4f) else penColor,
                                if (hl) HL_WIDTH else PEN_WIDTH,
                                hl
                            )
                        )
                        current.clear()
                        onChange()
                    }
                }
            }
    ) {
        val dn = density
        // Cuadricula
        val step = 26.dp.toPx()
        val lineColor = Color(0xFFBFD4EA)
        var x = 0f
        while (x <= size.width) { drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), 1f); x += step }
        var y = 0f
        while (y <= size.height) { drawLine(lineColor, Offset(0f, y), Offset(size.width, y), 1f); y += step }

        // El destacador va debajo del lapiz, como en papel
        strokes.filter { it.highlighter }.forEach { drawInk(it, dn) }
        strokes.filter { !it.highlighter }.forEach { drawInk(it, dn) }

        if (current.isNotEmpty() && tool != Tool.ERASER) {
            val hl = tool == Tool.HIGHLIGHTER
            drawInk(
                InkStroke(
                    current.toList(),
                    if (hl) hlColor.copy(alpha = 0.4f) else penColor,
                    if (hl) HL_WIDTH else PEN_WIDTH,
                    hl
                ), dn
            )
        }
    }
}

private fun DrawScope.drawInk(s: InkStroke, d: Float) {
    if (s.points.isEmpty()) return
    if (s.points.size == 1) {
        drawCircle(s.color, s.width * d / 2f, s.points[0] * d)
        return
    }
    val pts = s.points.map { it * d }
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            val p0 = pts[i - 1]; val p1 = pts[i]
            quadraticBezierTo(p0.x, p0.y, (p0.x + p1.x) / 2f, (p0.y + p1.y) / 2f)
        }
        lineTo(pts.last().x, pts.last().y)
    }
    drawPath(
        path, s.color,
        style = DrawStroke(
            width = s.width * d,
            cap = if (s.highlighter) StrokeCap.Square else StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

// ---------- Barra lateral ----------
@Composable
fun Sidebar(
    tool: Tool, penColor: Color, hlColor: Color,
    onTool: (Tool) -> Unit, onPenColor: (Color) -> Unit, onHlColor: (Color) -> Unit,
    onUndo: () -> Unit, onClear: () -> Unit
) {
    Column(
        Modifier.width(84.dp).fillMaxHeight().background(navy).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ToolButton("✏️", "Lápiz", tool == Tool.PEN) { onTool(Tool.PEN) }
        penColors.forEach { ColorDot(it, it == penColor && tool == Tool.PEN) { onPenColor(it) } }

        Spacer(Modifier.height(6.dp))
        ToolButton("🖍️", "Destacar", tool == Tool.HIGHLIGHTER) { onTool(Tool.HIGHLIGHTER) }
        highlightColors.forEach { ColorDot(it, it == hlColor && tool == Tool.HIGHLIGHTER) { onHlColor(it) } }

        Spacer(Modifier.height(6.dp))
        ToolButton("🧽", "Goma", tool == Tool.ERASER) { onTool(Tool.ERASER) }

        Spacer(Modifier.weight(1f))
        ToolButton("↩️", "Deshacer", false, onUndo)
        ToolButton("🗑️", "Limpiar", false, onClear)
    }
}

@Composable
private fun ToolButton(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0x44FFFFFF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 24.sp)
        Text(label, fontSize = 11.sp, color = Color.White)
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp).clip(CircleShape).background(color)
            .border(if (selected) 3.dp else 1.dp, if (selected) Color.White else Color(0x88FFFFFF), CircleShape)
            .clickable(onClick = onClick)
    )
}
