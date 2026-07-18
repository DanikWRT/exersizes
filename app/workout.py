#!/usr/bin/env python3
"""Workout_program — minimal workout tracking CLI (no external deps).

Data files live under app/data:
- exercises.json
- logs.json
- plans.json
"""

from __future__ import annotations

import argparse
import datetime as _dt
import json
import os
import re
import sys
from typing import Any, Dict, List, Optional, Tuple


APP_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(APP_DIR, "data")
EXERCISES_PATH = os.path.join(DATA_DIR, "exercises.json")
LOGS_PATH = os.path.join(DATA_DIR, "logs.json")
PLANS_PATH = os.path.join(DATA_DIR, "plans.json")


def _ensure_data_dir() -> None:
    os.makedirs(DATA_DIR, exist_ok=True)


def _load_json(path: str, default: Any) -> Any:
    if not os.path.exists(path):
        return default
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def _save_json(path: str, data: Any) -> None:
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")
    os.replace(tmp, path)


def _norm(s: str) -> str:
    return re.sub(r"\s+", " ", s.strip()).casefold()


def _slugify(name: str) -> str:
    s = name.strip().casefold()
    s = s.replace("ё", "е")
    s = re.sub(r"[^a-z0-9а-я\s-]", "", s)
    s = re.sub(r"[\s_]+", "-", s).strip("-")
    return s or "exercise"


def _parse_date(s: str) -> str:
    try:
        d = _dt.date.fromisoformat(s)
    except ValueError:
        raise argparse.ArgumentTypeError("date must be YYYY-MM-DD")
    return d.isoformat()


def _parse_split(split: str) -> List[str]:
    parts = [p.strip() for p in split.split(",") if p.strip()]
    if not parts:
        raise argparse.ArgumentTypeError("split must have at least one part")
    return parts


def _exercise_lookup(exercises: List[Dict[str, Any]], query: str) -> Optional[Dict[str, Any]]:
    q = _norm(query)
    for ex in exercises:
        if _norm(ex.get("name", "")) == q:
            return ex
        for a in ex.get("aliases", []) or []:
            if _norm(a) == q:
                return ex
    return None


def cmd_add_exercise(args: argparse.Namespace) -> int:
    _ensure_data_dir()
    exercises: List[Dict[str, Any]] = _load_json(EXERCISES_PATH, [])

    existing = _exercise_lookup(exercises, args.name)
    if existing:
        # merge aliases if provided
        changed = False
        for a in args.alias or []:
            if _norm(a) not in {_norm(x) for x in (existing.get("aliases") or [])}:
                existing.setdefault("aliases", []).append(a)
                changed = True
        if changed:
            _save_json(EXERCISES_PATH, exercises)
            print(f"Updated exercise: {existing['name']}")
        else:
            print(f"Exercise already exists: {existing['name']}")
        return 0

    ex = {
        "id": _slugify(args.name),
        "name": args.name.strip(),
        "muscle_group": args.muscle_group.strip(),
        "aliases": [a.strip() for a in (args.alias or []) if a.strip()],
    }
    exercises.append(ex)
    _save_json(EXERCISES_PATH, exercises)
    print(f"Added exercise: {ex['name']}")
    return 0


def cmd_list_exercises(args: argparse.Namespace) -> int:
    _ensure_data_dir()
    exercises: List[Dict[str, Any]] = _load_json(EXERCISES_PATH, [])
    if not exercises:
        print("No exercises yet. Use add-exercise.")
        return 0

    for ex in sorted(exercises, key=lambda x: _norm(x.get("name", ""))):
        aliases = ex.get("aliases") or []
        alias_str = f" (aliases: {', '.join(aliases)})" if aliases else ""
        print(f"- {ex.get('name')} [{ex.get('muscle_group')}]{alias_str}")
    return 0


def cmd_log_set(args: argparse.Namespace) -> int:
    _ensure_data_dir()
    exercises: List[Dict[str, Any]] = _load_json(EXERCISES_PATH, [])
    logs: List[Dict[str, Any]] = _load_json(LOGS_PATH, [])

    ex = _exercise_lookup(exercises, args.exercise)
    if not ex:
        print(
            "Exercise not found. Add it first with add-exercise, or add an alias.\n"
            f"Query was: {args.exercise}",
            file=sys.stderr,
        )
        return 2

    entry = {
        "date": args.date,
        "exercise": ex["name"],
        "weight": float(args.weight),
        "reps": int(args.reps),
        "notes": args.notes,
        "source": None,
    }
    logs.append(entry)
    _save_json(LOGS_PATH, logs)
    print(f"Logged: {entry['date']} — {entry['exercise']} {entry['weight']}kg x{entry['reps']}")
    return 0


