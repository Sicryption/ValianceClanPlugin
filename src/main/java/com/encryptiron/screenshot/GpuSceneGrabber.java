package com.encryptiron.screenshot;

import static org.lwjgl.opengl.GL33C.GL_COLOR_ATTACHMENT0;
import static org.lwjgl.opengl.GL33C.GL_COLOR_BUFFER_BIT;
import static org.lwjgl.opengl.GL33C.GL_DRAW_FRAMEBUFFER;
import static org.lwjgl.opengl.GL33C.GL_FRAMEBUFFER;
import static org.lwjgl.opengl.GL33C.GL_FRAMEBUFFER_COMPLETE;
import static org.lwjgl.opengl.GL33C.GL_NEAREST;
import static org.lwjgl.opengl.GL33C.GL_NO_ERROR;
import static org.lwjgl.opengl.GL33C.GL_DRAW_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL33C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME;
import static org.lwjgl.opengl.GL33C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE;
import static org.lwjgl.opengl.GL33C.GL_NONE;
import static org.lwjgl.opengl.GL33C.GL_PACK_ALIGNMENT;
import static org.lwjgl.opengl.GL33C.GL_READ_FRAMEBUFFER;
import static org.lwjgl.opengl.GL33C.GL_READ_FRAMEBUFFER_BINDING;
import static org.lwjgl.opengl.GL33C.GL_RENDERBUFFER;
import static org.lwjgl.opengl.GL33C.GL_RENDERBUFFER_HEIGHT;
import static org.lwjgl.opengl.GL33C.GL_RENDERBUFFER_WIDTH;
import static org.lwjgl.opengl.GL33C.GL_RGBA;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_HEIGHT;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_WIDTH;
import static org.lwjgl.opengl.GL33C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL33C.glBindFramebuffer;
import static org.lwjgl.opengl.GL33C.glBlitFramebuffer;
import static org.lwjgl.opengl.GL33C.glCheckFramebufferStatus;
import static org.lwjgl.opengl.GL33C.glDeleteFramebuffers;
import static org.lwjgl.opengl.GL33C.glDeleteRenderbuffers;
import static org.lwjgl.opengl.GL33C.glFramebufferRenderbuffer;
import static org.lwjgl.opengl.GL33C.glGenFramebuffers;
import static org.lwjgl.opengl.GL33C.glGenRenderbuffers;
import static org.lwjgl.opengl.GL33C.glGetError;
import static org.lwjgl.opengl.GL33C.glRenderbufferStorage;
import static org.lwjgl.opengl.GL33C.glBindRenderbuffer;
import static org.lwjgl.opengl.GL33C.glBindTexture;
import static org.lwjgl.opengl.GL33C.glGetFramebufferAttachmentParameteriv;
import static org.lwjgl.opengl.GL33C.glGetInteger;
import static org.lwjgl.opengl.GL33C.glGetRenderbufferParameteriv;
import static org.lwjgl.opengl.GL33C.glGetTexLevelParameteriv;
import static org.lwjgl.opengl.GL33C.glPixelStorei;
import static org.lwjgl.opengl.GL33C.glReadPixels;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Renderable;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;
import org.lwjgl.BufferUtils;

/**
 * Reads the rendered world out of the GPU, before the interface is drawn over it.
 *
 * The GPU plugin renders the scene into a framebuffer of its own and only blits
 * it to the screen at the top of {@code draw()}, immediately before the
 * interface is composited on top. That framebuffer therefore holds exactly what
 * we want - the whole scene, no interface - and it holds it every frame,
 * already, whether or not anyone asks for it. Nothing needs to be hidden or
 * re-rendered; the image exists and the only problem is reaching it.
 *
 * <h2>Finding it without reflection</h2>
 *
 * The framebuffer's name is a private field on the GPU plugin, but it does not
 * have to be read from there. Between {@code preSceneDraw} and
 * {@code postSceneDraw} the GPU plugin leaves that framebuffer bound as the
 * draw target, and the client calls registered {@link RenderCallback}s
 * throughout that window - once per entity, on the client thread, with the
 * context current. Asking OpenGL what is currently bound during one of those
 * calls yields the same number, from a supported API, with nothing reached into.
 *
 * It also means this is not tied to the stock GPU plugin's internals: any
 * renderer that draws the scene to its own framebuffer and calls the same
 * callbacks answers the same question the same way.
 *
 * <h2>Safety</h2>
 *
 * This never changes what is rendered. {@link #addEntity} always returns true,
 * the previous read binding is always put back, and any GL failure switches the
 * whole path off for the session rather than risking a corrupted context - a
 * lost screenshot is a nuisance, a broken renderer is somebody unable to play.
 */
