import express, { NextFunction, Request, Response } from 'express';
import crypto from 'crypto';
import fs from 'fs';
import path from 'path';

const PORT = 3000;
const LOG_DIR = path.resolve(process.cwd(), 'logs');
const LOG_FILE = path.join(LOG_DIR, 'his-application.json');

fs.mkdirSync(LOG_DIR, { recursive: true });

const PATIENT_REGISTRY: Record<
  string,
  {
    patientId: string;
    fullName: string;
    dateOfBirth: string;
    bloodType: string;
    primaryDiagnosis: string;
    allergies: string[];
  }
> = {
  'PAT-1001': {
    patientId: 'PAT-1001',
    fullName: 'Nguyen Van A',
    dateOfBirth: '1984-05-14',
    bloodType: 'O+',
    primaryDiagnosis: 'Acute Bronchitis',
    allergies: ['Penicillin'],
  },
  'PAT-1002': {
    patientId: 'PAT-1002',
    fullName: 'Tran Thi B',
    dateOfBirth: '1972-11-03',
    bloodType: 'A-',
    primaryDiagnosis: 'Atrial Fibrillation',
    allergies: ['Sulfa Drugs'],
  },
};

const CONTROLLED_SUBSTANCES = new Set([
  'morphine',
  'fentanyl',
  'oxycodone',
  'midazolam',
  'hydromorphone',
]);

interface MdcContext {
  trace_id: string;
  doctor_id: string;
  department: string;
}

interface MdcRequest extends Request {
  mdc?: MdcContext;
}

function writeStructuredLog(
  level: 'INFO' | 'WARN' | 'ERROR',
  loggerName: string,
  message: string,
  fields: Record<string, unknown>
): void {
  const entry = {
    '@timestamp': new Date().toISOString(),
    '@version': '1',
    level,
    logger_name: loggerName,
    service: 'his-core',
    environment: 'production',
    message,
    ...fields,
  };
  const line = JSON.stringify(entry);
  fs.appendFileSync(LOG_FILE, `${line}\n`, 'utf8');
  console.log(line);
}

function mdcHeaderMiddleware(req: MdcRequest, res: Response, next: NextFunction): void {
  const traceId = (req.header('X-Trace-Id') || '').trim() || crypto.randomUUID();
  const doctorId = (req.header('X-Doctor-Id') || '').trim() || 'DR-UNKNOWN';
  const department = (req.header('X-Department') || '').trim() || 'GENERAL';

  res.setHeader('X-Trace-Id', traceId);
  req.mdc = {
    trace_id: traceId,
    doctor_id: doctorId,
    department,
  };
  next();
}

function parseHl7Summary(raw: string): { messageType: string; patientId: string } {
  let messageType = 'UNKNOWN';
  let patientId = 'UNKNOWN';
  const segments = raw.split(/\r\n|\r|\n|\\n/);
  for (const segment of segments) {
    const fields = segment.split('|');
    if (fields[0] === 'MSH' && fields.length > 8) {
      messageType = fields[8] || 'UNKNOWN';
    } else if (fields[0] === 'PID' && fields.length > 3) {
      patientId = (fields[3] || 'UNKNOWN').split('^')[0] || 'UNKNOWN';
    }
  }
  return { messageType, patientId };
}

const app = express();
app.use(express.json());
app.use(mdcHeaderMiddleware);

app.get('/', (_req: Request, res: Response) => {
  res.json({
    service: 'his-core',
    mode: '100% backend REST API (ELK Stack + Filebeat demo)',
    status: 'UP',
    endpoints: [
      'GET /health',
      'GET /api/v1/patients/:patientId/emr',
      'POST /api/v1/patients/:patientId/prescriptions',
      'POST /api/v1/interoperability/hl7',
      'POST /api/v1/interoperability/fhir',
      'GET /api/v1/audit/logs',
    ],
  });
});

app.get('/health', (_req: Request, res: Response) => {
  res.json({
    status: 'UP',
    service: 'his-core',
    timestamp: new Date().toISOString(),
  });
});

