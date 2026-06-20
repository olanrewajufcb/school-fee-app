package com.fee.app.schoolfeeapp.auth.config;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;

@Component
public class SchoolIdContextFilter implements WebFilter {

    public static final String SCHOOL_ID_KEY = "CURRENT_SCHOOL_ID";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String schoolIdHeader = exchange.getRequest().getHeaders().getFirst("X-School-ID");
        
        if (schoolIdHeader != null && !schoolIdHeader.isBlank()) {
            try {
                UUID schoolId = UUID.fromString(schoolIdHeader);
                return chain.filter(exchange).contextWrite(Context.of(SCHOOL_ID_KEY, schoolId));
            } catch (IllegalArgumentException e) {
                // Ignore invalid UUIDs, let downstream logic handle missing schoolId
                return chain.filter(exchange);
            }
        }
        
        return chain.filter(exchange);
    }
}
