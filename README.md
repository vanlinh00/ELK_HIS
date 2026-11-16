
I)

Step1: Java App: Writes JSON logs (with patient_id, doctor_id from MDC) to ./logs/his-application.json.

Step2: Filebeat: Reads new lines from the mounted ./logs folder and sends them to logstash:5044.

Step3: Logstash: Adds the hipaa_audit tag to PATIENT_ACCESS events and forwards them to elasticsearch:9200.

Step4: Elasticsearch & Kibana: Stores and indexes the JSON so you can search it at http://localhost:5601.

II) Enterprise | Hospital Production

Java App ──▶ ./logs/his-application.json ──▶ Filebeat ──▶ Logstash ──▶ Elasticsearch ──▶ Kibana

How Data Flows Through This Project

1. Java App (Generates Logs)

   - MdcHeaderFilter captures request headers (X-Doctor-Id, X-Department, X-Trace-Id) into SLF4J MDC.

   - PatientController adds event_type: "PATIENT_ACCESS" and patient_id to MDC.

   - logback-spring.xml prints plain text to the console for developers and writes structured JSON lines to ./logs/his-application.json for Filebeat.

2. Filebeat (Ships Logs)

   - Reads ./logs/his-application.json via a mounted Docker volume (/var/log/his).

   - Parses each JSON line and forwards it to logstash:5044.

3. Logstash (Enriches & Routes)

   - Receives logs on port 5044.

   - Adds a hipaa_audit tag whenever event_type == "PATIENT_ACCESS".

   - Sends the events to elasticsearch:9200 under daily indices (his-clinical-logs-YYYY.MM.DD).

4. Elasticsearch & Kibana (Stores & Visualizes)

   - Elasticsearch (:9200) indexes every JSON field for fast searching.

   - Kibana (:5601) connects to Elasticsearch so you can search and filter his-clinical-logs-* in the browser.


III) Quickstart Guide

Prerequisites

- Docker & Docker Compose

- Java 17 + Maven

1. Start ELK Stack + Filebeat

   mkdir -p logs

   docker-compose up -d

   - Wait ~30-60s for services to start. Check status: docker-compose ps

2. Run the Java App

   mvn spring-boot:run

   - Starts on http://localhost:8080 and writes logs to ./logs/his-application.json

3. Send a Test Request

   curl -X GET http://localhost:8080/api/v1/patients/PAT-1001/emr \

     -H "X-Doctor-Id: DR-402" \

     -H "X-Department: CARDIOLOGY"

   - Or import HIS_ELK_Demo.postman_collection.json into Postman

4. View Logs in Kibana

   - Open http://localhost:5601

   - Go to Stack Management -> Data Views -> Create data view

   - Set Index pattern: his-clinical-logs-* and Timestamp field: @timestamp

   - Open Analytics -> Discover and filter by tags: "hipaa_audit" or patient_id: "PAT-1001"

5. Stop the Stack

   docker-compose down


IV) Screenshots Walkthrough

1. Docker Containers Running (Elasticsearch, Logstash, Kibana, Filebeat)

   ![Docker Containers](./images/Screenshot%202026-11-16%20163529.png)

2. Java App (`PatientController.java`) Logging with MDC

   ![PatientController MDC Logging](./images/Screenshot%202026-11-16%20163424.png)

3. Structured JSON Logs Written to `./logs/his-application.json`

   ![Structured JSON Logs](./images/Screenshot%202026-11-16%20163442.png)

4. Kibana Home (`http://localhost:5601`) -> Open **Stack Management**

   ![Kibana Home](./images/Screenshot%202026-11-16%20162755.png)

5. Stack Management -> **Data Views**

   ![Kibana Data Views](./images/Screenshot%202026-11-16%20162901.png)

6. Create Data View (`his-clinical-logs-*` matching `his-clinical-logs-2026.11.16`)

   ![Create Data View](./images/Screenshot%202026-11-16%20162936.png)

7. Open **Analytics -> Discover**

   ![Kibana Analytics](./images/Screenshot%202026-11-16%20163030.png)

8. Search Logs in **Discover** (`PAT-1001`, `doctor_id: DR-402`, `department: CARDIOLOGY`, `event_type: PATIENT_ACCESS`)

   ![Kibana Discover Results](./images/Screenshot%202026-11-16%20163505.png)

