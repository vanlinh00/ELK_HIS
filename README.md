
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