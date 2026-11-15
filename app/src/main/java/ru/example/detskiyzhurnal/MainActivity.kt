package ru.example.detskiyzhurnal

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("child_journal", MODE_PRIVATE)
        setContent {
            MaterialTheme {
                var records by remember { mutableStateOf(loadRecords(prefs.getString("records", "[]") ?: "[]")) }
                var selectedChild by remember { mutableIntStateOf(0) }
                var dialog by remember { mutableStateOf<String?>(null) }

                fun persist(updated: List<JournalEntry>) {
                    records = updated.sortedByDescending { it.time }
                    prefs.edit().putString("records", saveRecords(records)).apply()
                }

                JournalScreen(
                    selectedChild = selectedChild,
                    onChildSelected = { selectedChild = it },
                    records = records.filter { it.child == selectedChild },
                    onDelete = { entry -> persist(records.filterNot { it.id == entry.id }) },
                    onTemperatureClick = { dialog = "temp" },
                    onMedicineClick = { dialog = "med" }
                )
                when (dialog) {
                    "temp" -> TemperatureDialog(
                        onDismiss = { dialog = null },
                        onSave = { value, note ->
                            persist(records + JournalEntry(selectedChild, "temp", value, note, System.currentTimeMillis()))
                            dialog = null
                        }
                    )
                    "med" -> MedicineDialog(
                        onDismiss = { dialog = null },
                        onSave = { name, dose ->
                            persist(records + JournalEntry(selectedChild, "med", name, dose, System.currentTimeMillis()))
                            dialog = null
                        }
                    )
                }
            }
        }
    }
}

data class JournalEntry(
    val child: Int, val type: String, val value: String,
    val detail: String, val time: Long,
    val id: String = "${child}_${time}_${(0..999999).random()}"
)

private fun loadRecords(raw: String): List<JournalEntry> = runCatching {
    val array = JSONArray(raw)
    (0 until array.length()).map { i ->
        val o = array.getJSONObject(i)
        JournalEntry(o.optInt("child"), o.optString("type"), o.optString("value"),
            o.optString("detail"), o.optLong("time"), o.optString("id", "${i}_${o.optLong("time")}"))
    }
}.getOrDefault(emptyList())

private fun saveRecords(records: List<JournalEntry>): String {
    val array = JSONArray()
    records.forEach { e ->
        array.put(JSONObject().apply {
            put("child", e.child); put("type", e.type); put("value", e.value)
            put("detail", e.detail); put("time", e.time); put("id", e.id)
        })
    }
    return array.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(
    selectedChild: Int, onChildSelected: (Int) -> Unit, records: List<JournalEntry>,
    onDelete: (JournalEntry) -> Unit, onTemperatureClick: () -> Unit, onMedicineClick: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.getDefault()) }
    Scaffold(topBar = { TopAppBar(title = { Text("Детский журнал") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (0..1).forEach { index ->
                    SegmentedButton(selected = selectedChild == index,
                        onClick = { onChildSelected(index) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)) {
                        Text("Ребёнок ${index + 1}")
                    }
                }
            }
            Text("Добавить запись", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onTemperatureClick, modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Thermostat, contentDescription = null)
                    Spacer(Modifier.width(6.dp)); Text("Температура")
                }
                Button(onClick = onMedicineClick, modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.MedicalServices, contentDescription = null)
                    Spacer(Modifier.width(6.dp)); Text("Лекарство")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("История", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Text("${records.size} записей", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (records.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Записей пока нет.\nДобавьте первое измерение или лекарство.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(records, key = { it.id }) { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (entry.type == "temp") Icons.Default.Thermostat else Icons.Default.MedicalServices,
                                    contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(if (entry.type == "temp") "${entry.value} °C" else entry.value,
                                        style = MaterialTheme.typography.titleMedium)
                                    if (entry.detail.isNotBlank()) Text(entry.detail)
                                    Text(dateFormat.format(Date(entry.time)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { onDelete(entry) }) { Text("Удалить") }
                            }
                        }
                    }
                }
            }
            Text("Записи сохраняются на этом телефоне", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TemperatureDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var value by remember { mutableStateOf("36,6") }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Измерение температуры") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value, { value = it }, label = { Text("Температура, °C") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(note, { note = it }, label = { Text("Комментарий (необязательно)") },
                    singleLine = true)
                Text("Дата и время будут записаны автоматически.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val number = value.replace(',', '.').toFloatOrNull()
                if (number != null && number in 30f..45f) onSave(number.toString(), note)
            }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun MedicineDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Приём лекарства") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Название препарата") }, singleLine = true)
                OutlinedTextField(dose, { dose = it }, label = { Text("Дозировка, например 5 мл") }, singleLine = true)
                Text("Дата и время будут записаны автоматически.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name.trim(), dose.trim()) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
