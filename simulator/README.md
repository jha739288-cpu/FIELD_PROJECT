# Sensor simulator

`sensor_sim.py` pretends to be an ESP32 + current sensor so the ingest path can be
built and tested with **no hardware**. It is explicitly a simulator: every run
prints `SIMULATOR (no hardware involved)`, and nothing in the backend treats its
data as trusted — events are authenticated, validated, range-checked and
staleness-flagged like any hostile input.

## Setup (one time per equipment item)

Provision a device key as staff/admin (raw key is shown **once**):

```bash
curl -s -X POST localhost:8080/api/v1/equipment/1/sensor-key \
  -H "Authorization: Bearer $STAFF_JWT"
# {"equipmentId":1,"equipmentCode":"OSC-001","sensorKey":"..."}
```

## Run scenarios (Python 3, standard library only)

```bash
python3 sensor_sim.py --code OSC-001 --key <key> --scenario normal   # idle → usage → idle
python3 sensor_sim.py --code OSC-001 --key <key> --scenario idle     # steady idle
python3 sensor_sim.py --code OSC-001 --key <key> --scenario offline  # sensor offline
python3 sensor_sim.py --code OSC-001 --key <key> --scenario invalid  # must all be rejected (4xx)
python3 sensor_sim.py --code OSC-001 --key <key> --scenario delayed  # 10-min-old → stale=true
python3 sensor_sim.py --code OSC-001 --key <key> --scenario normal --wrong-key  # 401 demo
```

Expected backend effects: `IN_USE` (+ draw ≥ 0.10 A) flips AVAILABLE equipment to
`IN_USE`; `IDLE` returns it to `AVAILABLE` (unless a checked-in booking owns it);
`OFFLINE` → `SENSOR_OFFLINE`; `FAULT` → `MAINTENANCE`. `MAINTENANCE` items are
never auto-changed. Every accepted event lands in `sensor_events` + `audit_events`.

## Replacing the simulator with a real ESP32

See `esp32_notes.md`. In short: flash firmware that reads the current sensor,
POSTs the same JSON with the provisioned `X-Sensor-Key`, and retries with
backoff — then delete nothing; the backend cannot tell the difference (by design).
