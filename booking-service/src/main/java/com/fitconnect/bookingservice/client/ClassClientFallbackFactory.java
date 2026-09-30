package com.fitconnect.bookingservice.client;

import com.fitconnect.bookingservice.client.dto.ClassResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback du circuit breaker pour class-service. Le cours est critique pour la saga :
 * impossible de reserver sans lui, on echoue donc explicitement (503) plutot que de
 * renvoyer des donnees fictives.
 */
@Component
@Slf4j
public class ClassClientFallbackFactory implements FallbackFactory<ClassClient> {

    @Override
    public ClassClient create(Throwable cause) {
        log.warn("Fallback class-service declenche : {}", cause.toString());
        return new ClassClient() {
            @Override
            public ClassResponse getClassById(Long id) {
                throw FallbackSupport.translate(cause, "class-service");
            }

            @Override
            public ClassResponse incrementParticipants(Long id, int spots) {
                throw FallbackSupport.translate(cause, "class-service");
            }

            @Override
            public ClassResponse decrementParticipants(Long id, int spots) {
                throw FallbackSupport.translate(cause, "class-service");
            }
        };
    }
}
