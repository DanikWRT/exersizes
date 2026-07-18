# Workout_program — Руководство пользователя

Назначение: быстрый старт, типичный сценарий на неделю и примеры CLI-команд.

## Quickstart
Из папки проекта:
```bash
cd /home/openclaw/.openclaw/workspace/30-Projects/SELF/Workout_program/
python3 app/workout.py list-exercises
```

## Типичный сценарий на неделю (план → тренировки → отчёт)

### 1) Создать план на неделю
Сплит задаётся строкой и повторяется по дням, пока не заполнится 7 дней.
Пример сплита: `legs+shoulders,back+biceps,chest+triceps`

```bash
python3 app/workout.py plan-week \
  --start 2026-04-13 \
  --split legs+shoulders,back+biceps,chest+triceps
```

### 2) Посмотреть план недели
```bash
python3 app/workout.py show-week --start 2026-04-13
```

### 3) Логировать подходы во время тренировок
Каждый подход логируется отдельной командой `log-set`.

```bash
python3 app/workout.py log-set --date 2026-04-17 --exercise "Жим лёжа" --weight 60 --reps 8
python3 app/workout.py log-set --date 2026-04-17 --exercise "Жим лёжа" --weight 60 --reps 8 --notes "последний подход тяжело"
```

Подсказки:
- Если упражнение не находится по имени — добавь алиас через `add-exercise --alias ...`.
- Даты всегда в формате `YYYY-MM-DD`.

### 4) Посмотреть недельный отчёт
Отчёт строится по неделе, начиная с даты `--start`.

```bash
python3 app/workout.py report-week --start 2026-04-13
```

## Упражнения

### Посмотреть банк упражнений
```bash
python3 app/workout.py list-exercises
```

### Добавить упражнение
```bash
python3 app/workout.py add-exercise --name "Подтягивания" --muscle_group back --alias "Pull-ups"
```

## Где лежат данные
- `app/data/exercises.json` — упражнения
- `app/data/logs.json` — логи подходов
- `app/data/plans.json` — планы недели
