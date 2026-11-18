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

/**
 * The live connection to the Valiance server.
 *
 * Everything else the plugin sends is a request the server answers. This stays
 * open while the player is in the Valiance clan channel, so the server can send
 * things as they happen (chat messages, rank changes), and the plugin reports the
 * player's own clan chat messages up it.
 *
 * Every message either way is a JSON object with a "type". Handlers are
 * registered per type and run on the client thread; types without a handler
 * are ignored, so the server can add new ones without breaking this version.
 */
@Slf4j
@Singleton
public class ValianceSocket
{
    private static final int PORT = 8080;
    private static final String PATH = "/api/plugin/ws";
    private static final int NORMAL_CLOSURE = 1000;
    // The server closes with these when it will never accept this hello (a
    // malformed one, or a world it does not track), so retrying would only be
    // refused again.
    private static final int FIRST_REFUSAL_CODE = 4000;
    private static final int LAST_REFUSAL_CODE = 4099;
    // Chat said while the connection is down is sent once it is back, up to this
    // many messages; past that the oldest are dropped.
    private static final int MAX_QUEUED_MESSAGES = 50;
    private static final long PING_INTERVAL_SECONDS = 30;
    private static final int MAX_BACKOFF_EXPONENT = 6;
    // Added at random to every reconnect, so that when the server restarts its
    // plugins come back spread over a few seconds rather than all at once.
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

    // Everything below is guarded by this.
    private OkHttpClient socketClient;
    private WebSocket webSocket;
    private boolean open;
    // Sent on every (re)connect. Null while stopped.
    private JsonObject hello;
    private final Deque<String> queued = new ArrayDeque<>();
    private int failedAttempts;
    private ScheduledFuture<?> pendingReconnect;

    /** Runs handler on the client thread for every message of this type. */
    public void setHandler(String type, Consumer<JsonObject> handler)
    {
        handlers.put(type, handler);
    }

    /** Whether the connection is wanted, whether or not it is currently up. */
    public synchronized boolean isRunning()
    {
        return hello != null;
    }

    /**
     * Opens the connection and keeps it open, reconnecting as needed, until
     * {@link #stop()}. Does nothing if already running.
     *
     * Any "history" in the hello is asked for only until a welcome arrives, so a
     * reconnect later in the session does not replay it.
     */
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
        queued.clear();
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

    /** Sends a message now, or once reconnected. Dropped while stopped. */
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

        if (queued.size() >= MAX_QUEUED_MESSAGES)
        {
            queued.removeFirst();
        }
        queued.addLast(text);
    }

    private void connect()
    {
        if (socketClient == null)
        {
            // Pings let a connection that died without closing (lost network)
            // be noticed and replaced rather than sitting open forever.
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

        while (!queued.isEmpty())
        {
            if (!socket.send(queued.peekFirst()))
            {
                break;
            }
            queued.removeFirst();
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

        if (code >= FIRST_REFUSAL_CODE && code <= LAST_REFUSAL_CODE)
        {
            log.warn("The Valiance server refused the connection ({})", code);
            hello = null;
            queued.clear();
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
            // Handled here rather than in onClosed so the server's own close
            // code is what decides whether to reconnect.
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
