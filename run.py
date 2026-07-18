#!/usr/bin/env python
"""Thin runner to satisfy exec preflight (direct python file execution).

Usage:
  python run.py <args...>

It forwards all CLI args to app.workout.main().
"""

from app.workout import main

if __name__ == "__main__":
    main()
