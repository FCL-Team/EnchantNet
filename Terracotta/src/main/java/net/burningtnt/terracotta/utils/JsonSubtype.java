package net.burningtnt.terracotta.utils;

public @interface JsonSubtype {
    Class<?> clazz();

    String name();
}
