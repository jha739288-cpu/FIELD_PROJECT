#!/usr/bin/env python3
"""Simulated current-sensor device for LabMarket.

This is a SOFTWARE SIMULATOR — it is not hardware and never claims to be.
It speaks the same HTTP contract a future ESP32 + current sensor will use,
so backend work can proceed (and be tested) before hardware exists.

Contract:
  POST {base}/api/v1/sensors/events
  Header: X-Sensor-Key: <device key provisioned via POST /api/v1/equipment/{id}/sensor-key>
  Body:   {"equipmentCode": "...", "status": "IDLE|IN_USE|OFFLINE|FAULT",
           "currentValue": 1.8, "timestamp": "2026-01-01T10:00:00Z"}

Scenarios:
  normal   idle -> ramp-up IN_USE readings -> idle        (a real usage session)
  idle     steady IDLE reports at ~0 A
  offline  OFFLINE reports (dead radio / unplugged sensor)
  invalid  malformed events the backend must reject (bad status, negative
           current, unknown equipment code, wrong key) — expects 4xx
  delayed  10-minute-old timestamps — accepted but flagged stale=true

Usage:
  python3 sensor_sim.py --base http://localhost:8080 --code OSC-001 --key <key> --scenario normal
  python3 sensor_sim.py --base http://localhost:8080 --code OSC-001 --key <key> --scenario invalid

Exit code is 0 only when every event got the expected response.
Only the Python standard library is required.
"""

import argparse
import json
import random
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timedelta, timezone


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def iso(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def post(base: str, key: str, event: dict, label: str):
    """Returns (http_status, body_dict). Status -1 on connection failure."""
    url = base.rstrip("/") + "/api/v1/sensors/events"
    req = urllib.request.Request(
        url,
        data=json.dumps(event).encode(),
        headers={"Content-Type": "application/json", "X-Sensor-Key": key or ""},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            return res.status, json.loads(res.read().decode())
    except urllib.error.HTTPError as e:
        try:
            body = json.loads(e.read().decode())
        except Exception:
            body = {}
        return e.code, body
    except Exception as e:  # connection refused etc.
        print(f"[{label}] ERROR {e}")
        return -1, {}


def build_events(scenario: str, code: str):
    """Each event is (label, equipment_code, status, current, instant)."""
    now = utc_now()
    if scenario == "normal":
        out = [("boot-idle", code, "IDLE", 0.0, now)]
        for i in range(1, 6):
            out.append((f"use-{i}", code, "IN_USE",
                        round(random.uniform(1.2, 2.4), 2),
                        now + timedelta(seconds=2 * i)))
        out.append(("back-idle", code, "IDLE", 0.0, now + timedelta(seconds=15)))
        return out
    if scenario == "idle":
        return [(f"idle-{i}", code, "IDLE", 0.0, now + timedelta(seconds=2 * i))
                for i in range(5)]
    if scenario == "offline":
        return [(f"offline-{i}", code, "OFFLINE", 0.0, now + timedelta(seconds=2 * i))
                for i in range(3)]
    if scenario == "invalid":
        return [
            ("bad-status", code, "MELTDOWN", 1.0, now),
            ("neg-current", code, "IN_USE", -3.0, now),
            ("unknown-code", "NOPE-999", "IN_USE", 1.0, now),
            ("bad-key", code, "IN_USE", 1.0, now),
        ]
    if scenario == "delayed":
        old = now - timedelta(minutes=10)
        return [(f"late-{i}", code, "IN_USE",
                 round(random.uniform(1.0, 2.0), 2),
                 old + timedelta(seconds=i)) for i in range(3)]
    raise ValueError(f"unknown scenario: {scenario}")


def main() -> int:
    ap = argparse.ArgumentParser(description="Simulated LabMarket current-sensor device.")
    ap.add_argument("--base", default="http://localhost:8080", help="Backend base URL")
    ap.add_argument("--code", required=True, help="Equipment code, e.g. OSC-001")
    ap.add_argument("--key", default="", help="Device key (provision one via the API)")
    ap.add_argument("--scenario", default="normal",
                    choices=["normal", "idle", "offline", "invalid", "delayed"])
    ap.add_argument("--interval", type=float, default=0.5, help="Seconds between events")
    ap.add_argument("--wrong-key", action="store_true",
                    help="Send an invalid device key with every event (expects 401)")
    args = ap.parse_args()

    print("LabMarket sensor SIMULATOR (no hardware involved) — scenario:", args.scenario)
    failures = 0
    for label, code, status, current, at in build_events(args.scenario, args.code):
        key = "wrong-key" if (args.wrong_key or label == "bad-key") else args.key
        event = {"equipmentCode": code, "status": status,
                 "currentValue": current, "timestamp": iso(at)}
        http_status, body = post(args.base, key, event, label)

        if args.wrong_key or label == "bad-key":
            ok, want = http_status == 401, "401"
        elif args.scenario == "invalid":
            ok, want = 400 <= http_status < 500, "4xx"
        elif args.scenario == "delayed":
            ok, want = http_status == 201 and body.get("stale") is True, "201+stale"
        else:
            ok, want = http_status == 201, "201"

        detail = body.get("message", "") if isinstance(body, dict) else ""
        print(f"[{label}] HTTP {http_status} (want {want}) {detail}")
        if isinstance(body, dict) and "appliedEquipmentStatus" in body:
            print(f"           applied={body['appliedEquipmentStatus']} "
                  f"stale={body.get('stale')}")
        failures += 0 if ok else 1
        time.sleep(args.interval)

    print("done:", "OK — all expectations met" if failures == 0 else f"{failures} FAILURE(S)")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
