package org.micromanager.tileddataviewer.internal.gui;

import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.DoubleConsumer;
import javax.swing.Timer;
import org.micromanager.tileddataviewer.TiledDataViewerCanvasMouseListenerInterface;
import org.micromanager.tileddataviewer.TiledDataViewerOverlayerPlugin;
import org.micromanager.tileddataviewer.internal.TiledDataViewer;
import org.micromanager.tileddataviewer.overlay.Overlay;



public class GuiManager {

   // Bounds on the playback timer interval.  The tick rate sets timing granularity
   // only; how far each tick moves is derived from elapsed time and the requested
   // rate.  The lower bound keeps a very high rate from asking for a Swing timer
   // interval it cannot honor; the upper bound keeps a very low rate from queueing
   // many no-op ticks per second.
   private static final int MIN_TICK_MS = 5;
   private static final int MAX_TICK_MS = 100;

   private DisplayWindow displayWindow_;

   private ImageMaker imageMaker_;
   private BaseOverlayer overlayer_;
   // Written by shutdown() on the closing thread, read by ticks on the EDT.
   private volatile Timer animationTimer_;
   private double animationFPS_ = 7;
   // Set once the stored playback rate has been read; see getAnimateFPS().
   private boolean animationFpsLoaded_ = false;
   // Notified when the playback rate changes, so UI showing it can follow.
   private final List<DoubleConsumer> animateFpsListeners_ = new CopyOnWriteArrayList<>();
   // Playback state, accessed on the EDT only.
   private long lastTickNs_;
   private double cumulativeFrameCountError_;
   private int currentAnimationPosition_;

   private TiledDataViewer display_;

   public GuiManager(TiledDataViewer tiledDataViewer, boolean acquisition) {
      displayWindow_ = new DisplayWindow(tiledDataViewer, !acquisition);

      overlayer_ = new BaseOverlayer(tiledDataViewer);
      imageMaker_ = new ImageMaker(tiledDataViewer, tiledDataViewer.getDataSource());
      display_ = tiledDataViewer;

   }

   public void onScrollersAdded() {
      displayWindow_.onScrollersAdded();
   }

   public void onCanvasResize(int w, int h) {
      if (displayWindow_ == null) {
         return; // during startup
      }
      displayWindow_.onCanvasResized(w, h);

   }

   public void setWindowTitle(String s) {
      if (displayWindow_ != null) {
         displayWindow_.setTitle(s);
      }
   }


   public boolean isScrollerAxisLocked(String axis) {
      return displayWindow_.isScrollerAxisLocked(axis);
   }

