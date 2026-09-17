package com.encryptiron.screenshot;

import static org.junit.Assert.assertEquals;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import org.junit.Test;

/**
 * OpenGL hands pixels back bottom-row-first, and in RGBA byte order rather than
 * the packed ARGB int a BufferedImage wants. Both are easy to get subtly wrong -
 * an upside-down screenshot is obvious, but swapped red and blue channels just
 * look like a slightly odd screenshot, which is exactly the kind of thing that
 * ships and is never questioned.
 */
public class GpuSceneGrabberTest
{
    /** Builds a GL-style RGBA buffer, bottom row first, from top-row-first rows. */
    private static ByteBuffer glPixels(int width, int height, Color[][] topFirst)
    {
        ByteBuffer buffer = ByteBuffer.allocate(width * height * 4);
        for (int y = height - 1; y >= 0; y--)
        {
            for (int x = 0; x < width; x++)
            {
                Color c = topFirst[y][x];
                buffer.put((byte) c.getRed());
                buffer.put((byte) c.getGreen());
                buffer.put((byte) c.getBlue());
                buffer.put((byte) 255);
            }
        }
        buffer.flip();
        return buffer;
    }

    @Test
    public void theImageComesOutTheRightWayUp()
    {
        Color topLeft = new Color(255, 0, 0);
        Color bottomRight = new Color(0, 0, 255);
        Color filler = new Color(10, 20, 30);

        Color[][] rows = {
            {topLeft, filler},
            {filler, bottomRight},
        };

        BufferedImage image = GpuSceneGrabber.toImage(glPixels(2, 2, rows), 2, 2);

        assertEquals(topLeft.getRGB(), image.getRGB(0, 0));
        assertEquals(bottomRight.getRGB(), image.getRGB(1, 1));
    }

    @Test
    public void redAndBlueAreNotSwapped()
    {
        // The failure this exists for: a screenshot that looks plausible but has
        // the channels the wrong way round.
        Color red = new Color(200, 10, 20);
        Color[][] rows = {{red}};

        BufferedImage image = GpuSceneGrabber.toImage(glPixels(1, 1, rows), 1, 1);

        assertEquals(200, new Color(image.getRGB(0, 0)).getRed());
        assertEquals(10, new Color(image.getRGB(0, 0)).getGreen());
        assertEquals(20, new Color(image.getRGB(0, 0)).getBlue());
    }

    @Test
    public void everyPixelOfAWiderThanTallFrameLandsWhereItBelongs()
    {
        // Width and height are transposed in exactly one place if the row
        // arithmetic is wrong, and a square test image would never show it.
        int width = 4;
        int height = 2;
        Color[][] rows = new Color[height][width];
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                rows[y][x] = new Color(x * 40, y * 100, 0);
            }
        }

        BufferedImage image = GpuSceneGrabber.toImage(glPixels(width, height, rows), width, height);

        assertEquals(width, image.getWidth());
        assertEquals(height, image.getHeight());
        for (int y = 0; y < height; y++)
        {
            for (int x = 0; x < width; x++)
            {
                assertEquals(rows[y][x].getRGB(), image.getRGB(x, y));
            }
        }
    }
}
