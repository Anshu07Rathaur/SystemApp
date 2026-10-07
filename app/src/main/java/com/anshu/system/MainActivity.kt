package com.anshu.system

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

val Cyan = Color(0xFF00E5FF)
val Bg = Color(0xFF050B14)
val Panel = Color(0xFF0B1B2B)
val Mono = FontFamily.Monospace

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

@Composable
fun App() {
    val c = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { while (true) { Store.settle(c); tick++; delay(1000) } }
    val now = System.currentTimeMillis()
    val qs = remember(tick) { Store.quests(c) }
    val today = LocalDate.now().toString()
    val lastDay = LocalDate.now().plusDays(2).toString()

    MaterialTheme(colorScheme = darkColorScheme(primary = Cyan, onPrimary = Color.Black, background = Bg, surface = Bg)) {
        Surface(Modifier.fillMaxSize(), color = Bg) {
            Column(Modifier.systemBarsPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("[ SYSTEM ]", color = Cyan, fontSize = 24.sp, fontFamily = Mono, fontWeight = FontWeight.Bold)
                Text(
                    "Reward: ${Store.balance(c) / 60000} min  |  Pending penalty: -${Store.penalty(c)} min",
                    fontFamily = Mono, fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("QUESTS", "HISTORY", "SETUP").forEachIndexed { i, t ->
                        Button(
                            onClick = { tab = i }, modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tab == i) Cyan else Color(0xFF263645),
                                contentColor = if (tab == i) Color.Black else Color.White
                            )
                        ) { Text(t, fontSize = 12.sp) }
                    }
                }
                when (tab) {
                    0 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("+ NEW QUEST") }
                        }
                        items(qs.filter { it.date in today..lastDay }.sortedBy { it.deadline }, key = { it.id }) { q ->
                            QuestCard(q, now, c) { tick++ }
                        }
                    }
                    1 -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { Text("Purana data 7 din baad auto-delete hota hai.", fontSize = 12.sp, fontFamily = Mono) }
                        items(qs.filter { it.date < today }.sortedByDescending { it.deadline }, key = { it.id }) { q ->
                            Box(Modifier.fillMaxWidth().border(1.dp, Cyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("▸ ${q.title}", color = Cyan, fontFamily = Mono, fontWeight = FontWeight.Bold)
                                    Text("${q.date}  •  ${q.status}  •  Total ${fmt(q.spent(now))}", fontSize = 12.sp, fontFamily = Mono)
                                    q.sessions.forEachIndexed { i, s ->
                                        Text("  #${i + 1}  ${hm(s[0])} – ${hm(s[1])}  (${fmt(s[1] - s[0])})", fontSize = 12.sp, fontFamily = Mono)
                                    }
                                }
                            }
                        }
                    }
                    else -> SetupTab(c)
                }
            }
        }
        if (showAdd) AddDialog({ showAdd = false }) { title, day, hh, mm, r, p ->
            val d = LocalDate.now().plusDays(day.toLong())
            val dl = d.atTime(hh, mm).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            if (dl <= System.currentTimeMillis()) {
                Toast.makeText(c, "Deadline past me hai", Toast.LENGTH_SHORT).show()
            } else {
                Store.add(c, title, d.toString(), dl, r, p); showAdd = false; tick++
            }
        }
    }
}

@Composable
fun QuestCard(q: Quest, now: Long, c: Context, refresh: () -> Unit) {
    val today = LocalDate.now().toString()
    val running = q.runStart > 0
    Box(Modifier.fillMaxWidth().border(1.dp, Cyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("▸ ${q.title}", color = Cyan, fontFamily = Mono, fontWeight = FontWeight.Bold)
            Text("${q.date}  •  due ${hm(q.deadline)}  •  ${q.status}", fontSize = 12.sp, fontFamily = Mono)
            Text("Reward +${q.reward}m   Fail -${q.penalty}m", fontSize = 12.sp, fontFamily = Mono)
            Text(
                "Tracked: ${fmt(q.spent(now))}" +
                    (if (q.status == "PENDING" && q.date == today) "   Left: ${fmt(q.deadline - now)}" else ""),
                fontSize = 12.sp, fontFamily = Mono
            )
            if (q.status == "PENDING") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (q.date == today) {
                    Button(onClick = { Store.toggle(c, q.id); refresh() }) { Text(if (running) "STOP" else "START") }
                    Button(onClick = { Store.complete(c, q.id); refresh() }) { Text("DONE") }
                } else {
                    TextButton(onClick = { Store.delete(c, q.id); refresh() }) { Text("DELETE") }
                }
            }
        }
    }
}

@Composable
fun AddDialog(onDismiss: () -> Unit, onAdd: (String, Int, Int, Int, Int, Int) -> Unit) {
    val c = LocalContext.current
    var title by remember { mutableStateOf("") }
    var day by remember { mutableIntStateOf(0) }
    var h by remember { mutableIntStateOf(21) }
    var m by remember { mutableIntStateOf(0) }
    var r by remember { mutableStateOf("10") }
    var p by remember { mutableStateOf("5") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Quest") },
        confirmButton = {
            TextButton(onClick = {
                if (title.isNotBlank()) onAdd(title.trim(), day, h, m, r.toIntOrNull() ?: 10, p.toIntOrNull() ?: 5)
            }) { Text("ADD") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("CANCEL") } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Task") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Today", "+1", "+2").forEachIndexed { i, t ->
                        Button(
                            onClick = { day = i },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (day == i) Cyan else Color(0xFF263645),
                                contentColor = if (day == i) Color.Black else Color.White
                            )
                        ) { Text(t, fontSize = 12.sp) }
                    }
                }
                Button(onClick = {
                    TimePickerDialog(c, { _, hh, mm -> h = hh; m = mm }, h, m, true).show()
                }) { Text("Deadline %02d:%02d".format(h, m)) }
                OutlinedTextField(
                    value = r, onValueChange = { r = it }, label = { Text("Reward (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = p, onValueChange = { p = it }, label = { Text("Penalty (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
    )
}

@Composable
fun SetupTab(c: Context) {
    val apps = remember {
        val pm = c.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .distinctBy { it.first }
            .filter { it.first != c.packageName }
            .sortedBy { it.second.lowercase() }
    }
    var blk by remember { mutableStateOf(Store.blocked(c)) }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                c.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}")))
            }) { Text("1. App info → ⋮ → Allow restricted settings") }
        }
        item {
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                c.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }) { Text("2. Accessibility → System → ON") }
        }
        item {
            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                c.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }) { Text("3. Battery → System → No restrictions") }
        }
        item { Text("Block karne wale apps:", color = Cyan, fontFamily = Mono) }
        items(apps, key = { it.first }) { (pkg, name) ->
            Row(
                Modifier.fillMaxWidth().clickable {
                    blk = if (pkg in blk) blk - pkg else blk + pkg
                    Store.setBlocked(c, blk)
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = pkg in blk, onCheckedChange = null)
                Text(name, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
