# ESP32 replacement notes (for later hardware work)

The backend does not know or care whether reports come from `sensor_sim.py` or a
microcontroller — the contract below is the whole integration surface.

## Device contract

- Transport: `POST {base}/api/v1/sensors/events` (plain HTTPS in production).
- Auth: header `X-Sensor-Key: <key>` where `<key>` is the raw value returned once by
  `POST /api/v1/equipment/{id}/sensor-key` (staff/admin). Store it in ESP32 NVS /
  flash, never in firmware source. Rotate with the same endpoint if leaked.
- Body (JSON):
  ```json
  {"equipmentCode": "OSC-001", "status": "IN_USE", "currentValue": 1.8,
   "timestamp": "2026-09-25T10:00:00Z"}
  ```
  - `equipmentCode`: the item's code label (printed on the device sticker).
  - `status`: `IDLE` | `IN_USE` | `OFFLINE` | `FAULT` (anything else → 400).
  - `currentValue`: RMS amps, 0–100, may be omitted for status-only reports.
  - `timestamp`: device time, ISO-8601 UTC. Must be within −24 h / +60 s of server
    time, else 400; older than 5 min is stored with `stale: true`.

## Firmware sketch (Arduino core)

1. Calibrate the current sensor zero-offset at boot (average N idle samples).
2. Every 2–5 s: read RMS current, classify (`< 0.10 A` → IDLE else IN_USE),
   unless a fault pin / watchdog says OFFLINE/FAULT.
3. NTP-sync the clock at boot and daily (stale flags otherwise).
4. POST the JSON; on 401 stop and blink (key wrong — reprovision, don't retry fast);
   on 5xx/other errors back off exponentially; buffer at most a few reports in RTC
   RAM (server rejects anything older than 24 h anyway).
5. Power the ESP32 from the equipment's mains side so OFFLINE (missing reports)
   itself means something; the overdue module will treat silence as signal.

## Server behaviour recap

Accepted events are stored + audited and may move `currentStatus`
(IN_USE / AVAILABLE / SENSOR_OFFLINE / MAINTENANCE) per the rules in
`SensorService`. Items in `MAINTENANCE` never move automatically.
