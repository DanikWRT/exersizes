# Workout App — PATCH-11: Переделка FEATURE-3 + FEATURE-4

> **Проект:** `/home/openclaw/.openclaw/workspace/30-Projects/SELF/Workout_program/android/`
> **Git:** `git@github.com:DanikWRT/exersizes.git`

---

## FIX-1: Корректировка веса/повторов — НЕ AlertDialog, а поля на экране

**Проблема:** Сейчас после "Завершить подход" вылезает AlertDialog. Нужно лишний раз нажать "Сохранить и завершить".

**Что сделать:**
- В `ExerciseExecutionScreen` — под кнопкой "Завершить подход" добавить два поля:
  - "Вес: [−] 10.0 [+]" (step 0.5)
  - "Повторы: [−] 12 [+]" (step 1)
- Эти поля ВИДНЫ всегда после нажатия "Старт"
- Пользователь может подкорректировать значения прямо на экране
- Потом нажать "Завершить подход" — сохраняются текущие значения из полей
- НЕ нужен AlertDialog

**Где править:** `MainActivity.kt` — `ExerciseExecutionScreen`

---

## FIX-2: Дополнительные повторения — сегменты внутри подхода, не отдельные подходы

**Проблема:** Сейчас "дополнительный подход" — это отдельный подход. А нужно "добивочка" к текущему подходу.

**Пример:**
- Основной: 5кг × 10 раз
- Не дотянул → нажал "+ Доп повторения" → 3кг × 5 раз
- Итого в подходе: 15 повторений (10 на 5кг + 5 на 3кг)
- Можно добавить ещё: 1кг × 5 раз
- Итого: 20 повторений (10×5кг + 5×3кг + 5×1кг)

**Что сделать:**

1. **Изменить `WorkoutSetEntity`:**
   - Убрать поля `weight` и `reps` из `WorkoutSetEntity`
   - Добавить поле `segments: List<SetSegmentEntity>`
   - `SetSegmentEntity` = { weight: Double, reps: Int, isSupplemental: Boolean }

2. **В `ExerciseExecutionScreen`:**
   - После завершения основного подхода — кнопка "+ Доп повторения"
   - При нажатии: два поля (вес меньше основного, повторы) + кнопка "Добавить"
   - Можно добавить несколько сегментов
   - В списке подходов отображать: "5кг × 10 + 3кг × 5 + 1кг × 5"

3. **В отчётах:**
   - Для основного сегмента (isSupplemental=false) — считать статистику
   - Дополнительные (isSupplemental=true) — не считать в отчётах, но видны в тренировке

**Где править:**
- `Entities.kt` — изменить `WorkoutSetEntity`, добавить `SetSegmentEntity`
- `Dao.kt` — обновить запросы (JOIN с сегментами)
- `MainActivity.kt` — `ExerciseExecutionScreen`
- `WorkoutViewModel.kt` — логика завершения подхода

---

## Критерии готовности

- [ ] Корректировка веса/повторов — поля на экране, не AlertDialog
- [ ] Дополнительные повторения — сегменты внутри подхода
- [ ] В отчётах только основные сегменты
- [ ] APK пересобран
- [ ] Git push

---

## Git push
```bash
cd /home/openclaw/.openclaw/workspace/30-Projects/SELF/Workout_program
git add -A
git commit -m "PATCH-11: Redesign finish dialog + supplemental reps as segments"
git push origin master
```
