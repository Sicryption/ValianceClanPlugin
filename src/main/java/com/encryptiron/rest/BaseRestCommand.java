package com.encryptiron.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public abstract class BaseRestCommand {

    final String DEVELOPMENT_URL = "http://127.0.0.1:5000";
    final String PRODUCTION_URL = "http://valianceosrs.com";

    public void send() throws IOException
    {
        URL url = new URL(DEVELOPMENT_URL + endpoint());
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    
        connection.setRequestMethod(requestType());
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
    
        String headerBody = MessageHeaderData.getMessageHeaderJson();
        String requestBody = body();

        String body = "{ " + headerBody + ", " + requestBody + " }";
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = body.getBytes("utf-8");
            os.write(input, 0, input.length);
        }
    
        try (BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8")))
        {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            System.out.println("Successful " + requestType() + ", response: " + response.toString());
        }
    }
    
    abstract String requestType();
    abstract String endpoint();
    abstract String body();
}
