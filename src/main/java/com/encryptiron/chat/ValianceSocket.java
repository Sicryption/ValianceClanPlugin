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
    private boolean open;
    private JsonObject hello;
    private final Deque<String> queuedWhileDisconnected = new ArrayDeque<>();
    private int failedAttempts;
    private ScheduledFuture<?> pendingReconnect;

    public void setHandler(String type, Consumer<JsonObject> handler)
    {
        handlers.put(type, handler);
    }

    public synchronized boolean isRunning()
    {
        return hello != null;
    }

    public synchronized void start(JsonObject helloMessage)
    {
        if (hello != null)
        {
            return;
        }

        hello = helloMessage;
        failedAttempts = 0;
        connect();
    }

    public synchronized void stop()
    {
        hello = null;
        queuedWhileDisconnected.clear();
        open = false;

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
        if (hello == null)
        {
            return;
        }

        String text = gson.toJson(message);
        if (open && webSocket.send(text))
        {
            return;
        }

        if (queuedWhileDisconnected.size() >= MAX_QUEUED_MESSAGES)
        {
            queuedWhileDisconnected.removeFirst();
        }
        queuedWhileDisconnected.addLast(text);
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

        open = true;
        failedAttempts = 0;
        socket.send(gson.toJson(hello));

        while (!queuedWhileDisconnected.isEmpty())
        {
            if (!socket.send(queuedWhileDisconnected.peekFirst()))
            {
                break;
            }
            queuedWhileDisconnected.removeFirst();
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

            if ("welcome".equals(getString(message, "type")) && hello != null)
            {
                hello.remove("history");
            }
        }

        Consumer<JsonObject> handler = handlers.get(getString(message, "type"));
        if (handler != null)
        {
            clientThread.invokeLater(() -> handler.accept(message));
        }
    }

    private synchronized void onLost(WebSocket socket, int code)
    {
        if (socket != webSocket)
        {
            return;
        }

        webSocket = null;
        open = false;

        if (hello == null)
        {
            return;
        }

        if (code >= FIRST_NEVER_RETRY_CLOSE_CODE && code <= LAST_NEVER_RETRY_CLOSE_CODE)
        {
            log.warn("The Valiance server refused the connection ({})", code);
            hello = null;
            queuedWhileDisconnected.clear();
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
        if (hello != null && webSocket == null)
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
            onLost(socket, code);
        }

        @Override
        public void onClosed(WebSocket socket, int code, String reason)
        {
            onLost(socket, code);
        }

        @Override
        public void onFailure(WebSocket socket, Throwable t, Response response)
        {
            log.debug("Lost the connection to the Valiance server", t);
            onLost(socket, -1);
        }
    }
}
