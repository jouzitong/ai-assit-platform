package ai.platform.aiassit.gateway.core.filter;

import ai.platform.aiassit.gateway.core.context.GatewaySecurityAttributes;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 网关请求 traceId 统一提取与兜底。
 */
@Component
@Order(-200)
public class GatewayTraceFilter extends OncePerRequestFilter {

    private static final String TRACE_HEADER = "X-Trace-Id";

    private static final String MDC_TRACE_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        request.setAttribute(GatewaySecurityAttributes.TRACE_ID, traceId);
        response.setHeader(TRACE_HEADER, traceId);

        MDC.put(MDC_TRACE_KEY, traceId);
        try {
            filterChain.doFilter(new TraceIdRequestWrapper(request, traceId), response);
        } finally {
            MDC.remove(MDC_TRACE_KEY);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = firstNonBlank(
            request.getHeader(TRACE_HEADER),
            (String) request.getAttribute(GatewaySecurityAttributes.TRACE_ID)
        );
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        return traceId;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static final class TraceIdRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, List<String>> extraHeaders;

        private TraceIdRequestWrapper(HttpServletRequest request, String traceId) {
            super(request);
            this.extraHeaders = new LinkedHashMap<>();
            this.extraHeaders.put(TRACE_HEADER, List.of(traceId));
        }

        @Override
        public String getHeader(String name) {
            String value = firstValue(extraHeaders, name);
            if (value != null) {
                return value;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            List<String> values = new ArrayList<>();
            String extraValue = firstValue(extraHeaders, name);
            if (extraValue != null) {
                values.add(extraValue);
            }
            Enumeration<String> original = super.getHeaders(name);
            while (original.hasMoreElements()) {
                values.add(original.nextElement());
            }
            return Collections.enumeration(values);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                names.add(original.nextElement());
            }
            for (String name : extraHeaders.keySet()) {
                if (names.stream().noneMatch(existing -> existing.equalsIgnoreCase(name))) {
                    names.add(name);
                }
            }
            return Collections.enumeration(names);
        }

        private String firstValue(Map<String, List<String>> headers, String name) {
            if (!StringUtils.hasText(name)) {
                return null;
            }
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    return entry.getValue().get(0);
                }
            }
            return null;
        }
    }
}
