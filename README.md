
Java App: Writes JSON logs (with patient_id, doctor_id from MDC) to ./logs/his-application.json.

Filebeat: Reads new lines from the mounted ./logs folder and sends them to logstash:5044.

Logstash: Adds the hipaa_audit tag to PATIENT_ACCESS events and forwards them to elasticsearch:9200.

Elasticsearch & Kibana: Stores and indexes the JSON so you can search it at http://localhost:5601.

