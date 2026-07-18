# Workout App — PATCH-8 TODO: Каскадное удаление sets

> **Проект:** `/home/openclaw/.openclaw/workspace/30-Projects/SELF/Workout_program/android/`
> **Git:** `git@github.com:DanikWRT/exersizes.git`

---

## FIX: Удаление тренировки должно удалять подходы (sets)

**Проблема:** Пользователь удалил старые тренировки, а в отчётах остались подходы (sets) от этих тренировок. Удаление тренировки не удаляет связанные записи.

**Что сделать:**

1. **В `Dao.kt` — добавить каскадное удаление:**
   - `deleteSession(sessionId)` должен сначала удалить все `workout_exercises` для этой сессии
   - Потом удалить все `workout_sets` для этой сессии
   - Потом удалить саму `session`
   - В Room можно использовать `@Transaction`:
     ```kotlin
     @Transaction
     suspend fun deleteSessionWithData(sessionId: String) {
         deleteSetsBySessionId(sessionId)
         deleteWorkoutExercisesBySessionId(sessionId)
         deleteSession(sessionId)
     }
     ```
   - Добавить:
     ```kotlin
     @Query("DELETE FROM workout_sets WHERE sessionId = :sessionId")
     suspend fun deleteSetsBySessionId(sessionId: String)
     
     @Query("DELETE FROM workout_exercises WHERE sessionId = :sessionId")
     suspend fun deleteWorkoutExercisesBySessionId(sessionId: String)
     ```

2. **В `WorkoutViewModel` — обновить `deleteSession()`:**
   - Заменить `repo.dao.deleteSession(sessionId)` на `repo.dao.deleteSessionWithData(sessionId)`

3. **В `MainActivity.kt` — `PlansScreen`:**
   - Убедиться, что тап на корзину 🗑️ вызывает `vm.deleteSession(session)` — это уже есть
   - Но теперь `deleteSession` будет удалять всё каскадно

---

## Критерии готовности

- [ ] Удаление тренировки удаляет все workout_exercises и workout_sets
- [ ] Отчёты не показывают данные от удалённых тренировок
- [ ] APK пересобран
- [ ] Git commit + push

---

## Git push
```bash
cd /home/openclaw/.openclaw/workspace/30-Projects/SELF/Workout_program
git add -A
git commit -m "PATCH-8: Cascade delete workout data"
git push origin master
```
