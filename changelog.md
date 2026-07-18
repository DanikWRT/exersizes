# Workout_program — Changelog

## Unreleased
- TODO:
  - Уточнить неоднозначности импорта из `raw_import.md` (см. `spec.md`).
  - Улучшить устойчивость записи (конфликты при параллельном запуске CLI).
  - Расширить модель “тренировка как сессия” (если понадобится), и/или перейти на SQLite.

## 0.2.0 — 2026-04-17 (MVP)
- Added:
  - CLI-команды для банка упражнений (`add-exercise`, `list-exercises`).
  - CLI-команда логирования подходов (`log-set`).
  - Планирование недели: создание и просмотр (`plan-week`, `show-week`).
  - Недельный отчёт по логам (`report-week`).
  - Импорт исходных данных из `raw_import.md` в `app/data/logs.json`.
  - Документация проекта: `README.md`, `spec.md`, `architecture.md`, `api.md`, `user-guide.md`.

## 0.1.0 — 2026-04-17
- Added:
  - Первичная структура проекта и JSON-хранилища