app.get('/api/v1/patients/:patientId/emr', (req: MdcRequest, res: Response) => {
  const startNs = process.hrtime.bigint();
  const patientId = String(req.params.patientId);
  const mdc = req.mdc!;

  const base = PATIENT_REGISTRY[patientId] || {
    patientId,
    fullName: 'Clinical Demo Patient',
    dateOfBirth: '1990-01-01',
    bloodType: 'B+',
    primaryDiagnosis: 'Observation',
    allergies: [],
  };

  writeStructuredLog(
    'INFO',
    'com.hospital.his.controller.PatientController',
    `Electronic Medical Record (EMR) accessed for patient ${patientId}`,
    {
      ...mdc,
      event_type: 'PATIENT_ACCESS',
      resource_type: 'EMR',
      patient_id: patientId,
    }
  );

  const durationMs = Number((process.hrtime.bigint() - startNs) / 1_000_000n);
  writeStructuredLog(
    'INFO',
    'HIPAA_AUDIT',
    `Clinical action completed: event=PATIENT_ACCESS resource=EMR durationMs=${durationMs}`,
    {
      ...mdc,
      event_type: 'PATIENT_ACCESS',
      resource_type: 'EMR',
      patient_id: patientId,
      outcome: 'SUCCESS',
      duration_ms: String(durationMs),
    }
  );

  res.json({
    ...base,
    accessedByDoctorId: mdc.doctor_id,
    department: mdc.department,
  });
});

app.post('/api/v1/patients/:patientId/prescriptions', (req: MdcRequest, res: Response) => {
  const startNs = process.hrtime.bigint();
  const patientId = String(req.params.patientId);
  const mdc = req.mdc!;
  const drugName = String(req.body?.drugName || '').trim();
  const dosage = String(req.body?.dosage || '').trim();

  if (!drugName || !dosage) {
    res.status(400).json({
      error: 'BAD_REQUEST',
      message: 'drugName and dosage are required',
    });
    return;
  }

  const controlledSubstance = CONTROLLED_SUBSTANCES.has(drugName.toLowerCase());
  const logLevel = controlledSubstance ? 'WARN' : 'INFO';
  const logMessage = controlledSubstance
    ? `Controlled substance prescribed: ${drugName} (${dosage}) for patient ${patientId}`
    : `Prescription created: ${drugName} (${dosage}) for patient ${patientId}`;

  writeStructuredLog(logLevel, 'com.hospital.his.controller.PatientController', logMessage, {
    ...mdc,
    event_type: 'PRESCRIPTION_ORDER',
    resource_type: 'PRESCRIPTION',
    patient_id: patientId,
    drug_name: drugName,
    dosage,
  });

  const durationMs = Number((process.hrtime.bigint() - startNs) / 1_000_000n);
  writeStructuredLog(
    'INFO',
    'HIPAA_AUDIT',
    `Clinical action completed: event=PRESCRIPTION_ORDER resource=PRESCRIPTION durationMs=${durationMs}`,
    {
      ...mdc,
      event_type: 'PRESCRIPTION_ORDER',
      resource_type: 'PRESCRIPTION',
      patient_id: patientId,
      drug_name: drugName,
      dosage,
      outcome: 'SUCCESS',
      duration_ms: String(durationMs),
    }
  );

  res.json({
    prescriptionId: `RX-${crypto.randomUUID().slice(0, 8).toUpperCase()}`,
    status: 'PRESCRIBED',
    patientId,
    drugName,
    dosage,
    controlledSubstance,
    orderedByDoctorId: mdc.doctor_id,
    department: mdc.department,
    orderedAt: new Date().toISOString(),
  });
});

