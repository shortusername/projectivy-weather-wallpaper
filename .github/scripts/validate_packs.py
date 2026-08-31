#!/usr/bin/env python3
"""Validate packs/index.json for a contributed wallpaper pack.

Checks schema conformance, id uniqueness, licence allow-list, asset
reachability and file size. Exits non-zero with a readable report so a
contributor can fix their PR without waiting on a human.
"""

import json
import sys
from pathlib import Path

import jsonschema
import requests

ROOT = Path(__file__).resolve().parents[2]
INDEX = ROOT / "packs" / "index.json"
SCHEMA = ROOT / "packs" / "schema.json"

# Bytes. Generous enough for good art, tight enough to keep a TV box happy.
LIMITS = {
    "static": 800 * 1024,
    "lottie": 2 * 1024 * 1024,
    "video": 8 * 1024 * 1024,
}

TIMEOUT = 20
errors: list[str] = []
warnings: list[str] = []


def check_asset(pack_id: str, key: str, url: str, kind: str) -> None:
    try:
        # HEAD first; some hosts don't implement it, so fall back to a
        # ranged GET rather than downloading the whole file.
        resp = requests.head(url, timeout=TIMEOUT, allow_redirects=True)
        if resp.status_code >= 400 or "content-length" not in resp.headers:
            resp = requests.get(
                url, timeout=TIMEOUT, allow_redirects=True,
                headers={"Range": "bytes=0-0"}, stream=True,
            )
    except requests.RequestException as exc:
        errors.append(f"{pack_id}/{key}: unreachable ({exc.__class__.__name__})")
        return

    if resp.status_code >= 400:
        errors.append(f"{pack_id}/{key}: HTTP {resp.status_code}")
        return

    size = resp.headers.get("content-length")
    if size is None:
        size = resp.headers.get("content-range", "").rpartition("/")[2]
    if size and size.isdigit():
        limit = LIMITS[kind]
        if int(size) > limit:
            errors.append(
                f"{pack_id}/{key}: {int(size) // 1024} KB exceeds the "
                f"{limit // 1024} KB limit for {kind} packs"
            )
    else:
        warnings.append(f"{pack_id}/{key}: size unknown, not checked")

    ctype = resp.headers.get("content-type", "").split(";")[0]
    expected = {
        "static": ("image/jpeg", "image/png", "image/webp"),
        "lottie": ("application/json", "text/plain", "text/json"),
        "video": ("video/mp4",),
    }[kind]
    if ctype and ctype not in expected:
        warnings.append(
            f"{pack_id}/{key}: content-type '{ctype}' unexpected for {kind}"
        )


def main() -> int:
    if not INDEX.exists():
        print(f"::error::{INDEX} not found")
        return 1

    try:
        index = json.loads(INDEX.read_text())
    except json.JSONDecodeError as exc:
        print(f"::error::index.json is not valid JSON: {exc}")
        return 1

    schema = json.loads(SCHEMA.read_text())
    validator = jsonschema.Draft7Validator(schema)
    for err in sorted(validator.iter_errors(index), key=lambda e: e.path):
        location = "/".join(str(p) for p in err.path) or "(root)"
        errors.append(f"schema at {location}: {err.message}")

    if errors:
        report(); return 1

    packs = index["packs"]

    seen: dict[str, int] = {}
    for i, pack in enumerate(packs):
        pid = pack["id"]
        if pid in seen:
            errors.append(f"duplicate pack id '{pid}' (also at index {seen[pid]})")
        seen[pid] = i

    for pack in packs:
        # The example entry ships with placeholder URLs; skip it.
        if pack["id"] == "example-photographic":
            warnings.append("skipping the example pack's placeholder URLs")
            continue
        for key, url in pack["assets"].items():
            check_asset(pack["id"], key, url, pack["kind"])

    report()
    print(f"\nChecked {len(packs)} pack(s).")
    return 1 if errors else 0


def report() -> None:
    for w in warnings:
        print(f"::warning::{w}")
    for e in errors:
        print(f"::error::{e}")
    if not errors:
        print("All pack checks passed.")


if __name__ == "__main__":
    sys.exit(main())