@Slf4j
@Singleton
public class GpuSceneGrabber implements RenderCallback, SceneSource
{
    @Inject
    private Client client;

    @Inject
    private RenderCallbackManager renderCallbackManager;

    /**
     * The scene framebuffer, as last seen bound during scene rendering.
     *
     * Kept across frames: a frame with no entities at all would otherwise lose
     * it, and the name does not change between frames.
     */
    private volatile int sceneFbo = 0;

    /** Set when GL has failed once. Never cleared - one failure is enough. */
    private volatile boolean unavailable = false;

    /** How many times we have looked for the scene framebuffer, for diagnostics. */
    @Getter
    private volatile int sampleAttempts = 0;

    /** Only sample the binding while a capture is actually wanted. */
    private volatile boolean sampling = false;

    /** Our single-sample buffer, for resolving the multisampled scene into. */
    private int resolveFbo = 0;
    private int resolveRbo = 0;
    private int resolveWidth = 0;
    private int resolveHeight = 0;

    /**
     * Why the last attempt produced nothing.
     *
     * Kept so the player can be told, because the alternative is a feature that
     * silently does nothing and a log file that dev clients do not write to.
     */
    @Getter
    private volatile String lastFailure = null;

    public void startUp()
    {
        renderCallbackManager.register(this);
    }

    public void shutDown()
    {
        renderCallbackManager.unregister(this);
        // Deliberately not deleting the GL objects here: shutDown can run off
        // the client thread, where there is no current context and the delete
        // would be undefined behaviour rather than a leak. They go when the
        // context does.
    }

    @Override
    public boolean isAvailable()
    {
        return !unavailable && client.isGpu();
    }

    /** Begin watching for the scene framebuffer, for a capture about to happen. */
    @Override
    public void arm()
    {
        sampling = true;
    }

    /**
     * Never alters rendering. Returning anything but true here would remove
     * entities from the player's own view, which is emphatically not our
     * business - this callback is being used only as a moment in time when the
     * scene framebuffer is known to be bound.
     */
    @Override
    public boolean addEntity(Renderable renderable, boolean ui)
    {
        if (sampling && !unavailable && !ui)
        {
            try
            {
                int bound = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
                if (bound > 0)
                {
                    sceneFbo = bound;
                    sampling = false;
                }
            }
            catch (Throwable ex)
            {
                fail("could not read the current framebuffer binding", ex);
            }
            finally
            {
                sampleAttempts++;
            }
        }

        return true;
    }

    /**
     * The scene as an image, or null if it could not be read.
     *
     * Must be called with the GL context current - that is, from inside a
     * DrawManager frame listener, which runs on the client thread within the
     * renderer's own draw call.
     */
    @Override
    public BufferedImage grab()
    {
        int fbo = sceneFbo;
        if (unavailable || fbo <= 0)
        {
            return null;
        }

        int previousRead = 0;
        int previousDraw = 0;
        try
        {
            previousRead = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            previousDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);

            glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);

            int[] size = attachmentSize();
            if (size == null)
            {
                lastFailure = "the scene framebuffer has no readable colour attachment";
                return null;
            }

            int width = size[0];
            int height = size[1];

