package com.openclaw.workout

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.openclaw.workout.data.*
import com.openclaw.workout.ui.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

// PATCH-9: format session date (yyyy-MM-dd) as dd.MM for charts
fun formatSessionDate(sessionDate: String): String = try {
    java.time.LocalDate.parse(sessionDate, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE).format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"))
} catch (e: Exception) {
    sessionDate
}

// PATCH-9: sort sets by session date so backdated sessions appear in chronological order
fun List<WorkoutSetWithSessionDate>.sortedBySessionDate(): List<WorkoutSetWithSessionDate> = sortedBy { it.sessionDate }

fun formatDoubleOneDecimal(value: Double): String = "%.1f".format(value).replace(",", ".")

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent {
            val vm = viewModel<WorkoutViewModel>()
            val s by vm.state.collectAsState()
            val darkTheme = when (s.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            MaterialTheme(
                colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
            ) { App(vm) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun App(vm: WorkoutViewModel = viewModel()) {
    val nav = rememberNavController()
    val tabs = listOf(
        "plans" to "Планы",
        "reports" to "Отчёты",
        "exercises" to "Упражнения",
        "settings" to "Настройки"
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                val backStack by nav.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route
                tabs.forEach { (r, t) ->
                    NavigationBarItem(
                        selected = currentRoute == r,
                        onClick = { nav.navigate(r) { launchSingleTop = true; popUpTo(nav.graph.startDestinationId) { saveState = true }; restoreState = true } },
                        icon = { Icon(when(r) { "plans" -> Icons.Default.CalendarMonth; "reports" -> Icons.Default.BarChart; "exercises" -> Icons.Default.FitnessCenter; else -> Icons.Default.Settings }, t) },
                        label = { Text(t) }
                    )
                }
            }
        }
    ) { p ->
        NavHost(nav, "plans", Modifier.padding(p)) {
            composable("plans") { PlansScreen(vm, nav) }
            composable("new_workout") { NewWorkoutScreen(vm, nav) }
            composable("workout_execution/{sessionId}") { entry ->
                val sid = entry.arguments?.getString("sessionId") ?: ""
                WorkoutExecutionScreen(vm, nav, sid)
            }
            composable("exercise_execution/{workoutExerciseId}") { entry ->
                val weId = entry.arguments?.getString("workoutExerciseId") ?: ""
                ExerciseExecutionScreen(vm, nav, weId)
            }
            composable("view_session/{sessionId}") { entry ->
                val sid = entry.arguments?.getString("sessionId") ?: ""
                ViewSessionScreen(vm, nav, sid)
            }
            composable("reports") { ReportsScreen(vm, nav) }
            composable("report_exercise/{exerciseId}") { entry ->
                val eid = entry.arguments?.getString("exerciseId") ?: ""
                ReportExerciseScreen(vm, nav, eid)
            }
            composable("exercises") { ExercisesScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}

// ===================== PLANS SCREEN =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PlansScreen(vm: WorkoutViewModel, nav: NavHostController) {
    val s by vm.state.collectAsState()
    val planned by vm.plannedSessions.collectAsState()
    val completed by vm.completedSessions.collectAsState()
    val templates by vm.templates.collectAsState()
    var showTemplates by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Планы тренировок", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        val showTemplatePicker = remember { mutableStateOf(false) }
        val templates by vm.templates.collectAsState()
        Button(
            onClick = { showTemplatePicker.value = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Новая тренировка", fontSize = 18.sp)
        }
        if (showTemplatePicker.value) {
            AlertDialog(
                onDismissRequest = { showTemplatePicker.value = false },
                title = { Text("Выберите шаблон или создайте новую") },
                text = {
                    Column {
                        if (templates.isEmpty()) {
                            Text("Нет сохранённых шаблонов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyColumn(Modifier.heightIn(max = 300.dp)) {
                                items(templates) { tpl ->
                                    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable {
                                        vm.loadTemplateIntoPlan(tpl.id)
                                        showTemplatePicker.value = false
                                        nav.navigate("new_workout")
                                    }) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text(tpl.name, fontWeight = FontWeight.Bold)
                                            Text("Шаблон", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.clearPlan(); showTemplatePicker.value = false; nav.navigate("new_workout") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Создать с нуля") }
                    }
                },
                confirmButton = { TextButton(onClick = { showTemplatePicker.value = false }) { Text("Отмена") } }
            )
        }
        Spacer(Modifier.height(16.dp))

        Text("Запланированные", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        if (planned.isEmpty()) {
            Text("Нет запланированных тренировок", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn {
            items(planned) { session ->
                PlanCard(vm, session, nav, onDelete = { vm.deleteSession(session) })
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Завершённые", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        LazyColumn(Modifier.heightIn(max = 200.dp)) {
            items(completed.take(10)) { session ->
                CompletedCard(vm, session, nav, onReopen = { vm.reopenSession(session.id) })
            }
        }

        Spacer(Modifier.height(12.dp))
        Divider()
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Шаблоны", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = { showTemplates = !showTemplates }) { Text(if (showTemplates) "Скрыть" else "Показать") }
        }
        if (showTemplates) {
            LazyColumn(Modifier.heightIn(max = 200.dp)) {
                items(templates) { tpl ->
                    TemplateCard(vm, tpl, onEdit = {
                        vm.loadTemplateIntoPlan(tpl.id)
                        nav.navigate("new_workout")
                    }, onDelete = { vm.deleteTemplate(tpl) })
                }
            }
        }
    }
}

@Composable fun PlanCard(vm: WorkoutViewModel, session: WorkoutSessionEntity, nav: NavHostController, onDelete: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(session.date, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Тренировка", color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Button(
                onClick = { nav.navigate("workout_execution/${session.id}") },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Начать") }
            IconButton(onClick = { showConfirm = true }) { Icon(Icons.Default.Delete, "Удалить") }
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Удалить тренировку?") },
            text = { Text("Дата: ${session.date}. Это действие нельзя отменить.") },
            confirmButton = { TextButton(onClick = { onDelete(); showConfirm = false }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Отмена") } }
        )
    }
}

@Composable fun CompletedCard(vm: WorkoutViewModel, session: WorkoutSessionEntity, nav: NavHostController, onReopen: () -> Unit) {
    var showConfirm by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { nav.navigate("view_session/${session.id}") },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(session.date, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✓ Завершена", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { showConfirm = true }) { Text("✏️ Редактировать", color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
    }
    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("Отменить завершение?") },
            text = { Text("Тренировка ${session.date} снова появится в запланированных.") },
            confirmButton = { TextButton(onClick = { onReopen(); showConfirm = false }) { Text("Отменить завершение") } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Закрыть") } }
        )
    }
}

@Composable fun TemplateCard(vm: WorkoutViewModel, tpl: WorkoutTemplateEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(tpl.name, fontWeight = FontWeight.Bold) }
            TextButton(onClick = onEdit) { Text("Редактировать") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Удалить") }
        }
    }
}

// ===================== NEW WORKOUT SCREEN (PATCH-1, PATCH-2, PATCH-3) =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NewWorkoutScreen(vm: WorkoutViewModel, nav: NavHostController) {
    val s by vm.state.collectAsState()
    var showExercisePicker by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }
    var showSaveTemplate by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад") }
            Text("Новая тренировка", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))

        // PATCH-1: DatePicker readOnly with calendar icon
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        val datePickerDialog = remember {
            DatePickerDialog(context, { _, y, m, d ->
                val dateStr = String.format("%04d-%02d-%02d", y, m + 1, d)
                vm.setDate(dateStr)
            }, year, month, day)
        }
        OutlinedTextField(
            value = s.date,
            onValueChange = {},
            label = { Text("Дата") },
            modifier = Modifier.fillMaxWidth().clickable { datePickerDialog.show() },
            readOnly = true,
            trailingIcon = {
                Icon(Icons.Default.CalendarMonth, null, Modifier.clickable { datePickerDialog.show() })
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(Modifier.height(12.dp))

        // Selected exercises
        Text("Упражнения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(s.planExercises) { idx, item ->
                PlanExerciseCard(vm, idx, item)
            }
            item {
                OutlinedButton(
                    onClick = { showExercisePicker = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Добавить упражнение") }
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { vm.savePlan(); nav.popBackStack() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Сохранить план") }
            OutlinedButton(
                onClick = { showSaveTemplate = !showSaveTemplate },
                modifier = Modifier.weight(1f)
            ) { Text("Сохранить как шаблон") }
        }
        if (showSaveTemplate) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = templateName, onValueChange = { templateName = it },
                    label = { Text("Название шаблона") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (templateName.isNotBlank()) { vm.saveAsTemplate(templateName); templateName = ""; showSaveTemplate = false } }) { Text("OK") }
            }
        }
        if (s.message.isNotEmpty()) { Spacer(Modifier.height(4.dp)); Text(s.message, color = MaterialTheme.colorScheme.primary) }
    }

    if (showExercisePicker) {
        AlertDialog(
            onDismissRequest = { showExercisePicker = false },
            title = { Text("Выбрать упражнение") },
            text = {
                Column {
                    OutlinedTextField(
                        value = s.query, onValueChange = { vm.setQuery(it) },
                        label = { Text("Поиск") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        val filtered = s.exercises.filter { it.name.contains(s.query, true) || it.muscleGroup.contains(s.query, true) }
                        items(filtered) { ex ->
                            Card(Modifier.fillMaxWidth().padding(2.dp).clickable {
                                vm.selectExercise(ex)
                            }) {
                                Column(Modifier.padding(8.dp)) {
                                    Text(ex.name, fontWeight = FontWeight.Bold)
                                    Text(ex.muscleGroup, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    s.selectedExercise?.let { ex ->
                        Spacer(Modifier.height(8.dp))
                        // PATCH-10: distinct variant names + always offer "Базовый" first
                        Text("Варианты:", fontWeight = FontWeight.Bold)
                        LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                AssistChip(onClick = {
                                    vm.addExerciseToPlan(ex.id, null)
                                    showExercisePicker = false
                                }, label = { Text("Базовый") })
                            }
                            items(s.variants.distinctBy { it.name }) { v ->
                                AssistChip(onClick = {
                                    vm.addExerciseToPlan(ex.id, v.id)
                                    showExercisePicker = false
                                }, label = { Text(v.name) })
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showExercisePicker = false }) { Text("Закрыть") } }
        )
    }
}

@Composable fun PlanExerciseCard(vm: WorkoutViewModel, index: Int, item: PlanExerciseItem) {
    var showWeightInput by remember { mutableStateOf(false) }
    var showRepsInput by remember { mutableStateOf(false) }
    var showSetsInput by remember { mutableStateOf(false) }
    var showRestInput by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(item.exerciseName, fontWeight = FontWeight.Bold)
                if (item.variantName != "Базовый") Text(item.variantName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { vm.removePlanExercise(index) }) { Icon(Icons.Default.Close, "Удалить") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // FEATURE-1: Weight stepper (+/- 0.5) with optional direct input
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("Вес", style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { vm.updatePlanExercise(index, weight = kotlin.math.max(0.0, item.weight - 0.5)) }) { Icon(Icons.Default.Remove, null) }
                        Text(
                            text = if (item.weight == 0.0) "—" else "${item.weight} кг",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 48.dp).clickable { showWeightInput = true },
                            textAlign = TextAlign.Center
                        )
                        IconButton(onClick = { vm.updatePlanExercise(index, weight = item.weight + 0.5) }) { Icon(Icons.Default.Add, null) }
                    }
                }
                // FEATURE-1: Reps stepper (+/- 1) with optional direct input
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("Повторы", style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { vm.updatePlanExercise(index, reps = kotlin.math.max(0, item.reps - 1)) }) { Icon(Icons.Default.Remove, null) }
                        Text(
                            text = "${item.reps}",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 32.dp).clickable { showRepsInput = true },
                            textAlign = TextAlign.Center
                        )
                        IconButton(onClick = { vm.updatePlanExercise(index, reps = item.reps + 1) }) { Icon(Icons.Default.Add, null) }
                    }
                }
                // FEATURE-1: Sets stepper (+/- 1) with optional direct input
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Text("Подходы", style = MaterialTheme.typography.labelSmall)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        IconButton(onClick = { vm.updatePlanExercise(index, sets = kotlin.math.max(1, item.sets - 1)) }) { Icon(Icons.Default.Remove, null) }
                        Text(
                            text = "${item.sets}",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.widthIn(min = 32.dp).clickable { showSetsInput = true },
                            textAlign = TextAlign.Center
                        )
                        IconButton(onClick = { vm.updatePlanExercise(index, sets = item.sets + 1) }) { Icon(Icons.Default.Add, null) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // FEATURE-1: Rest stepper (+/- 30) with optional direct input
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Отдых: ", style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = { vm.updatePlanExercise(index, restSeconds = kotlin.math.max(0, item.restSeconds - 30)) }) { Icon(Icons.Default.Remove, null) }
                Text(
                    text = "${item.restSeconds}с",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(56.dp).clickable { showRestInput = true },
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { vm.updatePlanExercise(index, restSeconds = kotlin.math.min(600, item.restSeconds + 30)) }) { Icon(Icons.Default.Add, null) }
            }
        }
    }

    if (showWeightInput) {
        NumberInputDialog(
            title = "Вес (кг)",
            initial = if (item.weight == 0.0) "" else item.weight.toString(),
            keyboardType = KeyboardType.Decimal,
            onDismiss = { showWeightInput = false },
            onConfirm = { vm.updatePlanExercise(index, weight = it.toDoubleOrNull() ?: 0.0); showWeightInput = false }
        )
    }
    if (showRepsInput) {
        NumberInputDialog(
            title = "Повторы",
            initial = item.reps.toString(),
            keyboardType = KeyboardType.Number,
            onDismiss = { showRepsInput = false },
            onConfirm = { vm.updatePlanExercise(index, reps = it.toIntOrNull() ?: item.reps); showRepsInput = false }
        )
    }
    if (showSetsInput) {
        NumberInputDialog(
            title = "Подходы",
            initial = item.sets.toString(),
            keyboardType = KeyboardType.Number,
            onDismiss = { showSetsInput = false },
            onConfirm = { vm.updatePlanExercise(index, sets = (it.toIntOrNull() ?: item.sets).coerceAtLeast(1)); showSetsInput = false }
        )
    }
    if (showRestInput) {
        NumberInputDialog(
            title = "Отдых (сек)",
            initial = item.restSeconds.toString(),
            keyboardType = KeyboardType.Number,
            onDismiss = { showRestInput = false },
            onConfirm = { vm.updatePlanExercise(index, restSeconds = (it.toIntOrNull() ?: item.restSeconds).coerceAtLeast(0)); showRestInput = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NumberInputDialog(
    title: String,
    initial: String,
    keyboardType: KeyboardType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(value) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

// FEATURE-3+4 REDESIGN: reusable inline weight/reps editor
@Composable
fun WeightRepsInput(
    weight: Double,
    reps: Int,
    onWeightChange: (Double) -> Unit,
    onRepsChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var weightText by remember(weight) { mutableStateOf(formatDoubleOneDecimal(weight)) }
    var repsText by remember(reps) { mutableStateOf(reps.toString()) }

    Column(modifier) {
        // Weight
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Вес: ", fontWeight = FontWeight.Medium)
            IconButton(onClick = {
                val new = (weight - 0.5).coerceAtLeast(0.0)
                onWeightChange(new)
                weightText = formatDoubleOneDecimal(new)
            }) { Icon(Icons.Default.Remove, "Меньше") }
            OutlinedTextField(
                value = weightText,
                onValueChange = {
                    weightText = it
                    it.toDoubleOrNull()?.let(onWeightChange)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.width(80.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            IconButton(onClick = {
                val new = weight + 0.5
                onWeightChange(new)
                weightText = formatDoubleOneDecimal(new)
            }) { Icon(Icons.Default.Add, "Больше") }
            Text(" кг", fontWeight = FontWeight.Medium)
        }
        // Reps
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Повторы: ", fontWeight = FontWeight.Medium)
            IconButton(onClick = {
                val new = (reps - 1).coerceAtLeast(0)
                onRepsChange(new)
                repsText = new.toString()
            }) { Icon(Icons.Default.Remove, "Меньше") }
            OutlinedTextField(
                value = repsText,
                onValueChange = {
                    repsText = it
                    it.toIntOrNull()?.let(onRepsChange)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.width(64.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            IconButton(onClick = {
                val new = reps + 1
                onRepsChange(new)
                repsText = new.toString()
            }) { Icon(Icons.Default.Add, "Больше") }
        }
    }
}

// ===================== WORKOUT EXECUTION SCREEN =====================
@Composable fun WorkoutExecutionScreen(vm: WorkoutViewModel, nav: NavHostController, sessionId: String) {
    val s by vm.state.collectAsState()
    val session = s.currentSession ?: plannedSessionLookup(vm, sessionId)

    LaunchedEffect(sessionId) {
        if (s.currentSession?.id != sessionId) {
            session?.let { vm.startWorkout(it) }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад") }
            Text("Тренировка ${session?.date ?: ""}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))

        val exercises = s.workoutExercises
        LazyColumn(Modifier.weight(1f)) {
            items(exercises) { we ->
                val sets by vm.sets(we.id).collectAsState(emptyList())
                val allDone = sets.isNotEmpty() && sets.all { it.isCompleted }
                val exName = s.exercises.find { it.id == we.exerciseId }?.name ?: we.exerciseId.take(8)
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { nav.navigate("exercise_execution/${we.id}") },
                    colors = CardDefaults.cardColors(
                        containerColor = if (allDone) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(exName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            Text("${sets.size} подходов · ${if (allDone) "✓ Готово" else "${sets.count { it.isCompleted }}/${sets.size} выполнено"}", color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        if (allDone) Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        else Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { vm.finishWorkout(); nav.popBackStack() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) { Text("Завершить тренировку", fontSize = 18.sp) }
    }
}

@Composable fun plannedSessionLookup(vm: WorkoutViewModel, sessionId: String): WorkoutSessionEntity? {
    val planned by vm.plannedSessions.collectAsState()
    val completed by vm.completedSessions.collectAsState()
    return planned.find { it.id == sessionId } ?: completed.find { it.id == sessionId }
}

// ===================== EXERCISE EXECUTION SCREEN (PATCH-9) =====================
@Composable fun ExerciseExecutionScreen(vm: WorkoutViewModel, nav: NavHostController, workoutExerciseId: String) {
    val s by vm.state.collectAsState()
    val sets by vm.sets(workoutExerciseId).collectAsState(emptyList())
    val we = s.workoutExercises.find { it.id == workoutExerciseId }
    var restSeconds by remember(workoutExerciseId) { mutableStateOf(we?.restSeconds ?: 60) }
    var isSetInProgress by remember { mutableStateOf(false) }
    var restTimer by remember { mutableStateOf(0) }
    var restActive by remember { mutableStateOf(false) }

    var currentWeight by remember { mutableStateOf(0.0) }
    var currentReps by remember { mutableStateOf(0) }

    var showSupplementalInput by remember { mutableStateOf(false) }
    var supplementalSetId by remember { mutableStateOf("") }
    var supplementalWeight by remember { mutableStateOf(0.0) }
    var supplementalReps by remember { mutableStateOf(0) }

    val exName = s.exercises.find { it.id == we?.exerciseId }?.name ?: "Упражнение"
    val allDone = sets.isNotEmpty() && sets.all { it.isCompleted }
    val currentSet = sets.firstOrNull { !it.isCompleted }

    LaunchedEffect(currentSet?.id, isSetInProgress) {
        currentSet?.let {
            currentWeight = it.weight
            currentReps = it.reps
        }
    }

    LaunchedEffect(restActive) {
        while (restActive && restTimer > 0) {
            delay(1000)
            restTimer--
            if (restTimer <= 0) { restActive = false }
        }
    }

    LaunchedEffect(restSeconds, we) {
        if (we != null && restSeconds != (we.restSeconds)) {
            vm.updateRestSeconds(we.id, restSeconds)
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад") }
            Text(exName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))

        // Sets list — PATCH-9 colors + FEATURE-4 segments
        LazyColumn(Modifier.weight(1f)) {
            items(sets) { set ->
                val segments by vm.segments(set.id).collectAsState(emptyList())
                val isCurrent = set.id == currentSet?.id
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            set.isCompleted -> MaterialTheme.colorScheme.primaryContainer
                            isCurrent -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Подход ${set.setIndex + 1}${if (set.isSupplemental) " (доп.)" else ""}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("${set.weight} кг × ${set.reps} повт.", fontSize = 16.sp)
                            }
                            if (set.isCompleted) {
                                Icon(Icons.Default.CheckCircle, "Готово", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                            }
                        }
                        // FEATURE-4: show supplemental segments under the main set
                        if (segments.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            segments.forEachIndexed { idx, seg ->
                                Card(
                                    Modifier.fillMaxWidth().padding(start = 20.dp, top = 2.dp, bottom = 2.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                                ) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "Сегмент ${idx + 1}: ${seg.weight} кг × ${seg.reps} повт.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text("+", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Rest timer display
        if (restActive) {
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Отдых", style = MaterialTheme.typography.titleMedium)
                    Text("${restTimer}с", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                    TextButton(onClick = { restActive = false; restTimer = 0 }) { Text("Пропустить") }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (!isSetInProgress && !restActive) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Отдых: ", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                IconButton(onClick = { if (restSeconds > 10) { restSeconds -= 10 } }) { Icon(Icons.Default.Remove, null) }
                Text("${restSeconds}с", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.width(60.dp), textAlign = TextAlign.Center)
                IconButton(onClick = { if (restSeconds < 300) { restSeconds += 10 } }) { Icon(Icons.Default.Add, null) }
            }
        }

        // FEATURE-3+4 REDESIGN: inline weight/reps editor + finish button
        if (allDone) {
            Button(
                onClick = { nav.popBackStack() },
                modifier = Modifier.fillMaxWidth().height(72.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
            ) { Text("Готово ✓", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        } else if (isSetInProgress) {
            // Inline editors above finish button
            WeightRepsInput(
                weight = currentWeight,
                reps = currentReps,
                onWeightChange = { currentWeight = it },
                onRepsChange = { currentReps = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
            Button(
                onClick = {
                    currentSet?.let { set ->
                        vm.completeSet(set.id, currentWeight, currentReps)
                        isSetInProgress = false
                        showSupplementalInput = false
                        restTimer = restSeconds
                        restActive = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("ЗАВЕРШИТЬ ПОДХОД", fontSize = 28.sp, fontWeight = FontWeight.Bold) }
        } else {
            Button(
                onClick = {
                    isSetInProgress = true
                    showSupplementalInput = false
                },
                modifier = Modifier.fillMaxWidth().height(72.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Text("Старт", fontSize = 24.sp, fontWeight = FontWeight.Bold) }
        }

        // FEATURE-4: supplemental segments input (only when a set is complete / after set finished)
        if ((allDone || !isSetInProgress) && !restActive && sets.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            if (showSupplementalInput) {
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        WeightRepsInput(
                            weight = supplementalWeight,
                            reps = supplementalReps,
                            onWeightChange = { supplementalWeight = it },
                            onRepsChange = { supplementalReps = it }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    if (supplementalSetId.isNotBlank()) {
                                        vm.addSegment(supplementalSetId, supplementalWeight, supplementalReps)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Сохранить сегмент") }
                            OutlinedButton(
                                onClick = { showSupplementalInput = false },
                                modifier = Modifier.weight(1f)
                            ) { Text("Готово") }
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = {
                        val mainSet = sets.lastOrNull { !it.isSupplemental }
                        mainSet?.let {
                            supplementalSetId = it.id
                            supplementalWeight = it.weight
                            supplementalReps = it.reps
                            showSupplementalInput = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("+ Доп. повторения") }
            }
        }
    }
}

// ===================== VIEW SESSION SCREEN (PATCH-4) =====================
@Composable fun ViewSessionScreen(vm: WorkoutViewModel, nav: NavHostController, sessionId: String) {
    val s by vm.state.collectAsState()

    LaunchedEffect(sessionId) {
        vm.loadSessionForView(sessionId)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Назад") }
            Text("Просмотр: ${s.viewSession?.date ?: ""}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))

        if (s.viewSession == null) {
            Text("Тренировка не найдена", color = MaterialTheme.colorScheme.error)
        } else {
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Дата: ${s.viewSession!!.date}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("✓ Завершена", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Упражнения:", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(s.viewSessionExercises) { vse ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(vse.exerciseName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (vse.variantName != "Базовый") Text(vse.variantName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp))
                            vse.sets.forEach { set ->
                                val segments by vm.segments(set.id).collectAsState(emptyList())
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Подход ${set.setIndex + 1}${if (set.isSupplemental) " (доп.)" else ""}: ${set.weight} кг × ${set.reps} повт.")
                                    if (set.isCompleted) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                                segments.forEachIndexed { idx, seg ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(start = 16.dp, top = 1.dp, bottom = 1.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "  Сегмент ${idx + 1}: ${seg.weight} кг × ${seg.reps} повт.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                    }
                                }
                            }
                            if (vse.sets.isEmpty()) {
                                Text("Нет подходов", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===================== REPORTS SCREEN (PATCH-10) =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReportsScreen(vm: WorkoutViewModel, nav: NavHostController) {
    val s by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Отчёты", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Поиск") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Default.Search, null) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(Modifier.height(8.dp))

        val filtered = s.exercises.filter { it.name.contains(query, true) || it.muscleGroup.contains(query, true) }
        LazyColumn(Modifier.weight(1f)) {
            if (filtered.isEmpty()) {
                item {
                    Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            } else {
                items(filtered) { ex ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable {
                            nav.navigate("report_exercise/${ex.id}")
                        }
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(ex.muscleGroup, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReportExerciseScreen(vm: WorkoutViewModel, nav: NavHostController, exerciseId: String) {
    val s by vm.state.collectAsState()
    var selectedVariantId by remember { mutableStateOf<String?>(null) }

    val selectedExercise = remember(exerciseId, s.exercises) {
        s.exercises.find { it.id == exerciseId }
    }
    val variants by vm.variants(exerciseId).collectAsState(emptyList())
    val sets by vm.exactSets(exerciseId, selectedVariantId).collectAsState(emptyList())
    val sortedSets = remember(sets) { sets.sortedBySessionDate() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedExercise?.name ?: "Отчёт") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (selectedExercise == null) {
                Text("Упражнение не найдено", color = MaterialTheme.colorScheme.error)
            } else {
                // Variant chips: "Базовый" + existing variants
                LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterChip(
                            selected = selectedVariantId == null,
                            onClick = { selectedVariantId = null },
                            label = { Text("Базовый") }
                        )
                    }
                    items(variants) { v ->
                        FilterChip(
                            selected = selectedVariantId == v.id,
                            onClick = { selectedVariantId = v.id },
                            label = { Text(v.name) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (sortedSets.isNotEmpty()) {
                    Text("Динамика веса и повторений", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    ChartCard(sortedSets)
                    Spacer(Modifier.height(12.dp))
                    Text("Последние тренировки", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Дата", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                        Text("Вес", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("Повторы", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("Статус", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                    Divider()
                    LazyColumn {
                        items(sortedSets.takeLast(20)) { set ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                val dateStr = set.sessionDate
                                Text(dateStr, modifier = Modifier.weight(1.5f))
                                Text("${set.weight}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("${set.reps}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                if (set.isCompleted) Text("✓", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                else Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            }
                        }
                    }
                } else {
                    Text("Нет данных", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable fun ChartCard(sets: List<WorkoutSetWithSessionDate>) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            val weights = sets.map { it.weight }
            val reps = sets.map { it.reps.toDouble() }
            val avg = sets.map { (it.weight + it.reps) / 2.0 }
            val dates = sets.map { formatSessionDate(it.sessionDate) }
            SimpleChart(weights, reps, avg, dates)
            Spacer(Modifier.height(8.dp))
            ChartLegend()
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Макс вес", style = MaterialTheme.typography.labelSmall)
                    Text("${weights.maxOrNull() ?: 0.0} кг", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Макс повт", style = MaterialTheme.typography.labelSmall)
                    Text("${reps.maxOrNull()?.toInt() ?: 0}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Всего сетов", style = MaterialTheme.typography.labelSmall)
                    Text("${sets.size}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable fun ChartLegend() {
    fun legendItem(color: Color, text: String): @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, shape = RoundedCornerShape(50)))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        legendItem(MaterialTheme.colorScheme.primary, "Вес (кг)")()
        legendItem(MaterialTheme.colorScheme.error, "Повторения")()
        legendItem(MaterialTheme.colorScheme.tertiary, "Среднее")()
    }
}

@Composable fun SimpleChart(weights: List<Double>, reps: List<Double>, avg: List<Double>, dates: List<String>) {
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val textColor = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(180.dp)) {
        val padLeft = 0f
        val padBottom = 28f
        val chartH = size.height - padBottom
        val w = size.width - padLeft
        val count = weights.size
        if (count < 2) return@Canvas

        fun drawSeries(vals: List<Double>, color: Color, stroke: Float) {
            val max = (vals.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
            vals.zipWithNext().forEachIndexed { i, (a, b) ->
                val x1 = padLeft + i * w / (count - 1)
                val x2 = padLeft + (i + 1) * w / (count - 1)
                val y1 = chartH - (a / max * chartH).toFloat()
                val y2 = chartH - (b / max * chartH).toFloat()
                drawLine(color, Offset(x1, y1), Offset(x2, y2), stroke)
            }
        }

        // vertical grid lines
        val steps = (count - 1).coerceAtMost(6)
        for (i in 0..steps) {
            val x = padLeft + i * w / steps
            drawLine(gridColor, Offset(x, 0f), Offset(x, chartH), 1f)
        }

        drawSeries(weights, Color(0xFF2196F3), 4f)
        drawSeries(reps, Color(0xFFF44336), 4f)
        drawSeries(avg, Color(0xFF4CAF50), 4f)

        // dates under x-axis
        val labelStep = (count / 7).coerceAtLeast(1)
        val paint = android.graphics.Paint().apply {
            this.color = textColor.toArgb()
            alpha = 180
            textSize = 24f
            isAntiAlias = true
        }
        dates.filterIndexed { index, _ -> index % labelStep == 0 || index == count - 1 }.take(7).forEachIndexed { idx, date ->
            val i = (idx * labelStep).coerceAtMost(count - 1)
            val x = padLeft + i * w / (count - 1)
            drawContext.canvas.nativeCanvas.drawText(date, x, size.height - 4f, paint)
        }
    }
}

// ===================== EXERCISES SCREEN (PATCH-6, PATCH-7) =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ExercisesScreen(vm: WorkoutViewModel) {
    val s by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var showAdd by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newGroup by remember { mutableStateOf("") }
    var newVariantName by remember { mutableStateOf("") }

    // Edit exercise dialog
    var editExercise by remember { mutableStateOf<ExerciseEntity?>(null) }
    var editName by remember { mutableStateOf("") }
    var editGroup by remember { mutableStateOf("") }
    var editStrategy by remember { mutableStateOf("") }

    // Delete exercise dialog
    var deleteExercise by remember { mutableStateOf<ExerciseEntity?>(null) }
    var deleteExerciseSetsCount by remember { mutableStateOf(0) }

    // Variant dialog
    var variantExercise by remember { mutableStateOf<ExerciseEntity?>(null) }
    var variantName by remember { mutableStateOf("") }
    var variantDesc by remember { mutableStateOf("") }
    var showVariantAdd by remember { mutableStateOf(false) }
    var editVariant by remember { mutableStateOf<ExerciseVariantEntity?>(null) }
    var editVariantName by remember { mutableStateOf("") }

    // Variants list dialog
    var showVariantsFor by remember { mutableStateOf<ExerciseEntity?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Упражнения", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Поиск") },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Icon(Icons.Default.Search, null) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Добавить упражнение")
        }
        Spacer(Modifier.height(8.dp))
        val filtered = s.exercises.filter { it.name.contains(query, true) || it.muscleGroup.contains(query, true) }
        LazyColumn {
            items(filtered) { ex ->
                Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(ex.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${ex.muscleGroup} · Отдых: ${ex.restSeconds}с", style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            IconButton(onClick = {
                                variantExercise = ex
                                variantName = ""
                                variantDesc = ""
                                showVariantAdd = true
                            }) { Icon(Icons.Default.AddCircle, "Добавить вариант") }
                            IconButton(onClick = { showVariantsFor = ex }) { Icon(Icons.Default.List, "Варианты") }
                            IconButton(onClick = {
                                editExercise = ex
                                editName = ex.name
                                editGroup = ex.muscleGroup
                                editStrategy = ex.weightStrategy
                            }) { Icon(Icons.Default.Edit, "Редактировать") }
                            IconButton(onClick = {
                                deleteExercise = ex
                            }) { Icon(Icons.Default.Delete, "Удалить") }
                        }
                    }
                }
            }
        }
    }

    // Delete exercise confirmation dialog
    deleteExercise?.let { ex ->
        val sets by vm.exactSets(ex.id, null).collectAsState(emptyList())
        val historyCount = sets.size
        AlertDialog(
            onDismissRequest = { deleteExercise = null },
            title = { Text("Удалить упражнение '${ex.name}'?") },
            text = {
                Column {
                    Text("Это действие нельзя отменить.")
                    if (historyCount > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("Внимание: у упражнения есть история (${historyCount} подходов). Удаление сотрёт все записи.", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.deleteExercise(ex.id); deleteExercise = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteExercise = null }) { Text("Отмена") } }
        )
    }

    // Add exercise dialog (PATCH-7: user names variant, no auto "Базовый")
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Новое упражнение") },
            text = {
                Column {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newGroup, onValueChange = { newGroup = it }, label = { Text("Группа мышц") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = newVariantName, onValueChange = { newVariantName = it }, label = { Text("Название варианта (например: Базовый, Скамья 30°)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                }
            },
            confirmButton = { Button(onClick = {
                if (newName.isNotBlank()) {
                    val vName = newVariantName.ifBlank { "Базовый" }
                    vm.addExercise(newName, newGroup, "", vName)
                    newName = ""; newGroup = ""; newVariantName = ""; showAdd = false
                }
            }) { Text("Добавить") } },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Отмена") } }
        )
    }

    // Edit exercise dialog
    editExercise?.let { ex ->
        AlertDialog(
            onDismissRequest = { editExercise = null },
            title = { Text("Редактировать упражнение") },
            text = {
                Column {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = editGroup, onValueChange = { editGroup = it }, label = { Text("Группа мышц") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = editStrategy, onValueChange = { editStrategy = it }, label = { Text("Weight strategy") }, placeholder = { Text("Например: гантели, штанга, тренажёр...") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                }
            },
            confirmButton = { Button(onClick = { vm.updateExercise(ex.id, editName, editGroup, editStrategy); editExercise = null }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { editExercise = null }) { Text("Отмена") } }
        )
    }

    // Add variant dialog
    if (showVariantAdd && variantExercise != null) {
        AlertDialog(
            onDismissRequest = { showVariantAdd = false; variantExercise = null },
            title = { Text("Новый вариант для ${variantExercise!!.name}") },
            text = {
                Column {
                    OutlinedTextField(value = variantName, onValueChange = { variantName = it }, label = { Text("Название варианта") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = variantDesc, onValueChange = { variantDesc = it }, label = { Text("Описание (опционально)") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ))
                }
            },
            confirmButton = { Button(onClick = {
                if (variantName.isNotBlank()) {
                    vm.addVariant(variantExercise!!.id, variantName, variantDesc)
                    variantName = ""; variantDesc = ""; showVariantAdd = false; variantExercise = null
                }
            }) { Text("Добавить") } },
            dismissButton = { TextButton(onClick = { showVariantAdd = false; variantExercise = null }) { Text("Отмена") } }
        )
    }

    // Variant management dialog
    showVariantsFor?.let { ex ->
        val exVariants by vm.variants(ex.id).collectAsState(emptyList())
        AlertDialog(
            onDismissRequest = { showVariantsFor = null },
            title = { Text("Варианты ${ex.name}") },
            text = {
                if (exVariants.isEmpty()) {
                    Text("Нет вариантов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(exVariants) { v ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(v.name, modifier = Modifier.weight(1f))
                                Row {
                                    IconButton(onClick = { editVariant = v; editVariantName = v.name }) { Icon(Icons.Default.Edit, "Переименовать") }
                                    IconButton(onClick = { vm.deleteVariant(v.id) }) { Icon(Icons.Default.Delete, "Удалить") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showVariantsFor = null }) { Text("Закрыть") } }
        )
    }

    // Edit variant name dialog
    editVariant?.let { v ->
        AlertDialog(
            onDismissRequest = { editVariant = null },
            title = { Text("Переименовать вариант") },
            text = {
                OutlinedTextField(value = editVariantName, onValueChange = { editVariantName = it }, label = { Text("Новое название") }, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ))
            },
            confirmButton = { Button(onClick = { vm.updateVariantName(v.id, editVariantName); editVariant = null }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { editVariant = null }) { Text("Отмена") } }
        )
    }
}

// ===================== SETTINGS SCREEN (PATCH-8) =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SettingsScreen(vm: WorkoutViewModel) {
    val s by vm.state.collectAsState()
    var importText by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // Units
        Text("Единицы измерения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = s.weightUnit == "kg", onClick = { vm.setWeightUnit("kg") })
            Text("Килограммы (кг)")
            Spacer(Modifier.width(16.dp))
            RadioButton(selected = s.weightUnit == "lbs", onClick = { vm.setWeightUnit("lbs") })
            Text("Фунты (lbs)")
        }
        Spacer(Modifier.height(16.dp))

        // Sound
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Звук", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Switch(checked = s.timerSound, onCheckedChange = { vm.setTimerSound(it) })
        }
        Spacer(Modifier.height(8.dp))

        // Vibration
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Вибрация", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Switch(checked = s.vibration, onCheckedChange = { vm.setVibration(it) })
        }
        Spacer(Modifier.height(16.dp))

        // Theme
        Text("Тема", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = s.theme == "system", onClick = { vm.setTheme("system") })
                Text("Системная")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = s.theme == "light", onClick = { vm.setTheme("light") })
                Text("Светлая")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = s.theme == "dark", onClick = { vm.setTheme("dark") })
                Text("Тёмная")
            }
        }
        Spacer(Modifier.height(16.dp))
        Divider()
        Spacer(Modifier.height(16.dp))
        Text("Экспорт / Импорт данных", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Button(onClick = vm::exportData, modifier = Modifier.fillMaxWidth()) { Text("Экспорт JSON") }
        if (s.exportText.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = s.exportText, onValueChange = {}, label = { Text("Экспорт") }, modifier = Modifier.fillMaxWidth().height(120.dp), readOnly = true, colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
            ))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = importText, onValueChange = { importText = it }, label = { Text("Вставьте JSON для импорта") }, modifier = Modifier.fillMaxWidth().height(120.dp), colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ))
        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.importData(importText) }, modifier = Modifier.fillMaxWidth()) { Text("Импорт") }
        if (s.message.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(s.message, color = MaterialTheme.colorScheme.primary) }
    }
}