def _dates_7(start_iso: str) -> List[str]:
    d0 = _dt.date.fromisoformat(start_iso)
    return [(d0 + _dt.timedelta(days=i)).isoformat() for i in range(7)]


def cmd_plan_week(args: argparse.Namespace) -> int:
    _ensure_data_dir()
    plans: List[Dict[str, Any]] = _load_json(PLANS_PATH, [])

    dates = _dates_7(args.start)
    split_parts = args.split_parts

    days = []
    for i, date in enumerate(dates):
        focus = split_parts[i % len(split_parts)]
        days.append({"date": date, "focus": focus})

    # replace if exists
    plans = [p for p in plans if p.get("start") != args.start]
    plans.append({"start": args.start, "days": days, "split": ",".join(split_parts)})

    _save_json(PLANS_PATH, plans)
    print(f"Planned week starting {args.start}")
    return 0


def cmd_show_week(args: argparse.Namespace) -> int:
    _ensure_data_dir()
    plans: List[Dict[str, Any]] = _load_json(PLANS_PATH, [])
    plan = next((p for p in plans if p.get("start") == args.start), None)
    if not plan:
        print("No plan for that start date. Use plan-week.")
        return 2

    print(f"Week plan starting {args.start} (split: {plan.get('split')})")
    for day in plan.get("days", []):
        print(f"- {day.get('date')}: {day.get('focus')}")
    return 0


def cmd_report_week(args: argparse.Namespace) -> int:
    _ensure_data_dir()
    logs: List[Dict[str, Any]] = _load_json(LOGS_PATH, [])

    start = _dt.date.fromisoformat(args.start)
    end = start + _dt.timedelta(days=7)

    week_logs: List[Dict[str, Any]] = []
    for e in logs:
        try:
            d = _dt.date.fromisoformat(e.get("date"))
        except Exception:
            continue
        if start <= d < end:
            week_logs.append(e)

    total_sets = len(week_logs)
    total_reps = 0
    total_tonnage = 0.0
    per_ex: Dict[str, Dict[str, Any]] = {}

    for e in week_logs:
        ex = e.get("exercise") or "(unknown)"
        w = float(e.get("weight") or 0)
        r = int(e.get("reps") or 0)
        total_reps += r
        total_tonnage += w * r

        agg = per_ex.setdefault(ex, {"sets": 0, "reps": 0, "tonnage": 0.0})
        agg["sets"] += 1
        agg["reps"] += r
        agg["tonnage"] += w * r

    print(f"Report for week starting {args.start} (7 days)")
    print(f"Total sets: {total_sets}")
    print(f"Total reps: {total_reps}")
    print(f"Total tonnage: {total_tonnage:.2f}")
    print("\nBy exercise:")
    for ex_name in sorted(per_ex.keys(), key=_norm):
        a = per_ex[ex_name]
        print(f"- {ex_name}: sets={a['sets']}, reps={a['reps']}, tonnage={a['tonnage']:.2f}")

    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(prog="workout.py")
    sub = p.add_subparsers(dest="command", required=True)

    p_add = sub.add_parser("add-exercise", help="Add exercise")
    p_add.add_argument("--name", required=True)
    p_add.add_argument("--muscle_group", required=True)
    p_add.add_argument("--alias", action="append", default=[])
    p_add.set_defaults(func=cmd_add_exercise)

    p_list = sub.add_parser("list-exercises", help="List exercises")
    p_list.set_defaults(func=cmd_list_exercises)

    p_log = sub.add_parser("log-set", help="Log a set")
    p_log.add_argument("--date", required=True, type=_parse_date)
    p_log.add_argument("--exercise", required=True)
    p_log.add_argument("--weight", required=True, type=float)
    p_log.add_argument("--reps", required=True, type=int)
    p_log.add_argument("--notes", default=None)
    p_log.set_defaults(func=cmd_log_set)

    p_plan = sub.add_parser("plan-week", help="Create week plan")
    p_plan.add_argument("--start", required=True, type=_parse_date)
    p_plan.add_argument("--split", required=True)
    p_plan.set_defaults(func=cmd_plan_week)

    p_show = sub.add_parser("show-week", help="Show week plan")
    p_show.add_argument("--start", required=True, type=_parse_date)
    p_show.set_defaults(func=cmd_show_week)

    p_rep = sub.add_parser("report-week", help="Weekly report")
    p_rep.add_argument("--start", required=True, type=_parse_date)
    p_rep.set_defaults(func=cmd_report_week)

    return p


def main(argv: Optional[List[str]] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    # post-process split parsing for plan-week
    if getattr(args, "command", None) == "plan-week":
        args.split_parts = _parse_split(args.split)

    return int(args.func(args))


if __name__ == "__main__":
    raise SystemExit(main())
