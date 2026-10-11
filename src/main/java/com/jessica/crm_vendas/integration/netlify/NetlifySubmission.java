package com.jessica.crm_vendas.integration.netlify;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Campos que interessam de um envio de formulário do Netlify.
 * Os campos do formulário ficam em "data"; nomes em português (do quiz da Unic)
 * e em inglês (padrão do Netlify) são aceitos.
 */
public record NetlifySubmission(
    String id,
    String formName,
    String name,
    String phone,
    String email,
    String company,
    String result,
    String score
) {
    public static NetlifySubmission from(JsonNode root) {
        JsonNode data = root.path("data");
        return new NetlifySubmission(
            text(root, "id"),
            text(root, "form_name"),
            first(text(data, "nome"), text(data, "name"), text(root, "name")),
            first(text(data, "telefone"), text(data, "phone"), text(data, "whatsapp")),
            first(text(data, "email"), text(root, "email")),
            first(text(data, "empresa"), text(data, "company"), text(root, "company")),
            text(data, "resultado"),
            text(data, "placar")
        );
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) return null;
        String text = value.asText().trim();
        return text.isEmpty() ? null : text;
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null) return value;
        }
        return null;
    }
}
