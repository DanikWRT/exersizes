package com.openclaw.workout.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.openclaw.workout.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PlanExerciseItem(
    val exerciseId: String, val exerciseName: String = "", val variantId: String? = null, val variantName: String = "",
    var weight: Double = 0.0, var reps: Int = 10, var sets: Int = 3, var restSeconds: Int = 60
)

data class UiState(
    val date: String = LocalDate.now().toString(),
    val exercises: List<ExerciseEntity> = emptyList(),
    val query: String = "",
    val selectedExercise: ExerciseEntity? = null,
    val variants: List<ExerciseVariantEntity> = emptyList(),
    val planExercises: List<PlanExerciseItem> = emptyList(),
    val plannedSessions: List<WorkoutSessionEntity> = emptyList(),
    val completedSessions: List<WorkoutSessionEntity> = emptyList(),
    val currentSession: WorkoutSessionEntity? = null,
    val workoutExercises: List<WorkoutExerciseEntity> = emptyList(),
    val templates: List<WorkoutTemplateEntity> = emptyList(),
    val exportText: String = "",
    val message: String = "",
    // Settings (PATCH-8)
    val weightUnit: String = "kg",
    val timerSound: Boolean = true,
    val vibration: Boolean = true,
    val theme: String = "system", // system, light, dark
    // View session
    val viewSession: WorkoutSessionEntity? = null,
    val viewSessionExercises: List<ViewSessionExercise> = emptyList()
)

data class ViewSessionExercise(
    val workoutExercise: WorkoutExerciseEntity,
    val exerciseName: String,
    val variantName: String,
    val sets: List<WorkoutSetEntity>,
    val segments: Map<String, List<WorkoutSetSegmentEntity>> = emptyMap()
)

class WorkoutViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = WorkoutRepository(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    val sessions = repo.dao.sessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val plannedSessions = repo.dao.plannedSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val completedSessions = repo.dao.completedSessions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val templates = repo.dao.templates().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repo.seedIfEmpty()
            loadSettings()
            repo.dao.exercises().collect { list -> _state.update { it.copy(exercises = list) } }
        }
        viewModelScope.launch {
            plannedSessions.collect { _state.update { it.copy(plannedSessions = it.plannedSessions) } }
        }
        viewModelScope.launch {
            repo.dao.plannedSessions().collect { list -> _state.update { it.copy(plannedSessions = list) } }
        }
        viewModelScope.launch {
            repo.dao.completedSessions().collect { list -> _state.update { it.copy(completedSessions = list) } }
        }
        viewModelScope.launch {
            templates.collect { list -> _state.update { it.copy(templates = list) } }
        }
    }

    private suspend fun loadSettings() {
        _state.update {
            it.copy(
                weightUnit = repo.getSetting("weightUnit") ?: "kg",
                timerSound = (repo.getSetting("timerSound") ?: "true") == "true",
                vibration = (repo.getSetting("vibration") ?: "true") == "true",
                theme = repo.getSetting("theme") ?: "system"
            )
        }
    }

    fun setDate(v: String) { _state.update { it.copy(date = v) } }
    fun setQuery(v: String) { _state.update { it.copy(query = v) } }
    fun setPlanExercises(items: List<PlanExerciseItem>) { _state.update { it.copy(planExercises = items) } }
    fun clearPlan() { _state.update { it.copy(planExercises = emptyList(), date = LocalDate.now().toString()) } }

    fun selectExercise(e: ExerciseEntity) {
        _state.update { it.copy(selectedExercise = e, query = "") }
        viewModelScope.launch {
            repo.dao.variants(e.id).collect { vs -> _state.update { it.copy(variants = vs) } }
        }
    }

    fun addExerciseToPlan(exerciseId: String, variantId: String?) {
        viewModelScope.launch {
            val s = _state.value
            val ex = s.exercises.find { it.id == exerciseId } ?: return@launch
            val variant = s.variants.find { it.id == variantId }
            val restSec = variant?.restSeconds ?: ex.restSeconds

            // PATCH-2: auto-fill from last set
            val lastSet = repo.getLastSetForVariant(exerciseId, variantId)
            val lastWe = repo.getLastWorkoutExerciseForVariant(exerciseId, variantId)
            val lastSetsCount = lastWe?.let { repo.dao.setsOnce(it.id).size } ?: 3

            val item = PlanExerciseItem(
                exerciseId = exerciseId, exerciseName = ex.name,
                variantId = variantId, variantName = variant?.name ?: "Базовый",
                weight = lastSet?.weight ?: 0.0,
                reps = lastSet?.reps ?: 10,
                sets = lastSetsCount.coerceAtLeast(1),
                restSeconds = lastWe?.restSeconds ?: restSec
            )
            _state.update { it.copy(planExercises = it.planExercises + item) }
        }
    }

    fun updatePlanExercise(index: Int, weight: Double? = null, reps: Int? = null, sets: Int? = null, restSeconds: Int? = null) {
        val list = _state.value.planExercises.toMutableList()
        if (index !in list.indices) return
        val item = list[index]
        list[index] = item.copy(
            weight = weight ?: item.weight,
            reps = reps ?: item.reps,
            sets = sets ?: item.sets,
            restSeconds = restSeconds ?: item.restSeconds
        )
        _state.update { it.copy(planExercises = list) }
    }

    fun removePlanExercise(index: Int) {
        val list = _state.value.planExercises.toMutableList()
        if (index !in list.indices) return
        list.removeAt(index)
        _state.update { it.copy(planExercises = list) }
    }

    fun savePlan() = viewModelScope.launch {
        val s = _state.value
        if (s.planExercises.isEmpty()) {
            _state.update { it.copy(message = "Добавьте хотя бы одно упражнение") }
            return@launch
        }
        val items = s.planExercises.map { Triple(it.exerciseId, it.variantId, TemplateExerciseItem(it.exerciseId, it.variantId, it.weight, it.reps, it.sets, it.restSeconds)) }
        repo.createPlan(s.date, items)
        _state.update { it.copy(planExercises = emptyList(), message = "План сохранён на ${s.date}") }
    }

    fun saveAsTemplate(name: String) = viewModelScope.launch {
        val s = _state.value
        if (s.planExercises.isEmpty()) return@launch
        val items = s.planExercises.map { TemplateExerciseItem(it.exerciseId, it.variantId, it.weight, it.reps, it.sets, it.restSeconds) }
        repo.saveTemplate(name, items)
        _state.update { it.copy(message = "Шаблон \"$name\" сохранён") }
    }

    // PATCH-3: load template into plan editor
    fun loadTemplateIntoPlan(templateId: String) = viewModelScope.launch {
        val items = repo.loadTemplateAsPlan(templateId) ?: return@launch
        val planItems = items.map { tpl ->
            val exName = _state.value.exercises.find { it.id == tpl.exerciseId }?.name ?: ""
            val vName = _state.value.exercises.find { it.id == tpl.exerciseId }?.let { ex ->
                repo.dao.variants(ex.id).first().find { it.id == tpl.variantId }?.name
            } ?: (tpl.variantId?.let { repo.dao.variantById(it)?.name } ?: "Базовый")
            PlanExerciseItem(
                exerciseId = tpl.exerciseId, exerciseName = exName,
                variantId = tpl.variantId, variantName = vName,
                weight = tpl.weight, reps = tpl.reps, sets = tpl.sets, restSeconds = tpl.restSeconds
            )
        }
        _state.update { it.copy(planExercises = planItems) }
    }

    fun deleteTemplate(t: WorkoutTemplateEntity) = viewModelScope.launch {
        repo.dao.deleteTemplate(t)
    }

    fun deleteSession(s: WorkoutSessionEntity) = viewModelScope.launch {
        repo.dao.deleteSessionWithData(s.id)
    }

    // PATCH-5: reopen completed session
    fun reopenSession(sid: String) = viewModelScope.launch {
        repo.dao.reopenSession(sid)
    }

    fun startWorkout(session: WorkoutSessionEntity) = viewModelScope.launch {
        _state.update { it.copy(currentSession = session) }
        collectWorkoutExercises(session.id)
    }

    private fun collectWorkoutExercises(sessionId: String) = viewModelScope.launch {
        repo.dao.workoutExercises(sessionId).collect { w -> _state.update { it.copy(workoutExercises = w) } }
    }

    // PATCH-4: load session for view with segments
    fun loadSessionForView(sessionId: String) = viewModelScope.launch {
        val session = repo.dao.session(sessionId) ?: return@launch
        val wes = repo.dao.workoutExercisesOnce(sessionId)
        val viewExercises = wes.map { we ->
            val exName = _state.value.exercises.find { it.id == we.exerciseId }?.name ?: we.exerciseId.take(8)
            val vName = we.variantId?.let { vid ->
                _state.value.variants.find { it.id == vid }?.name
                    ?: repo.dao.variantById(vid)?.name ?: "—"
            } ?: "Базовый"
            val sets = repo.dao.setsOnce(we.id)
            val segments = sets.associate { it.id to repo.dao.segmentsOnce(it.id) }
            ViewSessionExercise(we, exName, vName, sets, segments)
        }
        _state.update { it.copy(viewSession = session, viewSessionExercises = viewExercises) }
    }

    fun clearViewSession() { _state.update { it.copy(viewSession = null, viewSessionExercises = emptyList()) } }

    fun completeSet(setId: String) = viewModelScope.launch {
        repo.dao.completeSet(setId)
    }

    // PATCH-12: update set weight/reps and main segment before completing
    fun updateSetWeightReps(setId: String, weight: Double, reps: Int) = viewModelScope.launch {
        repo.dao.updateSetWeightReps(setId, weight, reps)
    }

    fun addSupplementalSegment(setId: String, weight: Double, reps: Int) = viewModelScope.launch {
        repo.dao.upsertSegment(WorkoutSetSegmentEntity(workoutSetId = setId, segmentIndex = 1, weight = weight, reps = reps, isSupplemental = true))
    }

    fun updateRestSeconds(workoutExerciseId: String, seconds: Int) = viewModelScope.launch {
        val we = repo.dao.workoutExerciseById(workoutExerciseId) ?: return@launch
        repo.dao.updateWorkoutExerciseRest(workoutExerciseId, seconds)
        if (we.variantId != null) {
            repo.dao.updateVariantRest(we.variantId, seconds)
        } else {
            repo.dao.updateExerciseRest(we.exerciseId, seconds)
        }
    }

    fun finishWorkout() = viewModelScope.launch {
        _state.value.currentSession?.let {
            repo.dao.finishSession(it.id)
            _state.update { s -> s.copy(currentSession = null, message = "Тренировка завершена") }
        }
    }

    // FIX: Do not create any variant automatically in addExercise.
    // seedIfEmpty already provides a default "Базовый" for seed data.
    // User can add variants manually via addVariant after creating the exercise.
    fun addExercise(name: String, group: String, strategy: String, variantName: String = "") = viewModelScope.launch {
        val e = ExerciseEntity(name = name, muscleGroup = group, weightStrategy = strategy)
        repo.dao.upsertExercise(e)
    }

    fun updateExercise(exerciseId: String, name: String, group: String, strategy: String) = viewModelScope.launch {
        repo.dao.updateExercise(exerciseId, name, group, strategy)
    }

    fun deleteExercise(exerciseId: String) = viewModelScope.launch {
        repo.dao.deleteExerciseWithData(exerciseId)
    }

    fun addVariant(exerciseId: String, name: String, desc: String = "") = viewModelScope.launch {
        repo.dao.upsertVariant(ExerciseVariantEntity(id = "${exerciseId}_${name}", exerciseId = exerciseId, name = name, description = desc))
    }

    fun updateVariantName(variantId: String, name: String) = viewModelScope.launch {
        repo.dao.updateVariantName(variantId, name)
    }

    fun deleteVariant(variantId: String) = viewModelScope.launch {
        if (repo.canDeleteVariant(variantId)) {
            repo.dao.deleteVariantById(variantId)
        }
    }

    // PATCH-8: Settings
    fun setWeightUnit(unit: String) = viewModelScope.launch {
        repo.setSetting("weightUnit", unit)
        _state.update { it.copy(weightUnit = unit) }
    }

    fun setTimerSound(enabled: Boolean) = viewModelScope.launch {
        repo.setSetting("timerSound", enabled.toString())
        _state.update { it.copy(timerSound = enabled) }
    }

    fun setVibration(enabled: Boolean) = viewModelScope.launch {
        repo.setSetting("vibration", enabled.toString())
        _state.update { it.copy(vibration = enabled) }
    }

    fun setUnits(unit: String) = setWeightUnit(unit)
    fun setSoundEnabled(enabled: Boolean) = setTimerSound(enabled)
    fun setVibrationEnabled(enabled: Boolean) = setVibration(enabled)
    fun setTheme(theme: String) = viewModelScope.launch {
        repo.setSetting("theme", theme)
        _state.update { it.copy(theme = theme) }
    }

    fun exportData() = viewModelScope.launch {
        _state.update { it.copy(exportText = repo.exportJson(), message = "JSON export готов") }
    }

    fun importData(text: String) = viewModelScope.launch {
        runCatching { repo.importJson(text) }.onSuccess { _state.update { it.copy(message = "Импорт завершён") } }.onFailure { e -> _state.update { it.copy(message = "Ошибка импорта: ${e.message}") } }
    }

    fun exportToFile(path: String) = viewModelScope.launch {
        runCatching {
            val json = repo.exportJson()
            java.io.File(path).apply {
                parentFile?.mkdirs()
                writeText(json, Charsets.UTF_8)
            }
            _state.update { it.copy(message = "Данные экспортированы") }
        }.onFailure { e ->
            _state.update { it.copy(message = "Ошибка экспорта: ${e.message}") }
        }
    }

    fun importFromFile(path: String) = viewModelScope.launch {
        runCatching {
            val text = java.io.File(path).readText(Charsets.UTF_8)
            repo.importJson(text)
            _state.update { it.copy(message = "Данные восстановлены") }
        }.onFailure { e ->
            _state.update { it.copy(message = "Ошибка импорта: ${e.message}") }
        }
    }

    fun sets(workoutExerciseId: String) = repo.dao.sets(workoutExerciseId)
    fun segments(setId: String) = repo.dao.segments(setId)
    fun exactSets(exerciseId: String, variantId: String?) = repo.dao.exactVariantSets(exerciseId, variantId)
    fun variants(exerciseId: String) = repo.dao.variants(exerciseId)

    suspend fun getLastSetForVariant(exerciseId: String, variantId: String?): WorkoutSetWithSessionDate? {
        return repo.dao.lastSetForExerciseVariant(exerciseId, variantId)
    }    fun getExerciseName(id: String): String = _state.value.exercises.find { it.id == id }?.name ?: id.take(8)
    fun getVariantName(variantId: String?): String {
        if (variantId == null) return "Базовый"
        return _state.value.variants.find { it.id == variantId }?.name ?: "—"
    }
}
