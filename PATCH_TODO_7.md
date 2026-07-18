# Workout App — PATCH-7 TODO: Миграция БД + тёмная тема контрастность

> **Проект:** `/home/openclaw/.openclaw/workspace/30-Projects/SELF/Workout_program/android/`
> **Git:** `git@github.com:DanikWRT/exersizes.git`

---

## FIX-1: Удалить дубли "Базовый" из БД + UNIQUE constraint

**Проблема:** В БД есть дублирующиеся "Базовый" variant для одного exercise. GROUP BY в SQL — костыль.

**Что сделать:**

1. **Миграция Room (версия 4 → 5):**
   - В `AppDatabase.kt` изменить `version = 5`
   - В `Dao.kt` или `AppDatabase.kt` добавить `Migration(4, 5)`:
     ```kotlin
     val MIGRATION_4_5 = object : Migration(4, 5) {
         override fun migrate(db: SupportSQLiteDatabase) {
             // 1. Найти дубли: одинаковые exerciseId + name
             // 2. Удалить лишние, оставить только первый (с min id)
             // 3. Обновить workout_exercises.variantId на оставшийся id
             // 4. Добавить UNIQUE index на (exerciseId, name)
             db.execSQL("""
                 DELETE FROM exercise_variants 
                 WHERE id NOT IN (
                     SELECT MIN(id) FROM exercise_variants 
                     GROUP BY exerciseId, name
                 )
             """)
             db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_variant_unique ON exercise_variants(exerciseId, name)")
         }
     }
     ```
   - Подключить миграцию в `AppDatabase.get()`:
     ```kotlin
     .addMigrations(MIGRATION_4_5)
     ```

2. **Предотвратить дубли в будущем:**
   - В `Repository.seedIfEmpty()` — перед `upsertVariant` проверить, есть ли уже variant с таким именем для этого exercise
   - В `WorkoutViewModel.addExercise()` — то же самое

---

## FIX-2: Тёмная тема — контрастность текста

**Проблема:** На тёмном фоне текст не виден (цвета сливаются).

**Что сделать:**
- MaterialTheme уже использует `darkColorScheme()` — это хорошо
- Но в некоторых местах кода используются жёстко заданные цвета `Color.Black`, `Color.Gray`, или `MaterialTheme.colorScheme.onSurface` может быть тёмным на тёмном фоне
- Нужно проверить все `Text()` в `MainActivity.kt` — где используются цвета вручную:
  - `Color(0xFF...)` — заменить на `MaterialTheme.colorScheme.onSurface` или `onBackground`
  - Убрать все `color = Color.Black` — на тёмной теме это невидимо
  - Проверить карточки (`Card`, `Surface`) — фон карточки должен отличаться от фона экрана
  - Проверить `OutlinedTextField` — label и text должны быть `onSurface`

**Где править:** `MainActivity.kt` — везде где `Text(..., color = ...)` или цвета вручную

---

## Критерии готовности

- [ ] Дубли "Базовый" удалены из БД через миграцию
- [ ] UNIQUE constraint на (exerciseId, name) — дублей не будет
- [ ] Текст читаем на тёмной теме (контрастный)
- [ ] APK пересобран
- [ ] Git commit + push

---

## Git push
```bash
cd /home/openclaw/.openclaw/workspace/30-Projects/SELF/Workout_program
git add -A
git commit -m "PATCH-7: DB migration remove duplicates + dark theme contrast"
git push origin master
```
