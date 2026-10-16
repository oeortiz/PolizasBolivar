package com.bolivar.polizas.integration.weblogic;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.List;
import java.util.Set;

/**
 * Construye el {@link SSLContext} para mTLS: el keystore contiene el certificado y la clave
 * privada del cliente; el truststore, las CA que firman el certificado de WebLogic.
 * Los archivos se leen de rutas externas al repositorio (montadas como secretos).
 */
public final class SslContextFactory {

    /** Solo TLS 1.2 o superior, como exige el contrato. */
    static final Set<String> PROTOCOLOS_PERMITIDOS = Set.of("TLSv1.2", "TLSv1.3");

    private SslContextFactory() {
    }

    public static SSLContext crear(String keyStorePath, char[] keyStorePassword, String keyStoreType,
                                   String trustStorePath, char[] trustStorePassword, String trustStoreType) {
        try {
            KeyStore keyStore = cargar(keyStorePath, keyStorePassword, keyStoreType);
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keyStorePassword);

            KeyStore trustStore = cargar(trustStorePath, trustStorePassword, trustStoreType);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext contexto = SSLContext.getInstance("TLS");
            contexto.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return contexto;
        } catch (IOException | GeneralSecurityException e) {
            // Sin rutas completas ni contraseñas en el mensaje.
            throw new IllegalStateException("No se pudo inicializar mTLS: " + e.getClass().getSimpleName()
                    + " - " + e.getMessage(), e);
        }
    }

    /** Parámetros TLS restringidos a los protocolos configurados (solo 1.2 y 1.3). */
    public static SSLParameters parametros(List<String> protocolos) {
        if (protocolos == null || protocolos.isEmpty()) {
            throw new IllegalStateException("Debe configurarse al menos un protocolo TLS");
        }
        for (String protocolo : protocolos) {
            if (!PROTOCOLOS_PERMITIDOS.contains(protocolo)) {
                throw new IllegalStateException("Protocolo TLS no permitido: " + protocolo + " (mínimo TLSv1.2)");
            }
        }
        SSLParameters parametros = new SSLParameters();
        parametros.setProtocols(protocolos.toArray(String[]::new));
        // Verificación del nombre del host del servidor.
        parametros.setEndpointIdentificationAlgorithm("HTTPS");
        return parametros;
    }

    private static KeyStore cargar(String ruta, char[] password, String tipo) throws IOException, GeneralSecurityException {
        KeyStore ks = KeyStore.getInstance(tipo);
        try (InputStream in = Files.newInputStream(Path.of(ruta))) {
            ks.load(in, password);
        }
        return ks;
    }
}
