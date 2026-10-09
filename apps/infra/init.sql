-- Re-runnable migration, also applied to existing task-5 volumes.
ALTER TABLE sensors ADD COLUMN IF NOT EXISTS connection_mode text NOT NULL DEFAULT 'LEGACY';
ALTER TABLE sensors ADD COLUMN IF NOT EXISTS telemetry_mode text NOT NULL DEFAULT 'PULL';
ALTER TABLE sensors ADD COLUMN IF NOT EXISTS connection_settings jsonb NOT NULL DEFAULT '{"model":"demo-v1","address":"https://device.example.test"}';
CREATE TABLE IF NOT EXISTS device_types(code text PRIMARY KEY, kind text NOT NULL, metric text, unit text);
INSERT INTO device_types VALUES ('temperature','SENSOR','temperature','°C'),('heating','HEATING',NULL,NULL),('lighting','LIGHT',NULL,NULL),('gate','GATE',NULL,NULL),('camera','CAMERA',NULL,NULL) ON CONFLICT DO NOTHING;
DO $$
DECLARE svc text;
BEGIN
 FOREACH svc IN ARRAY ARRAY['telemetry','connectivity','heating','lighting','gates','scenarios'] LOOP
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname=svc) THEN
   EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L',svc,svc);
  END IF;
  EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I AUTHORIZATION %I',svc,svc);
  EXECUTE format('ALTER ROLE %I SET search_path TO %I',svc,svc);
 END LOOP;
END $$;
SET ROLE telemetry;
CREATE TABLE IF NOT EXISTS measurements(device_id int NOT NULL,measurement_id uuid NOT NULL,metric text NOT NULL,value double precision NOT NULL,unit text NOT NULL,measured_at timestamptz NOT NULL,received_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(device_id,measurement_id));
CREATE INDEX IF NOT EXISTS latest ON measurements(device_id,measured_at DESC);
RESET ROLE;
SET ROLE connectivity;
CREATE TABLE IF NOT EXISTS deliveries(command_service text NOT NULL,command_id uuid NOT NULL,device_id int NOT NULL,operation text NOT NULL,parameters jsonb NOT NULL,status text NOT NULL,updated_at timestamptz NOT NULL DEFAULT now(),PRIMARY KEY(command_service,command_id));
RESET ROLE;
DO $$
DECLARE svc text;
BEGIN
 FOREACH svc IN ARRAY ARRAY['heating','lighting','gates'] LOOP
  EXECUTE format('SET ROLE %I',svc);
  EXECUTE 'CREATE TABLE IF NOT EXISTS commands(id uuid PRIMARY KEY,idempotency_key uuid UNIQUE NOT NULL,device_id int NOT NULL,desired boolean NOT NULL,status text NOT NULL,created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now())';
  RESET ROLE;
 END LOOP;
END $$;
SET ROLE scenarios;
CREATE TABLE IF NOT EXISTS rules(id uuid PRIMARY KEY,body jsonb NOT NULL,previous_match boolean NOT NULL DEFAULT false,created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());
CREATE TABLE IF NOT EXISTS runs(id uuid PRIMARY KEY,rule_id uuid NOT NULL REFERENCES rules(id),action text NOT NULL,target_device_id int NOT NULL,command_service text NOT NULL,command_id uuid,status text NOT NULL DEFAULT 'PENDING',created_at timestamptz NOT NULL DEFAULT now(),updated_at timestamptz NOT NULL DEFAULT now());
RESET ROLE;
