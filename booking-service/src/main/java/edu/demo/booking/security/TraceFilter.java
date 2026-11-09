package edu.demo.booking.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TraceFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
        FilterChain chain) throws ServletException, IOException {
        String trace = request.getHeader("X-Trace-Id");
        if (trace == null || trace.isBlank() || trace.length() > 100) trace = UUID.randomUUID().toString();
        MDC.put("traceId", trace);
        response.setHeader("X-Trace-Id", trace);
        try { chain.doFilter(request, response); } finally { MDC.remove("traceId"); }
    }
}
