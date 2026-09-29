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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    * Runs an action that changes what the viewer shows, then captures the frame
    * the viewer painted in response.
    *
    * <p>Setting the display position only *requests* a render: rendering is
    * asynchronous and superseded frames are coalesced away, so capturing
    * immediately afterwards can catch the previous frame. This waits for the
    * viewer's render-complete signal, which fires once the frame and its overlay
    * are actually on the canvas.
    *
    * <p>If the render does not complete within the timeout this throws rather
    * than returning whatever the canvas happens to hold: that would be the
    * previous frame, and writing it would silently duplicate a frame and shift
    * every later one, corrupting the movie in a way that is hard to spot.
    *
    * @param viewer         viewer to capture
    * @param changeDisplay  action that moves the viewer to the desired frame
    * @param timeoutMs      how long to wait for the paint
    * @return the captured image, or null if the canvas has no size
    * @throws InterruptedException if interrupted while waiting
    * @throws TimeoutException     if the viewer did not finish rendering in time
    */
   public static BufferedImage captureAfter(TiledDataViewerAPI viewer,
                                            Runnable changeDisplay,
                                            long timeoutMs)
         throws InterruptedException, TimeoutException {
      if (viewer == null) {
         return null;
      }
      if (SwingUtilities.isEventDispatchThread()) {
         // Waiting for a paint from the EDT would block the very thread that
         // performs it. Callers must drive this from a worker thread.
         throw new IllegalStateException(
               "captureAfter() must not be called on the EDT");
      }
      final CountDownLatch painted = new CountDownLatch(1);
      Runnable listener = painted::countDown;
      viewer.addRenderCompleteListener(listener);
      boolean completed;
      try {
         changeDisplay.run();
         completed = painted.await(timeoutMs, TimeUnit.MILLISECONDS);
      } finally {
         viewer.removeRenderCompleteListener(listener);
      }
      if (!completed) {
         throw new TimeoutException(
               "The viewer did not finish rendering within " + timeoutMs + " ms");
      }
      return capture(viewer);
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
