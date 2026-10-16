package com.bolivar.polizas.config;

import com.bolivar.polizas.integration.port.CoreMutationPort;
import com.bolivar.polizas.integration.simulated.SimuladoCoreAdapter;
import com.bolivar.polizas.integration.weblogic.OAuth2ClientCredentialsTokenProvider;
import com.bolivar.polizas.integration.weblogic.SslContextFactory;
import com.bolivar.polizas.integration.weblogic.WebLogicCoreAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Selecciona el adaptador del CORE según {@code polizas.integracion.adaptador}.
 *
 * <ul>
 *   <li>{@code SIMULADO} (por defecto): no requiere credenciales; nada llega al CORE real y se
 *       advierte en el log al arrancar.</li>
 *   <li>{@code WEBLOGIC}: exige URL HTTPS, token endpoint HTTPS, credenciales OAuth y
 *       keystore/truststore para mTLS. Si falta algo, la aplicación no arranca (fail-fast) y el
 *       error enumera las propiedades faltantes, nunca sus valores.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class IntegracionConfig {

    private static final Logger log = LoggerFactory.getLogger(IntegracionConfig.class);

    @Bean
    public CoreMutationPort coreMutationPort(PolizasProperties properties, ObjectMapper objectMapper, Clock clock) {
        PolizasProperties.Integracion integracion = properties.integracion();
        return switch (integracion.adaptador()) {
            case SIMULADO -> {
                log.warn("Integración con el CORE en modo SIMULADO ({}): ninguna mutación se envía al CORE real.",
                        integracion.simulado().modo());
                yield new SimuladoCoreAdapter(integracion.simulado());
            }
            case WEBLOGIC -> crearWebLogic(integracion.weblogic(), objectMapper, clock);
        };
    }

    static WebLogicCoreAdapter crearWebLogic(PolizasProperties.WebLogic cfg, ObjectMapper objectMapper, Clock clock) {
        validar(cfg);
        SSLContext ssl = SslContextFactory.crear(
                cfg.keyStorePath(), cfg.keyStorePassword().toCharArray(), cfg.keyStoreType(),
                cfg.trustStorePath(), cfg.trustStorePassword().toCharArray(), cfg.trustStoreType());
        HttpClient httpClient = HttpClient.newBuilder()
                .sslContext(ssl)
                .sslParameters(SslContextFactory.parametros(cfg.protocolosTls()))
                .connectTimeout(cfg.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        OAuth2ClientCredentialsTokenProvider tokens = new OAuth2ClientCredentialsTokenProvider(
                httpClient, cfg.tokenUrl(), cfg.clientId(), cfg.clientSecret(), cfg.scope(), cfg.readTimeout(),
                objectMapper, clock);
        log.info("Integración con el CORE vía WebLogic: {}", cfg);
        return new WebLogicCoreAdapter(httpClient, cfg.mutacionesUrl(), cfg.readTimeout(), tokens, objectMapper);
    }

    static void validar(PolizasProperties.WebLogic cfg) {
        List<String> faltantes = new ArrayList<>();
        if (cfg == null) {
            throw new IllegalStateException("Falta la configuración polizas.integracion.weblogic");
        }
        exigirHttps(cfg.mutacionesUrl(), "mutaciones-url", faltantes);
        exigirHttps(cfg.tokenUrl(), "token-url", faltantes);
        exigir(cfg.clientId(), "client-id", faltantes);
        exigir(cfg.clientSecret(), "client-secret", faltantes);
        exigir(cfg.keyStorePath(), "key-store-path", faltantes);
        exigir(cfg.keyStorePassword(), "key-store-password", faltantes);
        exigir(cfg.trustStorePath(), "trust-store-path", faltantes);
        exigir(cfg.trustStorePassword(), "trust-store-password", faltantes);
        if (cfg.connectTimeout() == null || cfg.readTimeout() == null
                || cfg.connectTimeout().isNegative() || cfg.connectTimeout().isZero()
                || cfg.readTimeout().isNegative() || cfg.readTimeout().isZero()) {
            faltantes.add("connect-timeout/read-timeout (> 0)");
        }
        if (!faltantes.isEmpty()) {
            throw new IllegalStateException("Adaptador WEBLOGIC sin configuración completa. Revise "
                    + "polizas.integracion.weblogic: " + String.join(", ", faltantes));
        }
    }

    private static void exigir(String valor, String nombre, List<String> faltantes) {
        if (valor == null || valor.isBlank()) {
            faltantes.add(nombre);
        }
    }

    private static void exigirHttps(URI uri, String nombre, List<String> faltantes) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            faltantes.add(nombre + " (https://...)");
        }
    }
}
