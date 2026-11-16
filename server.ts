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

interface MdcRequest extends Request {
  mdc?: {
    trace_id: string;
    doctor_id: string;
    department: string;
  };
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

const app = express();
app.use(mdcHeaderMiddleware);

app.get('/api/v1/patients/:patientId/emr', (req: MdcRequest, res: Response) => {
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
      patient_id: patientId,
    }
  );

  res.json({
    ...base,
    accessedByDoctorId: mdc.doctor_id,
    department: mdc.department,
  });
});

app.listen(PORT, '0.0.0.0', () => {
  writeStructuredLog(
    'INFO',
    'com.hospital.his.HisApplication',
    `HIS Backend API listening on port ${PORT}`,
    { event_type: 'APP_STARTUP' }
  );
});
