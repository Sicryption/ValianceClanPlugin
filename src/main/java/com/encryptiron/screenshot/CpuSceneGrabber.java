package com.encryptiron.screenshot;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Reads the rendered world out of the client's own buffer, before the interface
 * is drawn into it.
 *
 * Without the GPU plugin the client rasterises the scene into the buffer that
 * {@link Client#getBufferProvider()} hands out, and only afterwards draws the
 * widgets into that same buffer. RuneLite renders overlays at several points
 * during that sequence, and {@link OverlayLayer#UNDER_WIDGETS} is the one that
 * happens after the scene and before the interface - so an overlay on that layer
 * is, in effect, a supported callback at the exact moment the buffer holds the
 * scene and nothing else.
 *
 * Nothing is drawn by this overlay. It renders nothing, changes nothing, and
 * exists only to be called at the right time. That is why this path costs no
 * flicker: the frame is not altered in any way, it is merely read halfway
 * through being built.
 */
@Slf4j
@Singleton
public class CpuSceneGrabber extends Overlay implements SceneSource
{
    @Inject
    private Client client;

    @Inject
    private OverlayManager overlayManager;

    /** Set when a capture is wanted; the next render past the scene takes it. */
    private volatile boolean wanted = false;

    private volatile BufferedImage scene = null;

    public CpuSceneGrabber()
    {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.UNDER_WIDGETS);
    }

    public void startUp()
    {
        overlayManager.add(this);
    }

    public void shutDown()
    {
        overlayManager.remove(this);
        scene = null;
    }

    @Override
    public boolean isAvailable()
    {
        return !client.isGpu();
    }

    /** Ask for the scene as it stands at the next pre-widget render. */
    @Override
    public void arm()
    {
        scene = null;
        wanted = true;
    }

    /** The scene grabbed since {@link #arm}, or null if none has arrived yet. */
    @Override
    public BufferedImage grab()
    {
        BufferedImage grabbed = scene;
        scene = null;
        return grabbed;
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!wanted)
        {
            return null;
        }

        wanted = false;

        try
        {
            BufferProvider buffer = client.getBufferProvider();
            int width = buffer.getWidth();
            int height = buffer.getHeight();
            int[] pixels = buffer.getPixels();

            if (width <= 0 || height <= 0 || pixels == null || pixels.length < width * height)
            {
                return null;
            }

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            // Copied rather than wrapped: these pixels are the live client buffer
            // and the widgets are about to be drawn straight over them.
            image.setRGB(0, 0, width, height, pixels, 0, width);
            scene = image;
        }
        catch (Exception ex)
        {
            log.warn("Failed to read the scene buffer", ex);
        }

        return null;
    }
}
