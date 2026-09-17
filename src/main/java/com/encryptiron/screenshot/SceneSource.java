package com.encryptiron.screenshot;

import java.awt.image.BufferedImage;

/**
 * Somewhere the rendered world can be read before the interface goes over it.
 *
 * Two implementations, one per renderer, because that intermediate image lives
 * in a different place depending on whether the GPU plugin is running - but the
 * shape is the same either way: say you want the next one, then read it from
 * inside the frame listener.
 */
interface SceneSource
{
    /** Whether this can read the scene on the client as it is currently set up. */
    boolean isAvailable();

    /** Watch for the next frame's scene. */
    void arm();

    /**
     * The scene, or null if it could not be read.
     *
     * Only valid from inside a DrawManager frame listener - for the GPU source
     * that is the one moment the GL context is current.
     */
    BufferedImage grab();
}
