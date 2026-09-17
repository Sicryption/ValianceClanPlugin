package com.encryptiron.screenshot;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.inject.Singleton;

import com.encryptiron.ValianceConfig;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.ui.DrawManager;

/**
 * A picture of the game world the moment a drop lands, held until we know
 * whether anyone wants it.
 *
 * Almost every drop is rejected by every event, so uploading on sight would mean
 * shipping a picture of someone's gameplay to our servers for nothing, thousands
 * of times a day. The frame is captured immediately - it has to be, the moment is
 * gone a tick later - and parked here under a key. If the accept response comes
 * back naming that drop, {@link #take} hands the bytes over; otherwise they
 * expire where they sit and never leave the machine.
 *
 * <h2>The scene is read, not altered</h2>
 *
 * The renderer draws the world and the interface in two separate steps, and the
 * world exists on its own in between. That intermediate image is what gets read,
 * so the screenshot has no interface in it without anything being hidden, and
 * the frame the player sees is never touched: no widget is hidden, no script is
 * run, no varc is written, no entity is dropped from the scene. Nothing is
 * cropped and nothing is painted over either - the whole world view is there,
 * including whatever the chatbox would have been sitting on top of.
 *
 * Where that intermediate image lives depends on the renderer, which is the only
 * reason there are two grabbers: {@link GpuSceneGrabber} reads it out of the
 * GPU plugin's scene framebuffer, {@link CpuSceneGrabber} reads it out of the
 * client's own pixel buffer. Both are reads at a moment the client already
 * provides.
 */
@Slf4j
@Singleton
public class DropScreenshot
{
    /** How long an unclaimed screenshot is kept before it is dropped. */
    private static final long PENDING_TTL_MS = 30_000;


    /**
     * How many screenshots may be waiting at once.
     *
     * A cap rather than a TTL alone because the TTL only helps if time passes.
     * A full-resolution PNG is around a megabyte, and nothing should be able to
     * grow the client's heap without bound just by killing things quickly.
     */
    private static final int MAX_PENDING = 8;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private DrawManager drawManager;

    @Inject
    private ScheduledExecutorService executor;

    @Inject
    private ValianceConfig config;

    @Inject
    private GpuSceneGrabber gpuGrabber;

    @Inject
    private CpuSceneGrabber cpuGrabber;

    /** Insertion-ordered so the oldest entry is the one evicted when full. */
    private final Map<String, Pending> pending = new LinkedHashMap<>();

    /** The last tick we captured on, so we capture at most once per tick. */
    private int lastCaptureTick = -1;


    /**
     * A screenshot that may not exist yet.
     *
     * A future rather than a byte array because the two things race. The frame
     * is encoded on a background thread - tens of milliseconds for a
     * full-resolution PNG - while the drop is already in flight to the server,
     * and a server on the same machine answers in less time than that. Reading a
     * plain field at that moment usually finds nothing, and the screenshot is
     * thrown away for a drop that was actually accepted.
     */
    private static final class Pending
    {
        private final CompletableFuture<byte[]> png = new CompletableFuture<>();
        private final long capturedAt = System.currentTimeMillis();
    }

    /**
     * Grab the game view now, against a key the response handler can look up.
     *
     * Silently does nothing if this tick already produced a screenshot. Several
     * NPCs dying on one tick is one moment as far as proof is concerned, and
     * each capture forces a pixel readback off the GPU.
     */
    public void capture(String captureKey)
    {
        if (!config.sendDropScreenshots() || captureKey == null)
        {
            return;
        }

        int tick = client.getTickCount();
        if (tick == lastCaptureTick)
        {
            return;
        }
        lastCaptureTick = tick;

        final Pending slot = new Pending();
        expirePending();
        if (pending.size() >= MAX_PENDING)
        {
            Iterator<String> oldest = pending.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        pending.put(captureKey, slot);

        SceneSource source = source();
        if (source == null)
        {
            // No renderer we know how to read the scene from. Better no
            // screenshot than one with the interface in it.
            pending.remove(captureKey);
            slot.png.complete(null);
            return;
        }

        source.arm();

        drawManager.requestNextFrameListener(frame ->
        {
            // Read on this thread, not the executor: we are inside the
            // renderer's own draw call, which is the only moment the scene is
            // still reachable - for the GPU path, the only moment the GL context
            // is current at all.
            BufferedImage scene = source.grab();

            // Encoding is tens of milliseconds, which is a visible stutter if it
            // happens between game ticks, so that part does go elsewhere.
            executor.execute(() -> slot.png.complete(encode(scene)));
        });
    }

    /** Whichever grabber can read this client's renderer, or null if neither. */
    private SceneSource source()
    {
        if (gpuGrabber.isAvailable())
        {
            return gpuGrabber;
        }
        if (cpuGrabber.isAvailable())
        {
            return cpuGrabber;
        }
        return null;
    }

    /**
     * Claims this key's screenshot and forgets it.
     *
     * Returns null only when nothing was ever captured for this key - the tick
     * already had a screenshot, or the feature is switched off. Otherwise the
     * future completes when the encode does, with null if the frame never
     * arrived or could not be encoded.
     */
    public CompletableFuture<byte[]> take(String captureKey)
    {
        expirePending();
        Pending slot = pending.remove(captureKey);
        return slot == null ? null : slot.png;
    }

    public void reset()
    {
        abandonPending();
        lastCaptureTick = -1;
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
        {
            // The widgets we were holding belong to a session that is gone, so
            // drop them rather than write to them. A screenshot is also proof of
            // a drop by a particular account: anything still waiting when that
            // account goes away cannot be claimed.
            abandonPending();
        }
    }

    /** Settles every screenshot still waiting, with nothing. */
    private void abandonPending()
    {
        for (Pending slot : pending.values())
        {
            slot.png.complete(null);
        }
        pending.clear();
    }

    /** Anything older than the TTL was for a drop nothing wanted. */
    private void expirePending()
    {
        long cutoff = System.currentTimeMillis() - PENDING_TTL_MS;
        pending.values().removeIf(slot ->
        {
            if (slot.capturedAt >= cutoff)
            {
                return false;
            }
            slot.png.complete(null);
            return true;
        });
    }

    private byte[] encode(BufferedImage scene)
    {
        if (scene == null)
        {
            return null;
        }

        try
        {
            BufferedImage scaled = rescale(scene, config.screenshotScale());

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", out);
            return out.toByteArray();
        }
        catch (Exception ex)
        {
            log.warn("Failed to encode drop screenshot", ex);
            return null;
        }
    }

    private static BufferedImage rescale(BufferedImage source, int scalePercent)
    {
        if (scalePercent >= 100 || scalePercent <= 0)
        {
            return source;
        }

        int width = Math.max(1, source.getWidth() * scalePercent / 100);
        int height = Math.max(1, source.getHeight() * scalePercent / 100);

        BufferedImage scaled = new BufferedImage(width, height, source.getType());
        Graphics2D graphics = scaled.createGraphics();
        try
        {
            graphics.drawImage(source, 0, 0, width, height, null);
        }
        finally
        {
            graphics.dispose();
        }

        return scaled;
    }
}
