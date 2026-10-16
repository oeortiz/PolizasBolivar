package com.bolivar.polizas.config;

import com.bolivar.polizas.observability.MetricasIntegracion;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ObservabilidadConfig {

    @Bean
    public MetricasIntegracion metricasIntegracion(MeterRegistry registry) {
        return new MetricasIntegracion(registry);
    }
}
