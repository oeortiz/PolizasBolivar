package com.bolivar.polizas.config;

import com.bolivar.polizas.domain.CalculadoraPrima;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Registra los componentes de dominio como beans sin acoplar el paquete
 * {@code domain} a Spring: el dominio sigue siendo Java puro y testeable sin contexto.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PolizasProperties.class)
public class DominioConfig {

    @Bean
    public CalculadoraPrima calculadoraPrima() {
        return new CalculadoraPrima();
    }

    /** Reloj inyectable en la zona de negocio; las pruebas lo reemplazan por uno fijo. */
    @Bean
    public Clock clock(PolizasProperties properties) {
        return Clock.system(properties.zonaHoraria());
    }
}
