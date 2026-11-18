package com.encryptiron.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.encryptiron.ValianceConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

@Slf4j
@Singleton
public class ValianceSocket
{
    private static final int PORT = 8080;
    private static final String PATH = "/api/plugin/ws";
    private static final int NORMAL_CLOSURE = 1000;
    private static final int FIRST_NEVER_RETRY_CLOSE_CODE = 4000;
    private static final int LAST_NEVER_RETRY_CLOSE_CODE = 4099;
    private static final int MAX_QUEUED_MESSAGES = 50;
    private static final long PING_INTERVAL_SECONDS = 30;
    private static final int MAX_BACKOFF_EXPONENT = 6;
    private static final long MAX_RECONNECT_JITTER_MS = 5000;

    @Inject
    private OkHttpClient httpClient;

    @Inject
    private Gson gson;

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ValianceConfig config;

    private final Map<String, Consumer<JsonObject>> handlers = new ConcurrentHashMap<>();

    private OkHttpClient socketClient;
    private WebSocket webSocket;
    private boolean running;
    private boolean connected;
    private JsonObject handshake;
    private final Deque<String> pendingMessages = new ArrayDeque<>();
    private int failedAttempts;
    private ScheduledFuture<?> pendingReconnect;

    public void setHandler(String type, Consumer<JsonObject> handler)
    {
        handlers.put(type, handler);
    }

    public synchronized boolean isRunning()
    {
        return running;
    }

    public synchronized void start(JsonObject handshakeMessage)
    {
        if (running)
        {
            return;
        }

        running = true;
        handshake = handshakeMessage;
        failedAttempts = 0;
        connect();
    }

    public synchronized void stop()
    {
        running = false;
        handshake = null;
        pendingMessages.clear();
        connected = false;

        if (pendingReconnect != null)
        {
            pendingReconnect.cancel(false);
            pendingReconnect = null;
        }

        if (webSocket != null)
        {
            webSocket.close(NORMAL_CLOSURE, null);
            webSocket = null;
        }
    }

    public synchronized void send(JsonObject message)
    {
        if (!running)
        {
            return;
        }

        String text = gson.toJson(message);
        if (connected && webSocket.send(text))
        {
            return;
        }

        if (pendingMessages.size() >= MAX_QUEUED_MESSAGES)
        {
            pendingMessages.removeFirst();
        }
        pendingMessages.addLast(text);
    }

    private void connect()
    {
        if (socketClient == null)
        {
            socketClient = httpClient.newBuilder()
                .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
                .build();
        }

        Request request = new Request.Builder()
            .url("ws://" + config.valianceServerUrl() + ":" + PORT + PATH)
            .header("User-Agent", "ValiancePlugin")
            .build();

        webSocket = socketClient.newWebSocket(request, new Listener());
    }

    private synchronized void onOpened(WebSocket socket)
    {
        if (socket != webSocket)
        {
            return;
        }

        connected = true;
        failedAttempts = 0;
        socket.send(gson.toJson(handshake));

        while (!pendingMessages.isEmpty())
        {
            if (!socket.send(pendingMessages.peekFirst()))
            {
                break;
            }
            pendingMessages.removeFirst();
        }
    }

    private void onReceived(WebSocket socket, String text)
    {
        JsonObject message;
        synchronized (this)
        {
            if (socket != webSocket)
            {
                return;
            }

            try
            {
                message = gson.fromJson(text, JsonObject.class);
            }
            catch (JsonParseException ex)
            {
                log.debug("Ignoring unreadable message from the Valiance server", ex);
                return;
            }

            if (message == null)
            {
                return;
            }

            if ("welcome".equals(getString(message, "type")) && running)
            {
                handshake.remove("history");
            }
        }

        Consumer<JsonObject> handler = handlers.get(getString(message, "type"));
        if (handler != null)
        {
            clientThread.invokeLater(() -> handler.accept(message));
        }
    }

    private synchronized void onDisconnected(WebSocket socket, int code)
    {
        if (socket != webSocket)
        {
            return;
        }

        webSocket = null;
        connected = false;

        if (!running)
        {
            return;
        }

        if (code >= FIRST_NEVER_RETRY_CLOSE_CODE && code <= LAST_NEVER_RETRY_CLOSE_CODE)
        {
            log.warn("The Valiance server refused the connection ({})", code);
            running = false;
            handshake = null;
            pendingMessages.clear();
            return;
        }

        long delayMs = TimeUnit.SECONDS.toMillis(1L << Math.min(failedAttempts, MAX_BACKOFF_EXPONENT))
            + ThreadLocalRandom.current().nextLong(MAX_RECONNECT_JITTER_MS + 1);
        failedAttempts++;
        pendingReconnect = executor.schedule(this::reconnect, delayMs, TimeUnit.MILLISECONDS);
    }

    private synchronized void reconnect()
    {
        pendingReconnect = null;
        if (running && webSocket == null)
        {
            connect();
        }
    }

    static String getString(JsonObject json, String key)
    {
        JsonElement element = json.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
    }

    private class Listener extends WebSocketListener
    {
        @Override
        public void onOpen(WebSocket socket, Response response)
        {
            onOpened(socket);
        }

        @Override
        public void onMessage(WebSocket socket, String text)
        {
            onReceived(socket, text);
        }

        @Override
        public void onClosing(WebSocket socket, int code, String reason)
        {
            socket.close(NORMAL_CLOSURE, null);
            onDisconnected(socket, code);
        }

        @Override
        public void onClosed(WebSocket socket, int code, String reason)
        {
            onDisconnected(socket, code);
        }

        @Override
        public void onFailure(WebSocket socket, Throwable t, Response response)
        {
            log.debug("Lost the connection to the Valiance server", t);
            onDisconnected(socket, -1);
        }
    }
}
