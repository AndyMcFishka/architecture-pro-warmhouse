"""Run end-to-end API checks against the Compose MVP (stdlib only)."""
import json
import os
import time
import sys
import subprocess
from pathlib import Path
import uuid
from datetime import datetime, timedelta, timezone
from urllib.request import Request, urlopen
from urllib.error import HTTPError

BASE = os.environ.get('MVP_URL', 'http://localhost:8090')


def call(path, data=None, method=None, status=200, key=None):
    """Assert HTTP status and decode the JSON body."""
    headers = {'Content-Type': 'application/json'}
    if key:
        headers['Idempotency-Key'] = key
    req = Request(BASE + path, data=None if data is None else json.dumps(data).encode(), headers=headers, method=method)
    try:
        response = urlopen(req, timeout=15)
    except HTTPError as error:
        response = error
    with response:
        raw = response.read()
        assert response.status == status, (path, response.status, status, raw)
        return json.loads(raw) if raw else None


def wait_for(check, seconds=25):
    """Wait for a background poll without assuming an exact scheduling time."""
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        if result := check():
            return result
        time.sleep(0.5)
    raise AssertionError('Background work did not complete')


def main():
    """Exercise the real service boundaries, retries and rule transitions."""
    suffix = str(uuid.uuid4())[:8]
    def device(kind, mode='HTTPS', telemetry='NONE'):
        return call('/api/v1/devices', {'name': kind + suffix, 'type': kind, 'location': 'check-' + suffix + '-' + mode, 'connection_mode': mode, 'telemetry_mode': telemetry, 'connection_settings': {'model': 'demo-v1', 'address': 'https://device.example.test', 'credential_ref': 'demo-secret'}}, status=201)['id']

    ids = {kind: device(kind) for kind in ('heating', 'lighting', 'gate', 'camera')}
    sensor = device('temperature', telemetry='PUSH')
    legacy = device('temperature', 'LEGACY', 'PULL')
    legacy_heater = device('heating', 'LEGACY')
    public = call(f'/api/v1/devices/{sensor}')
    assert 'credential_ref' not in public['connection_settings']
    registered = call('/api/v1/devices')
    assert set(ids.values()) | {sensor, legacy, legacy_heater} <= {d['id'] for d in registered}
    assert all('connection_settings' not in d for d in registered)
    filtered = call(f'/api/v1/devices?type=temperature&connection_mode=LEGACY&location=check-{suffix}-LEGACY')
    assert [d['id'] for d in filtered] == [legacy]
    assert call(f'/api/v1/devices?location=missing-{suffix}') == []
    receipts = []
    for kind, service, field in [('heating', 'heating', 'enabled'), ('lighting', 'lighting', 'enabled'), ('gate', 'gates', 'locked')]:
        path = f'/api/v1/{service}/devices/{ids[kind]}/commands'
        key = str(uuid.uuid4())
        command = call(path, {field: True}, key=key)
        assert command['status'] == 'SUCCEEDED', command
        receipts.append((path, field, key, command))
        assert call(path, {field: True}, key=key) == command
        call(path, {field: False}, key=key, status=409)
        call(path, {field: 'yes'}, key=str(uuid.uuid4()), status=400)
        state = call(f'/api/v1/{service}/devices/{ids[kind]}/state')
        assert state['state'] == ('LOCKED' if kind == 'gate' else 'ON')
        assert call(f'/api/v1/{service}/commands/{command["id"]}') == command
    call(f'/api/v1/lighting/devices/{ids["gate"]}/commands', {'enabled': True}, key=str(uuid.uuid4()), status=422)
    assert call(f'/api/v1/heating/devices/{legacy_heater}/commands', {'enabled': True}, key=str(uuid.uuid4()))['status'] == 'SUCCEEDED'
    assert call(f'/api/v1/heating/devices/{legacy_heater}/state')['state'] == 'ON'
    video = call(f'/api/v1/video/cameras/{ids["camera"]}/stream-link')
    assert video['url'].startswith('https://camera.example.test/') and 'expires_at' in video
    call(f'/api/v1/video/cameras/{sensor}/stream-link', status=422)

    def push(value, measured=None):
        n = {'device_id': sensor, 'measurement_id': str(uuid.uuid4()), 'metric': 'temperature', 'value': value, 'unit': '°C', 'measured_at': (measured or datetime.now(timezone.utc)).isoformat(timespec='milliseconds')}
        call('/api/v1/telemetry/measurements', n, status=201)
        return n
    n = push(20)
    call('/api/v1/telemetry/measurements', n)
    call('/api/v1/telemetry/measurements', {**n, 'value': 21}, status=409)
    push(5, datetime.now(timezone.utc) - timedelta(minutes=10))
    assert call(f'/api/v1/telemetry/devices/{sensor}/latest?metric=temperature')['value'] == 20
    assert call(f'/api/v1/sensors/{sensor}')['value'] == 20
    # Current legacy reads reach the sensor; push readings still come from storage.
    for route in (f'/api/v1/sensors/{legacy}',
                  f'/api/v1/sensors/temperature/check-{suffix}-LEGACY'):
        first, second = call(route), call(route)
        assert first['status'] == second['status'] == 'active'
        assert first['value'] != second['value'], (route, first, second)
    first_list, second_list = call('/api/v1/sensors'), call('/api/v1/sensors')
    assert next(s for s in first_list if s['id'] == legacy)['value'] != next(s for s in second_list if s['id'] == legacy)['value']
    # Background polling independently fills legacy history.
    history_path = f'/api/v1/telemetry/devices/{legacy}/measurements?metric=temperature&from=2020-01-01T00:00:00Z&to=2099-01-01T00:00:00Z'
    wait_for(lambda: call(history_path))
    rule_input = {'enabled': True, 'sensor_id': sensor, 'metric': 'temperature', 'comparison': 'LT', 'threshold': 18, 'unit': '°C', 'max_age_seconds': 60, 'action': 'HEATING_OFF', 'target_device_id': ids['heating']}
    rule = call('/api/v1/scenarios/rules', rule_input, status=201)
    path = '/api/v1/scenarios/rules/' + rule['id']
    assert call(path + '/runs') == []
    push(17)
    runs = wait_for(lambda: (r if r and r[0]['status'] == 'SUCCEEDED' else None) if (r := call(path + '/runs')) else None)
    assert len(runs) == 1
    assert call(f'/api/v1/heating/devices/{ids["heating"]}/state')['state'] == 'OFF'
    call(path, rule_input, method='PUT')
    time.sleep(6)
    assert len(call(path + '/runs')) == 1, 'Unchanged PUT must not reset previous_match'
    push(20)
    time.sleep(6)
    push(17)
    wait_for(lambda: len(call(path + '/runs')) == 2)
    call(path + '/enabled', {'enabled': False}, method='PATCH')
    assert len(call(path + '/runs?limit=1&offset=1')) == 1
    assert call(path + '/runs?limit=1&offset=2') == []
    call(path + '/runs?limit=oops', status=400)
    call('/api/v1/scenarios/rules', {**rule_input, 'enabled': None}, status=400)
    history = f'/api/v1/telemetry/devices/{sensor}/measurements?metric=temperature&from=2020-01-01T00:00:00Z&to=2099-01-01T00:00:00Z&limit=1'
    first, second = call(history), call(history + '&offset=1')
    assert len(first) == len(second) == 1 and first[0]['measurement_id'] != second[0]['measurement_id']
    # A timestamp with nanoseconds must remain idempotent after PostgreSQL persistence.
    precise = push(17)
    precise['measurement_id'] = str(uuid.uuid4())
    precise['measured_at'] = precise['measured_at'].replace('+00:00', '123456Z')
    call('/api/v1/telemetry/measurements', precise, status=201)
    call('/api/v1/telemetry/measurements', precise)
    if '--restart' in sys.argv:
        root = Path(__file__).resolve().parents[2]
        compose = ['docker', 'compose', '-f', str(root / 'apps/docker-compose.yml')]
        subprocess.run(compose + ['restart', 'connectivity', 'telemetry', 'heating', 'lighting', 'gates', 'video', 'scenarios', 'app'], check=True)
        subprocess.run(compose + ['up', '-d', '--wait'], check=True)
        for endpoint, field, key, saved in receipts:
            assert call(endpoint, {field: True}, key=key) == saved, 'Receipt changed after restart'
            state = call(endpoint.removesuffix('commands') + 'state')
            assert state['state'] == ('UNLOCKED' if field == 'locked' else 'OFF'), 'Retry executed mock again'
        assert call(f'/api/v1/telemetry/devices/{sensor}/latest?metric=temperature')['value'] == 17
        assert len(call(path + '/runs')) == 2
        print('PASS: durable measurements, runs, command receipts; mock state reset without re-delivery')
    print('PASS: registry, legacy and push telemetry, all actuators, retries, video, scenario transitions')

if __name__ == '__main__':
    main()