   /**
    * Starts or stops playback along the given scroller's axis.
    *
    * <p>Playback advances by elapsed wall-clock time rather than by one position per
    * timer tick, so the requested rate is honored even when rendering cannot keep up:
    * positions are skipped instead of playback slowing down.
    *
    * <p>The timer deliberately runs on the EDT.  Ticks can then be delivered late when
    * the EDT is busy, but the elapsed-time math absorbs that, costing smoothness rather
    * than playback rate.  Do not move this to a scheduled executor: setImageEvent()
    * mutates Swing state throughout (scrollbarsMoved, setActive, the setImageHooks),
    * and DisplayModel.parseNewAxesToUpdateDisplayModel() uses invokeAndWait() when off
    * the EDT, which could deadlock against a busy EDT.  Marshalling back with
    * invokeLater() would relocate the jitter rather than remove it.
    *
    * @param scroller scroller whose axis is played back
    * @param animate  true to start playback, false to stop it
    */
   public void onAnimationToggle(final AxisScroller scroller, boolean animate) {
      if (animationTimer_ != null) {
         animationTimer_.stop();
         animationTimer_ = null;
      }
      if (!animate) {
         return;
      }
      lastTickNs_ = System.nanoTime();
      cumulativeFrameCountError_ = 0.0;
      // Seed the position once, while we know we are in sync with the scroller.  From
      // here on the driver owns the position: the scrollbar is only updated once a frame
      // actually renders, so reading it back every tick would couple playback rate to
      // render rate, which is what capped the rate before.
      currentAnimationPosition_ = (scroller != null && scroller.isInitialized())
              ? scroller.getPosition() : 0;
      // Via the getter, so a viewer whose stored rate has not been read yet
      // starts the timer at that rate rather than the hard-coded default.
      animationTimer_ = new Timer(tickIntervalFromFPS(getAnimateFPS()), new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            onAnimationTick(scroller);
         }
      });
      animationTimer_.start();
   }

   /**
    * Runs one playback tick, advancing the animated axis by however many positions the
    * elapsed wall-clock time calls for.  That may be zero (at rates slower than the tick
    * rate) or several (when the EDT or the renderer fell behind).
    */
   private void onAnimationTick(AxisScroller scroller) {
      // Consume elapsed time first, so that the early returns below cannot let time
      // accumulate into one large jump once playback does resume.
      long nowNs = System.nanoTime();
      final double elapsedMs = (nowNs - lastTickNs_) / 1000000.0;
      lastTickNs_ = nowNs;

      // shutdown() runs on the closing thread and can land between a tick being queued
      // and it being dispatched.
      if (display_ == null || displayWindow_ == null) {
         return;
      }
      Timer timer = animationTimer_;
      if (timer == null || !timer.isRunning()) {
         return; // Event queued before stop() was called.
      }
      if (scroller == null || !scroller.isInitialized()) {
         return; // No data bound to this axis yet.
      }

      int min = scroller.getMinimum();
      int max = scroller.getMaximum();
      // Inclusive bounds, so the number of positions is the span plus one.
      int range = max - min + 1;
      if (range <= 1) {
         cumulativeFrameCountError_ = 0.0;
         return;
      }

      // Carry the fractional remainder across ticks so that rates slower than the tick
      // rate still average out to exactly the requested rate.
      double frames = getAnimateFPS() * elapsedMs / 1000.0;
      frames -= cumulativeFrameCountError_;
      int framesToAdvance = Math.max(0, (int) Math.round(frames));
      if (framesToAdvance > range) {
         // A long stall (garbage collection, a modal dialog).  Advancing more than one
         // full loop looks the same as advancing one, and carrying the debt forward
         // would keep us jumping after the stall has passed.
         framesToAdvance = range;
         cumulativeFrameCountError_ = 0.0;
      } else {
         cumulativeFrameCountError_ = framesToAdvance - frames;
      }
      if (framesToAdvance == 0) {
         return; // Not enough time has passed to move yet.
      }

      int newPos = min + Math.floorMod(currentAnimationPosition_ - min + framesToAdvance,
              range);
      currentAnimationPosition_ = newPos;
      display_.setAxisPosition(scroller.getAxis(), newPos);
   }

   public ViewerCanvas getCanvas() {
      return displayWindow_.getCanvas();
   }

   public void superlockAllScrollers() {
      displayWindow_.superlockAllScrollers();
   }

   public void unlockAllScroller() {
      displayWindow_.unlockAllScrollers();
   }

   /**
    * Sets the playback rate.  If an animation is currently running, its rate is
    * changed on the fly; otherwise the new rate applies to the next animation.
    *
    * @param fps desired playback rate in frames per second
    */
   public void setAnimateFPS(double fps) {
      animationFPS_ = fps;
      animationFpsLoaded_ = true; // Do not let a later read overwrite this choice.
      if (display_ != null && display_.getDisplayModel() != null) {
         display_.getDisplayModel().setPlaybackFPS(fps);
      }
      Timer timer = animationTimer_;
      if (timer != null && timer.isRunning()) {
         // Drop any accumulated fractional-frame remainder: it was accrued at the old
         // rate, and carrying it over would produce one catch-up jump after the change.
         cumulativeFrameCountError_ = 0.0;
         lastTickNs_ = System.nanoTime();
         timer.setDelay(tickIntervalFromFPS(fps));
      }
      for (DoubleConsumer listener : animateFpsListeners_) {
         listener.accept(fps);
      }
   }

   /**
    * Registers a listener notified whenever the playback rate changes.
    *
    * @param listener receives the new rate in frames per second
    */
   public void addAnimateFpsListener(DoubleConsumer listener) {
      if (listener != null) {
         animateFpsListeners_.add(listener);
      }
   }

   /**
    * Unregisters a playback rate listener. Callers must do this when they go
    * away, or they will be retained for the life of the viewer.
    *
    * @param listener the listener to remove
    */
   public void removeAnimateFpsListener(DoubleConsumer listener) {
      animateFpsListeners_.remove(listener);
   }

   /**
    * Returns the playback rate.  The stored display setting wins until the rate is set
    * in this session, so a reopened dataset starts at the rate it was last played at.
    *
    * <p>Every read of the rate must go through here rather than touching
    * animationFPS_ directly: the stored setting is loaded lazily, so a direct
    * field read before the first call would see the hard-coded default and, for
    * a newly created viewer, run playback at a rate the control does not show.
    */
   public double getAnimateFPS() {
      if (!animationFpsLoaded_ && display_ != null && display_.getDisplayModel() != null) {
         animationFPS_ = display_.getDisplayModel().getPlaybackFPS();
         animationFpsLoaded_ = true;
      }
      return animationFPS_;
   }

   /**
    * Re-reads the playback rate from the display settings and updates the control.
    * Called once the settings have been loaded from disk, which happens after the
    * playback control was first built.
    */
   public void reloadPlaybackFPS() {
      if (display_ == null || display_.getDisplayModel() == null) {
         return;
      }
      animationFPS_ = display_.getDisplayModel().getPlaybackFPS();
      animationFpsLoaded_ = true;
      if (displayWindow_ != null) {
         displayWindow_.setPlaybackFPSControl(animationFPS_);
      }
      Timer timer = animationTimer_;
      if (timer != null && timer.isRunning()) {
         timer.setDelay(tickIntervalFromFPS(animationFPS_));
      }
   }

   /**
    * Converts a playback rate into a Swing Timer interval.  The interval only sets how
    * often playback is re-evaluated; how far each tick advances comes from elapsed time.
    */
   private static int tickIntervalFromFPS(double fps) {
      if (fps <= 0) {
         return MAX_TICK_MS;
      }
      long perFrameMs = Math.round(1000.0 / fps);
      return (int) Math.max(MIN_TICK_MS, Math.min(MAX_TICK_MS, perFrameMs));
   }

   public void displayOverlay(Overlay overlay) {
      displayWindow_.displayOverlay(overlay);
   }

   /**
    * Shows the overlay belonging to a particular render generation.
    *
    * @param overlay    the overlay to draw
    * @param generation render generation this overlay was computed for
    */
   public void displayOverlay(Overlay overlay, long generation) {
      if (displayWindow_ != null) {
         displayWindow_.displayOverlay(overlay, generation);
      }
   }

   /**
    * Records that the overlay for the given generation is in place, without
    * replacing it: used by overlayer plugins that install their own overlay.
    *
    * @param generation render generation whose overlay is now current
    */
   public void setPendingOverlayGeneration(long generation) {
      if (displayWindow_ != null) {
         displayWindow_.setPendingOverlayGeneration(generation);
      }
   }

   public void showScaleBar(boolean selected) {
      overlayer_.setShowScaleBar(selected);
   }

   public boolean isCompositeMode() {
      return display_.getDisplayModel().isCompositeMode();
   }

   public void shutdown() {
      displayWindow_.onDisplayClose();

      imageMaker_.close();
      imageMaker_ = null;

      overlayer_.shutdown();
      overlayer_ = null;

      if (animationTimer_ != null) {
         animationTimer_.stop();
      }
      animationTimer_ = null;
      displayWindow_ = null;
   }

   public void displayNewImage(Image img, HashMap<String, int[]> hists, DataViewCoords view,
                               TiledDataViewerOverlayerPlugin overlayerPlugin) {
      if (displayWindow_ == null || overlayer_ == null) {
         return; // shutdown() already ran; discard stale repaint
      }
      // The overlay is computed on its own thread and installed later; tag it
      // with this frame's generation so a render is only reported complete once
      // the two match.
      long generation = displayWindow_.displayImage(img, hists, view);
      overlayer_.createOverlay(view, overlayerPlugin, generation);
      displayWindow_.repaintCanvas();
   }

   public void setRenderSettings(Map<String, ChannelRenderSettings> channelSettings,
                                  GlobalRenderSettings globalSettings,
                                  ContrastUpdateCallback callback) {
      if (imageMaker_ != null) {
         imageMaker_.setRenderSettings(channelSettings, globalSettings, callback);
      }
   }

   public Image makeOrGetImage(DataViewCoords view) {
      return imageMaker_.makeOrGetImage(view);
   }

   public mmcorej.org.json.JSONObject getLatestTags() {
      return imageMaker_.getLatestTags();
   }

   public int[] getRenderedPixelRGB(int canvasX, int canvasY) {
      ViewerCanvas vc = getCanvas();
      if (vc == null) {
         return null;
      }
      return vc.getRenderedPixelRGB(canvasX, canvasY);
   }

   public HashMap<String, int[]> getHistograms() {
      return imageMaker_.getHistograms();
   }

   public HashMap<String, int[][]> getComponentHistograms() {
      return imageMaker_.getComponentHistograms();
   }

   public void expandDisplayedRangeToInclude(java.util.List<HashMap<String,
            Object>> newIamgeEvents, java.util.List<String> activeChannels) {
      if (displayWindow_ != null) {
         displayWindow_.expandDisplayedRangeToInclude(newIamgeEvents, activeChannels);
      }
   }

   public void setWindowActivatedCallback(Runnable callback) {
      if (displayWindow_ != null) {
         displayWindow_.setWindowActivatedCallback(callback);
      }
   }

   /**
    * Installs the gear button in the controls panel.
    *
    * @param viewer viewer whose gear menu this is
    * @param studio the Studio, used to discover gear menu plugins
    */
   public void installGearButton(org.micromanager.display.DataViewer viewer,
                                 org.micromanager.Studio studio) {
      if (displayWindow_ != null) {
         displayWindow_.installGearButton(viewer, studio);
      }
   }

   public void setPersistentMouseAdapter(java.awt.event.MouseAdapter adapter) {
      if (displayWindow_ != null) {
         displayWindow_.setPersistentMouseAdapter(adapter);
      }
   }

   public void setCustomCanvasMouseListener(TiledDataViewerCanvasMouseListenerInterface m) {
      if (displayWindow_ != null) {
         displayWindow_.setCustomCanvasMouseListener(m);
      }
   }

   public void resetCanvasMouseListener() {
      if (displayWindow_ != null) {
         displayWindow_.resetCanvasMouseListener();
      }
   }

   public void setShowZPosition(boolean selected) {
      overlayer_.setShowZPosition(selected);
   }

   public void setShowTimeLabel(boolean selected) {
      overlayer_.setShowTimeLabel(selected);
   }

   public void updateActiveChannelCheckboxes() {
      // No-op: side controls panel has been removed.
   }

   public void addContrastControlsIfNeeded(String channelName) {
      // No-op: side controls panel has been removed.
   }

   public void readHistogramControlsStateFromGUI() {
      // No-op: side controls panel has been removed.
   }

   public void updateGUIFromDisplaySettings() {
      // No-op: side controls panel has been removed.
   }
}
