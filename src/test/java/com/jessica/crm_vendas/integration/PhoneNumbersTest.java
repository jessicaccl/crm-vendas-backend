package com.jessica.crm_vendas.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhoneNumbersTest {

    @Test
    void normalizesBrazilianNumbers() {
        assertThat(PhoneNumbers.digits("(21) 99603-2769")).isEqualTo("21996032769");
        assertThat(PhoneNumbers.digits("+55 21 99603-2769")).isEqualTo("21996032769");
        assertThat(PhoneNumbers.digits("  ")).isNull();
        assertThat(PhoneNumbers.digits(null)).isNull();
    }

    @Test
    void formatsMobileAndLandline() {
        assertThat(PhoneNumbers.format("21996032769")).isEqualTo("(21) 99603-2769");
        assertThat(PhoneNumbers.format("2133334444")).isEqualTo("(21) 3333-4444");
        assertThat(PhoneNumbers.format("12345")).isEqualTo("12345");
    }
}
