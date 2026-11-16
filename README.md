# Hospital Information System (HIS) + ELK Stack + Filebeat (100% Backend)

Complete backend demo integrating a **Java Spring Boot 3** Hospital Information System with **Elasticsearch, Logstash, Kibana (ELK 8.13.0)** and **Filebeat** (no Spring Security, direct header-based MDC context):

1. **Header MDC Filter (`MdcHeaderFilter`)**: Extracts `X-Doctor-Id` (default `DR-UNKNOWN`), `X-Department` (default `GENERAL`), and `X-Trace-Id` into SLF4J `MDC` and clears it in `finally`.
2. **Declarative AOP HIPAA Audit Trail (`@AuditableAction` + `AuditLogAspect`)**: Automatically records `event_type`, `resource_type`, `patient_id`, `outcome`, and `duration_ms` on every clinical endpoint.
3. **HL7 v2 & FHIR R4 Parsing in Logstash (`logstash/pipeline/logstash.conf`)**: Parses pipe-delimited HL7 v2 segments (`MSH`, `PID`, `PV1`, `ORC`, `RXO`) into `hl7.*` fields and enriches FHIR R4 resources (`fhir.*`) while tagging `hipaa_audit`, `clinical_event`, and `controlled_substance_alert`.
4. **Live Node/Express Runtime Mirror (`server.ts`)**: Runs on port `3000` in this container environment (0% frontend, pure JSON API) and writes identical Logstash NDJSON logs to `logs/his-application.json`.

---

## Running ELK + Spring Boot Locally

```bash
# 1. Start Elasticsearch, Logstash, Kibana, and Filebeat
mkdir -p logs
docker-compose up -d

# 2. Run Java Spring Boot (port 8080)
mvn spring-boot:run
```

Or run the Node/Express runtime mirror on port `3000`:

```bash
npm run dev
```

---

## Testing Clinical & Interoperability Endpoints

```bash
# 1. Doctor views a patient's EMR
curl -X GET http://localhost:8080/api/v1/patients/PAT-1001/emr \
  -H "X-Doctor-Id: DR-402" \
  -H "X-Department: CARDIOLOGY"

# 2. Controlled substance prescription (triggers WARN + controlled_substance_alert in Logstash)
curl -X POST http://localhost:8080/api/v1/patients/PAT-1001/prescriptions \
  -H "Content-Type: application/json" \
  -H "X-Doctor-Id: DR-402" \
  -H "X-Department: CARDIOLOGY" \
  -d '{"drugName": "Morphine", "dosage": "5mg IV"}'

# 3. HL7 v2 ADT/ORM message ingestion (parsed into hl7.* fields by Logstash Ruby filter)
curl -X POST http://localhost:8080/api/v1/interoperability/hl7 \
  -H "Content-Type: application/json" \
  -H "X-Doctor-Id: DR-108" \
  -H "X-Department: EMERGENCY" \
  -d '{"rawMessage": "MSH|^~\\&|HIS|HOSP|ELK|AUDIT|202611152300||ADT^A01|MSG001|P|2.5\nPID|1||PAT-1001||NGUYEN^VAN^A||19840514|M\nPV1|1|I|CARDIOLOGY^101"}'

# 4. FHIR R4 Observation ingestion (enriched into fhir.* fields by Logstash)
curl -X POST http://localhost:8080/api/v1/interoperability/fhir \
  -H "Content-Type: application/json" \
  -H "X-Doctor-Id: DR-108" \
  -H "X-Department: EMERGENCY" \
  -d '{"resourceType": "Observation", "id": "OBS-991", "patientId": "PAT-1001", "loincCode": "8867-4"}'
```

---

## Kibana Data View Setup (`http://localhost:5601`)

1. Open **Management -> Stack Management -> Data Views**.
2. Create a Data View with index pattern `his-clinical-logs-*` and timestamp field `@timestamp`.
3. Filter in **Analytics -> Discover** using `tags: "hipaa_audit"`, `tags: "controlled_substance_alert"`, `hl7.message_type: "ADT^A01"`, or `department: "CARDIOLOGY"`.
