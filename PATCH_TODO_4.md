# Workout App — PATCH-4 TODO

> **Проект:** `/home/openclaw/.openclaw/workspace/30-Projects/SELF/Workout_program/android/`
> **Git:** `git@github.com:DanikWRT/exersizes.git` (только Workout_program)
> **APK:** `android/app/build/outputs/apk/debug/app-debug.apk`

---

## FIX-1: Отчёты работают

**Проблема:** При нажатии на "Отчёты" в боттомбаре — в поиск вставляется что-то и "ничего не найдено".

**Что сделать:**
- В `ReportsScreen` (вкладка "Отчёты") — должен быть список ВСЕХ упражнений сразу, без какого-либо предзаполненного поиска
- Поле поиска должно быть ПУСТЫМ по умолчанию
- Список показывает все упражнения (имя + группа мышц)
- Тап на упражнение → переход на `ReportExerciseScreen(exerciseId)`
- В `ReportExerciseScreen`:
  - Показывать варианты упражнения (FilterChip)
  - Таблица: дата | вес | повторы (последние 20 записей)
  - График веса и повторов по времени
  - Если нет данных — показать "Нет данных"

**Где править:** `MainActivity.kt` — `ReportsScreen` и `ReportExerciseScreen`

---

## FIX-2: Убрать дубли "Базовый" в выборе variant

**Проблема:** В `NewWorkoutScreen` при выборе exercise — варианты "Базовый" задваиваются.

**Что сделать:**
- В `NewWorkoutScreen`, блок `LazyRow` с variants: использовать `s.variants.distinctBy { it.name }`
- Проверить `Dao.kt` — запрос `variants(exerciseId)` должен возвращать уникальные записи
- Проверить `Repository.seedIfEmpty()` — возможно там создаётся "Базовый" дважды при seed
- Также проверить `WorkoutViewModel.addExercise()` — при создании нового exercise через UI НЕ создавать автоматический "Базовый" variant, если пользователь не указал имя

**Где править:** `MainActivity.kt`, `Dao.kt`, `Repository.kt`

---

## Критерии готовности

- [ ] Отчёты: список всех упражнений → тап → варианты → таблица + графики
- [ ] Нет дублей "Базовый" в списке variant
- [ ] APK пересобран
- [ ] Git commit + push в `git@github.com:DanikWRT/exersizes.git`

---

## Порядок работы

1. Прочитать `MainActivity.kt`
2. Исправить ReportsScreen и ReportExerciseScreen
3. Исправить дубли variant
4. Собрать APK: `./gradlew assembleDebug --no-daemon`
5. Git commit + push