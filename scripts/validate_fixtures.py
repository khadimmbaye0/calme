#!/usr/bin/env python3
"""Validate the repository's static product fixtures using only the Python standard library."""

import json
import re
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ISO_DATE = re.compile(r"\d{4}-\d{2}-\d{2}\Z")
INTENTS = {
    "create_event",
    "update_event",
    "delete_event",
    "create_task",
    "plan_project",
    "reschedule",
    "lighten_day",
    "set_energy",
    "set_availability",
    "query_today",
    "explain_decision",
    "set_block_schedule",
}


def load_json(relative_path: str):
    with (ROOT / relative_path).open(encoding="utf-8") as file:
        return json.load(file)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def valid_date(value: object, field: str, record: object) -> None:
    require(isinstance(value, str) and ISO_DATE.fullmatch(value) is not None, f"{field} must be YYYY-MM-DD: {record}")
    date.fromisoformat(value)


def validate_blocks() -> None:
    blocks = load_json("fixtures/alternance-blocks.json")
    require(isinstance(blocks, list) and len(blocks) == 14, "alternance fixture must contain exactly 14 blocks")
    expected_types = ["SCHOOL", "COMPANY"] * 7
    for number, (block, expected_type) in enumerate(zip(blocks, expected_types), start=1):
        require(block["number"] == number, f"block numbering must be consecutive at {number}")
        require(block["type"] == expected_type, f"unexpected type for block {number}")
        valid_date(block["start_date"], "start_date", block)
        valid_date(block["end_date"], "end_date", block)
        require(block["start_date"] <= block["end_date"], f"block {number} ends before it starts")
        require(block["default_availability"] is None, "availability defaults must remain unset until decided")
    require(blocks[-1]["reserved_time_ratio"] == 0.2, "block 14 must reserve 20% for PFE-related work")
    for previous, current in zip(blocks, blocks[1:]):
        require(previous["end_date"] < current["start_date"], f"blocks overlap: {previous['number']} and {current['number']}")


def validate_commands() -> None:
    commands = load_json("fixtures/agent-commands.json")
    require(isinstance(commands, list) and len(commands) == 20, "agent command fixture must contain 20 variants")
    ids = set()
    counts = {"create_event_and_availability": 0, "plan_project": 0, "lighten_day": 0}
    for command in commands:
        require(command["id"] not in ids, f"duplicate command id: {command['id']}")
        ids.add(command["id"])
        require(command["group"] in counts, f"unexpected command group: {command['group']}")
        counts[command["group"]] += 1
        require(isinstance(command["text"], str) and command["text"].strip(), f"empty command: {command['id']}")
        require(
            isinstance(command["expected_intents"], list)
            and command["expected_intents"]
            and set(command["expected_intents"]) <= INTENTS,
            f"invalid expected intents: {command['id']}",
        )
    require(counts == {"create_event_and_availability": 7, "plan_project": 7, "lighten_day": 6}, f"unexpected variant distribution: {counts}")


def validate_schema_json() -> None:
    schema = load_json("docs/agent-contract.schema.json")
    require(schema.get("$schema") == "https://json-schema.org/draft/2020-12/schema", "agent contract must declare JSON Schema 2020-12")
    require(set(schema.get("$defs", {})) >= INTENTS, "agent contract is missing one or more MVP intent definitions")


def main() -> int:
    try:
        validate_blocks()
        validate_commands()
        validate_schema_json()
    except (OSError, json.JSONDecodeError, KeyError, TypeError, ValueError) as error:
        print(f"Fixture validation failed: {error}", file=sys.stderr)
        return 1
    print("Fixtures and agent contract JSON are valid.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
