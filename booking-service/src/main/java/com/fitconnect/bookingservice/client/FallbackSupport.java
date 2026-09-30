package com.fitconnect.bookingservice.client;

import com.fitconnect.bookingservice.exception.ServiceUnavailableException;
import feign.FeignException;

/**
 * Regle commune aux fallbacks Feign :
 * <ul>
 *   <li>une reponse 4xx du service distant est une erreur metier (404 cours introuvable,
 *       409 plus de places...) : on la laisse remonter telle quelle pour que le service
 *       appelant la traduise ;</li>
 *   <li>tout le reste (5xx, timeout, connexion refusee, circuit ouvert) devient une
 *       ServiceUnavailableException -> 503 pour le client final.</li>
 * </ul>
 */
final class FallbackSupport {

    private FallbackSupport() {
    }

    static RuntimeException translate(Throwable cause, String serviceName) {
        if (cause instanceof FeignException.FeignClientException clientError) {
            return clientError;
        }
        String detail = cause == null ? "cause inconnue" : cause.getClass().getSimpleName();
        return new ServiceUnavailableException(
                "Le service " + serviceName + " est indisponible, veuillez reessayer plus tard (" + detail + ")",
                cause);
    }
}
