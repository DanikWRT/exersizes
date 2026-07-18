# Workout_program — CLI API

Назначение: описать интерфейс командной строки.

## Запуск
```bash
python3 app/workout.py <command> [args]
```

Данные сохраняются рядом со скриптом в `app/data/*.json`.

## Форматы данных (кратко)
- Даты: `YYYY-MM-DD`
- Вес: число (кг), например `60` или `12.5`
- Повторы: целое число, например `8`
- Сплит недели: строка через запятую, например `legs+shoulders,back+biceps,chest+triceps`

## Команды

### add-exercise — добавить упражнение
**Аргументы**
- `--name` (строка, обязательно)
- `--muscle_group` (строка, обязательно)
- `--alias` (строка, опционально, можно повторять)

**Примеры**
```bash
python3 app/workout.py add-exercise --name "Жим лёжа" --muscle_group chest
python3 app/workout.py add-exercise --name "Тяга верхнего блока" --muscle_group back --alias "Спина тяга на спину верхний блок"
```

### list-exercises — список упражнений
**Пример**
```bash
python3 app/workout.py list-exercises
```

### log-set — залогировать подход
Добавляет одну запись о подходе (вес/повторы/заметки) на конкретную дату.

**Аргументы**
- `--date` `YYYY-MM-DD` (обязательно)
- `--exercise` (строка; ищется по `name`/`aliases`; обязательно)
- `--weight` (число; кг; обязательно)
- `--reps` (целое; обязательно)
- `--notes` (строка; опционально)

**Примеры**
```bash
python3 app/workout.py log-set --date 2026-04-17 --exercise "Жим лёжа" --weight 60 --reps 8
python3 app/workout.py log-set --date 2026-04-17 --exercise "Жим лёжа" --weight 60 --reps 8 --notes "последний подход тяжело"
```

### plan-week — создать план на 7 дней
**Аргументы**
- `--start` `YYYY-MM-DD` (обязательно)
- `--split` строка формата: `legs+shoulders,back+biceps,chest+triceps`

Правило: элементы сплита циклически повторяются по дням, пока не заполнится 7 дней.

**Пример**
```bash
python3 app/workout.py plan-week --start 2026-04-13 --split legs+shoulders,back+biceps,chest+triceps
```

### show-week — показать план недели
**Аргументы**
- `--start` `YYYY-MM-DD` (обязательно)

**Пример**
```bash
python3 app/workout.py show-week --start 2026-04-13
```

### report-week — отчёт за неделю
Строит отчёт по логам за 7 дней, начиная с даты `--start`.

**Аргументы**
- `--start` `YYYY-MM-DD` (обязательно)

**Пример**
```bash
python3 app/workout.py report-week --start 2026-04-13
```

## Типичные ошибки и что проверить
- Неверная дата: используйте строго формат `YYYY-MM-DD`.
- Упражнение не найдено: добавьте его через `add-exercise` или добавьте алиас через `--alias`.
- Нечисловой вес или повторы: `--weight` должен быть числом, `--reps` — целым числом.
- Пустой/неправильный сплит: проверьте `--split` (элементы через запятую).
