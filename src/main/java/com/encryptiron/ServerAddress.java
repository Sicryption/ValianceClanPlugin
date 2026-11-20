package com.encryptiron;

public final class ServerAddress
{
    private static final String SECURE_SCHEME = "https://";
    private static final String SCHEME_SEPARATOR = "://";

    private ServerAddress()
    {
    }

    public static String httpUrl(String configuredServer, String path)
    {
        return baseUrl(configuredServer) + path;
    }

    public static String webSocketUrl(String configuredServer, String path)
    {
        return baseUrl(configuredServer).replaceFirst("^http", "ws") + path;
    }

    private static String baseUrl(String configuredServer)
    {
        String server = configuredServer.trim().replaceAll("/+$", "");
        return server.contains(SCHEME_SEPARATOR) ? server : SECURE_SCHEME + server;
    }
}