            // The scene is rendered into a multisampled renderbuffer whenever
            // anti-aliasing is on, and glReadPixels on a multisampled attachment
            // is an error rather than a slow path. Resolving through a blit into
            // our own single-sample buffer is the supported way to read one, and
            // it costs nothing when anti-aliasing is off.
            if (!ensureResolveTarget(width, height))
            {
                return null;
            }

            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, resolveFbo);
            glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL_COLOR_BUFFER_BIT, GL_NEAREST);

            glBindFramebuffer(GL_READ_FRAMEBUFFER, resolveFbo);

            ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
            glPixelStorei(GL_PACK_ALIGNMENT, 1);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);

            int error = glGetError();
            if (error != GL_NO_ERROR)
            {
                lastFailure = "reading the scene failed with GL error 0x" + Integer.toHexString(error);
                return null;
            }

            lastFailure = null;
            return toImage(pixels, width, height);
        }
        catch (Throwable ex)
        {
            fail("could not read the scene framebuffer", ex);
            return null;
        }
        finally
        {
            try
            {
                glBindFramebuffer(GL_READ_FRAMEBUFFER, previousRead);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, previousDraw);
            }
            catch (Throwable ex)
            {
                // Leaving a binding moved would corrupt the next frame, so a
                // failure here is the one worth shouting about.
                fail("could not restore the previous framebuffer bindings", ex);
            }
        }
    }

    /**
     * Our own single-sample buffer to resolve the scene into, sized to match.
     *
     * Kept between captures and rebuilt only when the scene size changes, so a
     * drop does not cost an allocation of several megabytes of GPU memory on
     * every kill.
     */
    private boolean ensureResolveTarget(int width, int height)
    {
        if (resolveFbo != 0 && width == resolveWidth && height == resolveHeight)
        {
            return true;
        }

        releaseResolveTarget();

        resolveFbo = glGenFramebuffers();
        resolveRbo = glGenRenderbuffers();

        glBindFramebuffer(GL_FRAMEBUFFER, resolveFbo);
        glBindRenderbuffer(GL_RENDERBUFFER, resolveRbo);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_RGBA, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, resolveRbo);
        glBindRenderbuffer(GL_RENDERBUFFER, 0);

        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE)
        {
            lastFailure = "our resolve framebuffer is incomplete (status " + status + ")";
            releaseResolveTarget();
            return false;
        }

        resolveWidth = width;
        resolveHeight = height;
        return true;
    }

    private void releaseResolveTarget()
    {
        if (resolveFbo != 0)
        {
            glDeleteFramebuffers(resolveFbo);
            resolveFbo = 0;
        }
        if (resolveRbo != 0)
        {
            glDeleteRenderbuffers(resolveRbo);
            resolveRbo = 0;
        }
        resolveWidth = 0;
        resolveHeight = 0;
    }

    /**
     * How big the framebuffer actually is.
     *
     * Asked of the attachment rather than computed from the canvas: the scene is
     * rendered at the stretched size and again at the display's scale factor, so
     * a canvas-derived guess is wrong on a stretched client, on a HiDPI display,
     * and on both at once.
     */
    private int[] attachmentSize()
    {
        int[] type = new int[1];
        glGetFramebufferAttachmentParameteriv(
            GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
            GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE, type);

        if (type[0] == GL_NONE)
        {
            return null;
        }

        int[] name = new int[1];
        glGetFramebufferAttachmentParameteriv(
            GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
            GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, name);

        int[] width = new int[1];
        int[] height = new int[1];

        if (type[0] == GL_RENDERBUFFER)
        {
            glBindRenderbuffer(GL_RENDERBUFFER, name[0]);
            glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_WIDTH, width);
            glGetRenderbufferParameteriv(GL_RENDERBUFFER, GL_RENDERBUFFER_HEIGHT, height);
            glBindRenderbuffer(GL_RENDERBUFFER, 0);
        }
        else if (type[0] == GL_TEXTURE)
        {
            glBindTexture(GL_TEXTURE_2D, name[0]);
            glGetTexLevelParameteriv(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH, width);
            glGetTexLevelParameteriv(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT, height);
            glBindTexture(GL_TEXTURE_2D, 0);
        }
        else
        {
            return null;
        }

        if (width[0] <= 0 || height[0] <= 0)
        {
            return null;
        }

        return new int[]{width[0], height[0]};
    }

    /** OpenGL's origin is the bottom-left, so the rows come back upside down. */
    static BufferedImage toImage(ByteBuffer pixels, int width, int height)
    {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[width];

        for (int y = 0; y < height; y++)
        {
            int offset = (height - 1 - y) * width * 4;
            for (int x = 0; x < width; x++)
            {
                int i = offset + x * 4;
                int r = pixels.get(i) & 0xFF;
                int g = pixels.get(i + 1) & 0xFF;
                int b = pixels.get(i + 2) & 0xFF;
                row[x] = (r << 16) | (g << 8) | b;
            }
            image.setRGB(0, y, width, 1, row, 0, width);
        }

        return image;
    }

    private void fail(String what, Throwable ex)
    {
        if (!unavailable)
        {
            unavailable = true;
            sampling = false;
            lastFailure = what;
            log.warn("Drop screenshots disabled for this session - {}", what, ex);
        }
    }
}
