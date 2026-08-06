package org.micromanager.tileddataviewer.internal.gui;


import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JPanel;
import org.micromanager.tileddataviewer.internal.TiledDataViewer;
import org.micromanager.tileddataviewer.overlay.Overlay;
import org.micromanager.tileddataviewer.overlay.Roi;

public class ViewerCanvas {

   private volatile Image currentImage_;
   private volatile Overlay currentOverlay_ = new Overlay();
   private double scale_;
   private TiledDataViewer display_;
   private JPanel canvas_;
   // Cached BufferedImage version of the last rendered frame, for pixel lookups.
   private volatile BufferedImage renderedBuffer_;
   // Notified after each paint completes; see notifyRenderComplete().
   private final List<Runnable> renderCompleteListeners_ = new CopyOnWriteArrayList<>();
   // Render generation bookkeeping. A frame is only "complete" once the overlay
   // computed for that same generation has been installed and painted: the image
   // and its overlay are produced by different threads and painted separately,
   // so a paint carrying an older overlay is not yet the finished frame.
   private final AtomicLong imageGeneration_ = new AtomicLong();
   private final AtomicLong overlayGeneration_ = new AtomicLong(-1);
   // Generation an overlayer plugin is currently drawing for, applied when that
   // plugin installs its overlay; -1 when none is pending.
   private final AtomicLong pendingOverlayGeneration_ = new AtomicLong(-1);
   private volatile long signalledGeneration_ = -1;

