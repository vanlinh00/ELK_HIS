package com.hospital.his.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Extracts clinical request headers (X-Doctor-Id, X-Department, X-Trace-Id) into SLF4J MDC
 * so every downstream log line carries structured fields for Filebeat/Logstash/Kibana.
 */
@Component
public class MdcHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            String traceId = request.getHeader("X-Trace-Id");
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }
            String doctorId = request.getHeader("X-Doctor-Id");
            if (doctorId == null || doctorId.isBlank()) {
                doctorId = "DR-UNKNOWN";
            }
            String department = request.getHeader("X-Department");
            if (department == null || department.isBlank()) {
                department = "GENERAL";
            }

            MDC.put("trace_id", traceId);
            MDC.put("doctor_id", doctorId);
            MDC.put("department", department);
            response.setHeader("X-Trace-Id", traceId);

            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
