package com.jessica.crm_vendas.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio da aplicação. Fica num bean para que os testes possam fixar a data,
 * e usa o fuso do negócio (APP_TIMEZONE) para cálculos como "ganho no mês".
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock(@Value("${app.timezone}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