   public ViewerCanvas(TiledDataViewer display) {
      canvas_ = createCanvas();
      display_ = display;

      //For recreating/resizing compositie image on window size change
      canvas_.addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(ComponentEvent e) {
            display_.onCanvasResize(canvas_.getWidth(), canvas_.getHeight());
         }
      });
   }

   public void onDisplayClose() {
      for (ComponentListener l : canvas_.getComponentListeners()) {
         canvas_.removeComponentListener(l);
      }
      for (MouseListener l : canvas_.getMouseListeners()) {
         canvas_.removeMouseListener(l);
      }
      for (MouseMotionListener l : canvas_.getMouseMotionListeners()) {
         canvas_.removeMouseMotionListener(l);
      }
      for (KeyListener l : canvas_.getKeyListeners()) {
         canvas_.removeKeyListener(l);
      }
      for (MouseWheelListener l : canvas_.getMouseWheelListeners()) {
         canvas_.removeMouseWheelListener(l);
      }

      canvas_ = null;
      display_ = null;
   }

   /**
    * Set the size of the image displayed on screen, which is not neccesarily
    * the same as the image pixels read to create it.
    *
    * @param w
    * @param h
    */
   public void onCanvasResize(int w, int h) {
   }

   /**
    * Installs a new frame and opens a new render generation.
    *
    * @param img   the rendered image
    * @param scale factor the image is drawn at
    * @return the generation this frame belongs to; the overlay computed for it
    *         must be installed with {@link #updateOverlay(Overlay, long)}
    */
   long updateDisplayImage(Image img, double scale) {
      currentImage_ = img;
      scale_ = scale;
      // Invalidate the cached buffer whenever a new frame arrives.
      // The BufferedImage copy is created lazily in getRenderedPixelRGB() only when needed.
      renderedBuffer_ = null;
      return imageGeneration_.incrementAndGet();
   }

   /**
    * Returns the RGB values at the given canvas coordinates from the last rendered frame.
    * Returns null if no frame has been rendered yet or coordinates are out of bounds.
    * The array contains [R, G, B] values in the range 0–255.
    * These are the display-mapped values (post contrast/gamma), which is sufficient
    * for computing white-balance ratios since only relative R:G:B proportions matter.
    */
   public int[] getRenderedPixelRGB(int canvasX, int canvasY) {
      Image img = currentImage_;
      double scale = scale_;
      if (img == null || scale <= 0) {
         return null;
      }
      // Build or reuse the cached BufferedImage copy (created lazily on first pixel lookup).
      BufferedImage buf = renderedBuffer_;
      if (buf == null) {
         if (img instanceof BufferedImage) {
            buf = (BufferedImage) img;
         } else {
            int w = img.getWidth(null);
            int h = img.getHeight(null);
            if (w <= 0 || h <= 0) {
               return null;
            }
            buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics g = buf.getGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
         }
         renderedBuffer_ = buf;
      }
      // The AffineTransform in paint() scales the image UP by scale when drawing it.
      // canvas pixel = renderedImagePixel * scale → renderedImagePixel = canvasPixel / scale
      int px = (int) Math.floor(canvasX / scale);
      int py = (int) Math.floor(canvasY / scale);
      if (px < 0 || py < 0 || px >= buf.getWidth() || py >= buf.getHeight()) {
         return null;
      }
      int rgb = buf.getRGB(px, py);
      return new int[]{(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
   }

   void updateOverlay(Overlay overlay) {
      // An overlayer plugin installing its own result, or a refresh not tied to a
      // new frame. Applying the generation here -- when the overlay pixels
      // actually land -- is what makes the repaint the caller schedules next the
      // one that reports completion.
      updateOverlayPixels(overlay);
      // Read without clearing: a plugin may call setOverlay() more than once
      // while drawing a frame (the bridge plugin has several early-return
      // paths), and consuming the value on the first call would leave the later
      // ones -- and any frame whose plugin takes a different path -- unreported.
      // setOverlayGeneration() only ever moves forwards, so repeats are benign.
      long pending = pendingOverlayGeneration_.get();
      if (pending >= 0) {
         setOverlayGeneration(pending);
      }
   }

   /**
    * Installs the overlay computed for a particular render generation.
    *
    * @param overlay    the overlay to draw
    * @param generation the generation returned by {@link #updateDisplayImage}
    *                   when the matching frame was installed
    */
   void updateOverlay(Overlay overlay, long generation) {
      updateOverlayPixels(overlay);
      setOverlayGeneration(generation);
   }

   /**
    * Declares which render generation the overlay about to be installed by an
    * overlayer plugin belongs to.
    *
    * <p>Such plugins call setOverlay() themselves and cannot pass the generation
    * through, so it is announced here and applied by {@link #updateOverlay} when
    * that overlay arrives.
    *
    * @param generation the generation the next plugin overlay belongs to
    */
   void setPendingOverlayGeneration(long generation) {
      pendingOverlayGeneration_.set(generation);
   }

   /** Replaces the drawn overlay without touching generation bookkeeping. */
   private void updateOverlayPixels(Overlay overlay) {
      synchronized (currentOverlay_) {
         currentOverlay_.clear();
         for (int i = 0; i < overlay.size(); i++) {
            currentOverlay_.add(overlay.get(i));
         }
      }
   }

   /**
    * Records that the overlay for the given generation is in place, without
    * replacing it: used by overlayer plugins that install their own overlay.
    *
    * @param generation render generation whose overlay is now current
    */
   void setOverlayGeneration(long generation) {
      // Only ever move forwards: a superseded overlay task may finish after a
      // newer one has already installed its result.
      long current = overlayGeneration_.get();
      while (generation > current
            && !overlayGeneration_.compareAndSet(current, generation)) {
         current = overlayGeneration_.get();
      }
   }

   public JPanel getCanvas() {
      return canvas_;
   }

   /**
    * Registers a listener run after every canvas paint completes.
    *
    * <p>Listeners are called on the EDT from within paint(), so they must be
    * cheap and must not trigger another repaint.
    *
    * @param listener run once the frame is on screen
    */
   public void addRenderCompleteListener(Runnable listener) {
      if (listener != null) {
         renderCompleteListeners_.add(listener);
      }
   }

   /**
    * Unregisters a render-complete listener.
    *
    * @param listener the listener to remove
    */
   public void removeRenderCompleteListener(Runnable listener) {
      renderCompleteListeners_.remove(listener);
   }

   /**
    * Signals completion if this paint drew a frame together with the overlay
    * belonging to it.
    *
    * <p>The image and its overlay are produced by different threads and each
    * triggers its own repaint, so the first paint after a new frame usually
    * still carries the previous frame's overlay. Signalling then would hand a
    * capture the wrong scale bar or time stamp. Waiting for the overlay
    * generation to catch up is what makes the "including its overlay" contract
    * true. Each generation is signalled at most once, so ordinary repaints
    * (resize, expose) do not produce spurious completions.
    */
   private void notifyRenderComplete() {
      if (renderCompleteListeners_.isEmpty()) {
         return;
      }
      long generation = imageGeneration_.get();
      if (overlayGeneration_.get() < generation || signalledGeneration_ == generation) {
         return;
      }
      signalledGeneration_ = generation;
      for (Runnable listener : renderCompleteListeners_) {
         try {
            listener.run();
         } catch (RuntimeException e) {
            // A misbehaving listener must not break painting.
            System.err.println("TiledDataViewer: render complete listener threw: " + e);
         }
      }
   }

   private JPanel createCanvas() {
      return new JPanel() {
         @Override
         public void paint(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            AffineTransform af = new AffineTransform(scale_, 0, 0, scale_, 0, 0);
            g2.drawImage(currentImage_, af, canvas_);
            synchronized (currentOverlay_) {
               if (currentOverlay_ != null) {
                  for (int i = 0; i < currentOverlay_.size(); i++) {
                     Roi roi = currentOverlay_.get(i);
                     roi.drawOverlay(g);
                  }
               }
            }
            // Report completion only when this paint drew a frame together with
            // the overlay computed for it; see notifyRenderComplete().
            notifyRenderComplete();
         }

         public void update(Graphics g) {
            paint(g);
         }
      };
   }

}
