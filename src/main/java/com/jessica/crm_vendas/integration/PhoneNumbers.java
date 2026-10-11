package com.jessica.crm_vendas.integration;

/** Telefones brasileiros: compara pelos dígitos e exibe como (21) 99603-2769. */
final class PhoneNumbers {

    private PhoneNumbers() {}

    /** Só os dígitos, sem o 55 do país; nulo se não houver número. */
    static String digits(String phone) {
        if (phone == null) return null;
        String digits = phone.replaceAll("\\D", "");
        if ((digits.length() == 12 || digits.length() == 13) && digits.startsWith("55")) {
            digits = digits.substring(2);
        }
        return digits.isEmpty() ? null : digits;
    }

    static String format(String digits) {
        if (digits.length() == 11) {
            return "(%s) %s-%s".formatted(digits.substring(0, 2), digits.substring(2, 7), digits.substring(7));
        }
        if (digits.length() == 10) {
            return "(%s) %s-%s".formatted(digits.substring(0, 2), digits.substring(2, 6), digits.substring(6));
        }
        return digits;
    }
}