app.post('/api/v1/interoperability/hl7', (req: MdcRequest, res: Response) => {
  const startNs = process.hrtime.bigint();
  const mdc = req.mdc!;
  const rawMessage = String(req.body?.rawMessage || '').trim();

  if (!rawMessage) {
    res.status(400).json({
      error: 'BAD_REQUEST',
      message: 'rawMessage is required',
    });
    return;
  }

  const { messageType, patientId } = parseHl7Summary(rawMessage);
  writeStructuredLog(
    'INFO',
    'com.hospital.his.controller.InteroperabilityController',
    `HL7 v2 message ingested: type=${messageType} patientId=${patientId}`,
    {
      ...mdc,
      event_type: 'HL7_MESSAGE_PROCESSED',
      resource_type: 'HL7_V2',
      hl7_raw: rawMessage,
      hl7_message_type: messageType,
      patient_id: patientId,
    }
  );

  const durationMs = Number((process.hrtime.bigint() - startNs) / 1_000_000n);
  writeStructuredLog(
    'INFO',
    'HIPAA_AUDIT',
    `Clinical action completed: event=HL7_MESSAGE_PROCESSED resource=HL7_V2 durationMs=${durationMs}`,
    {
      ...mdc,
      event_type: 'HL7_MESSAGE_PROCESSED',
      resource_type: 'HL7_V2',
      patient_id: patientId,
      outcome: 'SUCCESS',
      duration_ms: String(durationMs),
    }
  );

  res.json({
    status: 'ACK',
    protocol: 'HL7v2',
    messageType,
    patientId,
    processedAt: new Date().toISOString(),
  });
});

app.post('/api/v1/interoperability/fhir', (req: MdcRequest, res: Response) => {
  const startNs = process.hrtime.bigint();
  const mdc = req.mdc!;
  const resourceType = String(req.body?.resourceType || '').trim();
  const resourceId = String(req.body?.id || '').trim();
  const patientId = String(req.body?.patientId || 'UNKNOWN').trim() || 'UNKNOWN';
  const loincCode = req.body?.loincCode ? String(req.body.loincCode).trim() : undefined;

  if (!resourceType || !resourceId) {
    res.status(400).json({
      error: 'BAD_REQUEST',
      message: 'resourceType and id are required',
    });
    return;
  }

  writeStructuredLog(
    'INFO',
    'com.hospital.his.controller.InteroperabilityController',
    `FHIR R4 resource ingested: ${resourceType}/${resourceId} for patient ${patientId}`,
    {
      ...mdc,
      event_type: 'FHIR_RESOURCE_INGESTED',
      resource_type: 'FHIR_R4',
      fhir_resource_type: resourceType,
      fhir_resource_id: resourceId,
      ...(loincCode ? { fhir_loinc_code: loincCode } : {}),
      patient_id: patientId,
    }
  );

  const durationMs = Number((process.hrtime.bigint() - startNs) / 1_000_000n);
  writeStructuredLog(
    'INFO',
    'HIPAA_AUDIT',
    `Clinical action completed: event=FHIR_RESOURCE_INGESTED resource=FHIR_R4 durationMs=${durationMs}`,
    {
      ...mdc,
      event_type: 'FHIR_RESOURCE_INGESTED',
      resource_type: 'FHIR_R4',
      patient_id: patientId,
      outcome: 'SUCCESS',
      duration_ms: String(durationMs),
    }
  );

  res.json({
    status: 'INGESTED',
    protocol: 'FHIR_R4',
    resourceType,
    resourceId,
    patientId,
    processedAt: new Date().toISOString(),
  });
});

app.get('/api/v1/audit/logs', (_req: Request, res: Response) => {
  if (!fs.existsSync(LOG_FILE)) {
    res.json({ count: 0, logs: [] });
    return;
  }
  const lines = fs
    .readFileSync(LOG_FILE, 'utf8')
    .split('\n')
    .filter((line) => line.trim().length > 0)
    .slice(-50)
    .map((line) => JSON.parse(line) as Record<string, unknown>);
  res.json({ count: lines.length, logs: lines });
});

app.listen(PORT, '0.0.0.0', () => {
  writeStructuredLog(
    'INFO',
    'com.hospital.his.HisApplication',
    `HIS Backend API listening on port ${PORT}`,
    { event_type: 'APP_STARTUP' }
  );
});
