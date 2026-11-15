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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }
    private var entriesListener: ListenerRegistration? = null
    private var familyListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("child_journal", MODE_PRIVATE)

        // Anonymous Firebase user: enable Anonymous provider in Firebase Console.
        auth.signInAnonymously().addOnFailureListener {
            runOnUiThread { firebaseError = "Firebase вход: ${it.localizedMessage}" }
        }

        setContent {
            MaterialTheme {
                var records by remember { mutableStateOf(loadRecords(prefs.getString("records", "[]") ?: "[]")) }
                var selectedChild by remember { mutableIntStateOf(0) }
                var childNames by remember {
                    mutableStateOf(listOf(
                        prefs.getString("child_name_0", "Ребёнок 1") ?: "Ребёнок 1",
                        prefs.getString("child_name_1", "Ребёнок 2") ?: "Ребёнок 2"
                    ))
                }
                var showNamesDialog by remember { mutableStateOf(false) }
                var dialog by remember { mutableStateOf<String?>(null) }
                var syncing by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }

                DisposableEffect(Unit) {
                    entriesListener = db.collection("families").document("shared")
                        .collection("entries").addSnapshotListener { snapshot, e ->
                            if (e != null) {
                                error = "Firestore: ${e.localizedMessage}"
                                syncing = false
                                return@addSnapshotListener
                            }
                            val loaded = snapshot?.documents.orEmpty().mapNotNull { doc ->
                                val d = doc.data ?: return@mapNotNull null
                                val child = (d["childIndex"] as? Number)?.toInt() ?: return@mapNotNull null
                                val kind = d["kind"] as? String ?: return@mapNotNull null
                                val value = d["value"] as? String ?: return@mapNotNull null
                                val note = d["note"] as? String ?: ""
                                val date = (d["date"] as? Timestamp)?.toDate()?.time ?: return@mapNotNull null
                                JournalEntry(child, kind, value, note, date, doc.id)
                            }
                            records = loaded.sortedByDescending { it.time }
                            prefs.edit().putString("records", saveRecords(records)).apply()
                            syncing = false
                            error = null
                        }

                    familyListener = db.collection("families").document("shared")
                        .addSnapshotListener { snapshot, e ->
                            if (e != null) {
                                error = "Имена: ${e.localizedMessage}"
                                return@addSnapshotListener
                            }
                            val names = snapshot?.get("children") as? List<*>
                            if (names != null && names.size == 2 && names.all { it is String }) {
                                childNames = names.filterIsInstance<String>()
                                prefs.edit().putString("child_name_0", childNames[0])
                                    .putString("child_name_1", childNames[1]).apply()
                            }
                        }

                    onDispose {
                        entriesListener?.remove()
                        familyListener?.remove()
                    }
                }

                JournalScreen(
                    selectedChild = selectedChild,
                    onChildSelected = { selectedChild = it },
                    records = records.filter { it.child == selectedChild }.sortedByDescending { it.time },
                    childNames = childNames,
                    onEditNames = { showNamesDialog = true },
                    onDelete = { entry ->
                        db.collection("families").document("shared").collection("entries")
                            .document(entry.id).delete()
                            .addOnFailureListener { error = "Удаление: ${it.localizedMessage}" }
                    },
                    onTemperatureClick = { dialog = "temp" },
                    onMedicineClick = { dialog = "med" }
                )

                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    if (syncing) Text("Синхронизация…", style = MaterialTheme.typography.labelSmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                    firebaseError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall) }
                }

                if (showNamesDialog) {
                    ChildNamesDialog(childNames, { showNamesDialog = false }) { first, second ->
                        val names = listOf(first, second)
                        db.collection("families").document("shared")
                            .set(mapOf("children" to names), SetOptions.merge())
                            .addOnFailureListener { error = "Имена: ${it.localizedMessage}" }
                        childNames = names
                        prefs.edit().putString("child_name_0", first).putString("child_name_1", second).apply()
                        showNamesDialog = false
                    }
                }

                when (dialog) {
                    "temp" -> TemperatureDialog({ dialog = null }) { value, note ->
                        writeEntry(selectedChild, "temperature", "${value} °C", note) { error = it }
                        dialog = null
                    }
                    "med" -> MedicineDialog({ dialog = null }) { name, dose ->
                        writeEntry(selectedChild, "medicine", "$name — $dose", "") { error = it }
                        dialog = null
                    }
                }
            }
        }
    }

    private fun writeEntry(child: Int, kind: String, value: String, note: String, onError: (String) -> Unit) {
        val id = UUID.randomUUID().toString()
        val data = mapOf("id" to id, "childIndex" to child, "kind" to kind,
            "value" to value, "note" to note, "date" to Timestamp(Date()))
        db.collection("families").document("shared").collection("entries")
            .document(id).set(data).addOnFailureListener { onError("Сохранение: ${it.localizedMessage}") }
    }

    override fun onDestroy() {
        entriesListener?.remove()
        familyListener?.remove()
        super.onDestroy()
    }

    companion object {
        @Volatile var firebaseError: String? = null
    }
}

