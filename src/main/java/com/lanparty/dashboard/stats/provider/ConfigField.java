package com.lanparty.dashboard.stats.provider;

/**
 * Describes one config input so the admin UI can render a form for any provider.
 *
 * @param kind text, url, number or password (passwords are masked when read back)
 */
public record ConfigField(String name, String label, String kind, boolean required, String placeholder) {

    public static ConfigField text(String name, String label, boolean required, String placeholder) {
        return new ConfigField(name, label, "text", required, placeholder);
    }

    public static ConfigField url(String name, String label, String placeholder) {
        return new ConfigField(name, label, "url", true, placeholder);
    }

    public static ConfigField number(String name, String label, boolean required, String placeholder) {
        return new ConfigField(name, label, "number", required, placeholder);
    }

    public static ConfigField password(String name, String label) {
        return new ConfigField(name, label, "password", false, "");
    }
}
