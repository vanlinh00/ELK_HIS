package com.hospital.his.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditLogAspect {

    private static final Logger auditLog = LoggerFactory.getLogger("HIPAA_AUDIT");

    @Around("@annotation(auditableAction)")
    public Object auditClinicalAction(ProceedingJoinPoint joinPoint, AuditableAction auditableAction) throws Throwable {
        long startNs = System.nanoTime();
        String previousEventType = MDC.get("event_type");
        String previousResource = MDC.get("resource_type");

        MDC.put("event_type", auditableAction.eventType());
        MDC.put("resource_type", auditableAction.resource());

        extractPatientIdIfPresent(joinPoint);

        try {
            Object result = joinPoint.proceed();
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            MDC.put("outcome", "SUCCESS");
            MDC.put("duration_ms", String.valueOf(durationMs));
            auditLog.info("Clinical action completed: event={} resource={} method={} durationMs={}",
                    auditableAction.eventType(),
                    auditableAction.resource(),
                    joinPoint.getSignature().toShortString(),
                    durationMs);
            return result;
        } catch (Throwable ex) {
            long durationMs = (System.nanoTime() - startNs) / 1_000_000;
            MDC.put("outcome", "ERROR");
            MDC.put("duration_ms", String.valueOf(durationMs));
            auditLog.error("Clinical action failed: event={} resource={} method={} error={}",
                    auditableAction.eventType(),
                    auditableAction.resource(),
                    joinPoint.getSignature().toShortString(),
                    ex.getMessage());
            throw ex;
        } finally {
            MDC.remove("outcome");
            MDC.remove("duration_ms");
            restoreOrRemove("event_type", previousEventType);
            restoreOrRemove("resource_type", previousResource);
        }
    }

    private void extractPatientIdIfPresent(ProceedingJoinPoint joinPoint) {
        if (MDC.get("patient_id") != null) {
            return;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();
        if (paramNames == null || args == null) {
            return;
        }
        for (int i = 0; i < paramNames.length; i++) {
            if ("patientId".equals(paramNames[i]) && args[i] != null) {
                MDC.put("patient_id", args[i].toString());
                break;
            }
        }
    }

    private void restoreOrRemove(String key, String previousValue) {
        if (previousValue == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, previousValue);
        }
    }
}
