///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// COPYRIGHT:    Regents of the University of California, 2026
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.tileddataviewer;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Captures what a TiledDataViewer is currently showing, as an image.
 *
 * <p>The capture is taken by asking the viewer's canvas to paint itself into an
 * offscreen buffer, which is the only way to obtain the composite the user
 * actually sees: the canvas draws the rendered frame through its zoom transform
 * and then paints the overlays (scale bar, time stamp, ROIs) on top. Reading the
 * viewer's frame buffer directly would omit the overlays and the zoom.
 *
 * <p>Consequently a capture is the visible viewport at the current zoom, not the
 * full dataset at full resolution. For a full-resolution export of a region, use
 * the Inspector's Export button, which re-composites from storage instead.
 */
public final class CanvasCapture {

   /** How often to re-capture while waiting for the display to change. */
   private static final long POLL_INTERVAL_MS = 20;

   private CanvasCapture() {
   }

   /**
    * Captures the viewer's canvas as it is currently displayed.
    *
    * <p>Safe to call from any thread; the paint is marshalled to the EDT.
    *
    * @param viewer viewer to capture
    * @return the captured image, or null if the canvas has no size yet
    * @throws InterruptedException if interrupted while waiting for the EDT
    */
   public static BufferedImage capture(TiledDataViewerAPI viewer)
         throws InterruptedException {
      if (viewer == null) {
         return null;
      }
      final JPanel canvas = viewer.getCanvasJPanel();
      if (canvas == null) {
         return null;
      }
      final BufferedImage[] result = new BufferedImage[1];
      Runnable paintTask = () -> result[0] = paintOnEdt(canvas);
      if (SwingUtilities.isEventDispatchThread()) {
         paintTask.run();
      } else {
         try {
            SwingUtilities.invokeAndWait(paintTask);
         } catch (java.lang.reflect.InvocationTargetException e) {
            throw new RuntimeException("Failed to capture the viewer canvas", e);
         }
      }
      return result[0];
   }

   /**
    * Captures the canvas once it differs from the given previous capture.
    *
    * <p>TiledDataViewer renders asynchronously and coalesces superseded frames,
    * and neither the display position nor DisplayDidShowImageEvent tells us that
    * the new pixels have actually reached the canvas: the position updates
    * before the render, and the event is posted from the statistics pipeline.
    * So the frame itself is the signal -- capture until it changes.
    *
    * <p>A frame identical to the previous one is indistinguishable from one that
    * has not been drawn yet, so on timeout the latest capture is returned. That
    * only mislabels genuinely identical frames, which are visually the same
    * anyway.
    *
    * @param viewer    viewer to capture
    * @param previous  the previous frame, or null to capture immediately
    * @param timeoutMs how long to wait for the display to change
    * @return the captured image, or null if the canvas has no size
    * @throws InterruptedException if interrupted while waiting
    */
   public static BufferedImage captureWhenChanged(TiledDataViewerAPI viewer,
                                                  BufferedImage previous,
                                                  long timeoutMs)
         throws InterruptedException {
      long deadline = System.currentTimeMillis() + timeoutMs;
      BufferedImage img = capture(viewer);
      if (previous == null) {
         return img;
      }
      while (sameImage(previous, img) && System.currentTimeMillis() < deadline) {
         Thread.sleep(POLL_INTERVAL_MS);
         img = capture(viewer);
      }
      return img;
   }

   /** Compares two captures pixel-for-pixel. */
   private static boolean sameImage(BufferedImage a, BufferedImage b) {
      if (a == null || b == null) {
         return a == b;
      }
      if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
         return false;
      }
      for (int y = 0; y < a.getHeight(); y++) {
         for (int x = 0; x < a.getWidth(); x++) {
            if (a.getRGB(x, y) != b.getRGB(x, y)) {
               return false;
            }
         }
      }
      return true;
   }

   private static BufferedImage paintOnEdt(JPanel canvas) {
      int w = canvas.getWidth();
      int h = canvas.getHeight();
      if (w <= 0 || h <= 0) {
         return null;
      }
      BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
      Graphics g = img.getGraphics();
      try {
         // paint(), not print(): the canvas overrides paint() to draw the scaled
         // frame plus the overlay ROIs, which is exactly the "as displayed" view.
         canvas.paint(g);
      } finally {
         g.dispose();
      }
      return img;
   }
}