data class JournalEntry(val child: Int, val type: String, val value: String,
    val detail: String, val time: Long, val id: String)

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
    records.forEach { e -> array.put(JSONObject().apply {
        put("child", e.child); put("type", e.type); put("value", e.value)
        put("detail", e.detail); put("time", e.time); put("id", e.id)
    }) }
    return array.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(selectedChild: Int, onChildSelected: (Int) -> Unit, records: List<JournalEntry>,
    childNames: List<String>, onEditNames: () -> Unit, onDelete: (JournalEntry) -> Unit,
    onTemperatureClick: () -> Unit, onMedicineClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.getDefault()) }
    Scaffold(topBar = { TopAppBar(title = { Text("Детский журнал") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (0..1).forEach { index ->
                    SegmentedButton(selected = selectedChild == index, onClick = { onChildSelected(index) },
                        shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(childNames[index]) }
                }
            }
            TextButton(onClick = onEditNames, modifier = Modifier.align(Alignment.End)) { Text("Изменить имена детей") }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onTemperatureClick, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Thermostat, null); Spacer(Modifier.width(6.dp)); Text("Температура")
                }
                Button(onClick = onMedicineClick, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.MedicalServices, null); Spacer(Modifier.width(6.dp)); Text("Лекарство")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("История", style = MaterialTheme.typography.titleLarge); Spacer(Modifier.weight(1f))
                Text("${records.size} записей", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (records.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Записей пока нет.\nДобавьте первое измерение или лекарство.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(records, key = { it.id }) { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (entry.type == "temperature" || entry.type == "temp") Icons.Default.Thermostat else Icons.Default.MedicalServices,
                                    null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                                    Text(entry.value, style = MaterialTheme.typography.titleMedium)
                                    if (entry.detail.isNotBlank()) Text(entry.detail)
                                    Text(dateFormat.format(Date(entry.time)), style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { onDelete(entry) }) { Text("Удалить") }
                            }
                        }
                    }
                }
            }
            Text("Данные синхронизируются через Firebase", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun TemperatureDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var value by remember { mutableStateOf("36,6") }; var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Измерение температуры") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value, { value = it }, label = { Text("Температура, °C") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(note, { note = it }, label = { Text("Комментарий (необязательно)") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = {
            val n = value.replace(',', '.').toFloatOrNull()
            if (n != null && n in 30f..45f) onSave(n.toString().replace('.', ','), note)
        }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun MedicineDialog(onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf("Парацетамол") }; var dose by remember { mutableStateOf("250") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Приём лекарства") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Название препарата") }, singleLine = true)
            OutlinedTextField(dose, { dose = it }, label = { Text("Дозировка, например 5 мл") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank() && dose.isNotBlank()) onSave(name.trim(), dose.trim()) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
fun ChildNamesDialog(initialNames: List<String>, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var first by remember { mutableStateOf(initialNames.getOrElse(0) { "Ребёнок 1" }) }
    var second by remember { mutableStateOf(initialNames.getOrElse(1) { "Ребёнок 2" }) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Имена детей") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(first, { first = it }, label = { Text("Первый ребёнок") }, singleLine = true)
            OutlinedTextField(second, { second = it }, label = { Text("Второй ребёнок") }, singleLine = true)
        } },
        confirmButton = { TextButton(onClick = { if (first.isNotBlank() && second.isNotBlank()) onSave(first.trim(), second.trim()) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
