package org.micromanager.plugins.isim;

import com.google.common.eventbus.Subscribe;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.data.DataProvider;
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.LiveModeEvent;
import org.micromanager.events.PropertyChangedEvent;

/**
 * Panel implementing alignment mode controls:
 * - Enter/exit alignment mode (adds reference-line overlay to the live view)
 * - Reference line parameters (angle, spacing, offset)
 * - Optional automatic spot detection
 */
public class AlignmentPanel extends JPanel {
   private static final String ENTER_LABEL = "Enter Alignment Mode";
   private static final String EXIT_LABEL = "Exit Alignment Mode";
   private static final long DETECTION_PERIOD_MS = 1000;

   private final Studio studio_;
   private final AlignmentModel model_;
   private final iSIMFrame frame_;
   private final String deviceLabel_;

   // Alignment state
   private boolean inAlignmentMode_ = false;
   private AlignmentOverlay overlay_;
   private DataProvider dataProvider_;
   private ScheduledExecutorService executor_;

   // Latest image from the live view, written by the EventBus thread.
   private volatile Image latestImage_;

   // Controls
   private final JButton alignmentModeButton_;
   private final JSpinner angleSpinner_;
   private final JSpinner spacingSpinner_;
   private final JSpinner offsetXSpinner_;
   private final JSpinner offsetYSpinner_;
   private final JCheckBox detectionCheckBox_;
   private final JSpinner thresholdSpinner_;
   private final JSpinner windowSpinner_;

   public AlignmentPanel(Studio studio, AlignmentModel model, iSIMFrame frame, String deviceLabel) {
      studio_ = studio;
      model_ = model;
      frame_ = frame;
      deviceLabel_ = deviceLabel;
      studio_.events().registerForEvents(this);

      setLayout(new MigLayout("fill, insets 8, gap 4"));

      alignmentModeButton_ = new JButton(ENTER_LABEL);
      alignmentModeButton_.addActionListener(e -> onAlignmentModeButtonClicked());
      add(alignmentModeButton_, "span, growx, wrap");

      // Reference lines section
      add(new JLabel("Reference Lines"), "span, gaptop 8, wrap");

      add(new JLabel("Angle (deg):"), "");
      angleSpinner_ = new JSpinner(new SpinnerNumberModel(
            model_.getAngleDeg(), -90.0, 90.0, 0.01));
      angleSpinner_.addChangeListener(e -> {
         model_.setAngleDeg((Double) angleSpinner_.getValue());
         repaintOverlay();
      });
      add(angleSpinner_, "width 80, wrap");

      add(new JLabel("Spacing (px):"), "");
      spacingSpinner_ = new JSpinner(new SpinnerNumberModel(
            model_.getSpacingPx(), 1, 9999, 1));
      spacingSpinner_.addChangeListener(e -> {
         model_.setSpacingPx((Integer) spacingSpinner_.getValue());
         repaintOverlay();
      });
      add(spacingSpinner_, "width 80, wrap");

      add(new JLabel("Offset X (px):"), "");
      offsetXSpinner_ = new JSpinner(new SpinnerNumberModel(
            model_.getOffsetX(), -9999, 9999, 1));
      offsetXSpinner_.addChangeListener(e -> {
         model_.setOffsetX((Integer) offsetXSpinner_.getValue());
         repaintOverlay();
      });
      add(offsetXSpinner_, "width 80");

      add(new JLabel("Offset Y (px):"), "");
      offsetYSpinner_ = new JSpinner(new SpinnerNumberModel(
            model_.getOffsetY(), -9999, 9999, 1));
      offsetYSpinner_.addChangeListener(e -> {
         model_.setOffsetY((Integer) offsetYSpinner_.getValue());
         repaintOverlay();
      });
      add(offsetYSpinner_, "width 80, wrap");

      // Spot detection section
      add(new JLabel("Spot Detection"), "span, gaptop 8, wrap");

      detectionCheckBox_ = new JCheckBox(
            "Enable automatic spot detection", model_.isDetectionEnabled());
      detectionCheckBox_.addActionListener(e -> {
         model_.setDetectionEnabled(detectionCheckBox_.isSelected());
         if (inAlignmentMode_) {
            if (model_.isDetectionEnabled()) {
               startDetection();
            } else {
               stopDetection();
               if (overlay_ != null) {
                  overlay_.updateSpots(new ArrayList<>());
               }
            }
         }
      });
      add(detectionCheckBox_, "span, wrap");

      add(new JLabel("Threshold:"), "");
      thresholdSpinner_ = new JSpinner(new SpinnerNumberModel(
            model_.getThreshold(), 0, 65535, 10));
      thresholdSpinner_.addChangeListener(e ->
            model_.setThreshold((Integer) thresholdSpinner_.getValue()));
      add(thresholdSpinner_, "width 80");

      add(new JLabel("Window (px):"), "");
      windowSpinner_ = new JSpinner(new SpinnerNumberModel(
            model_.getWindowPx(), 3, 200, 1));
      windowSpinner_.addChangeListener(e ->
            model_.setWindowPx((Integer) windowSpinner_.getValue()));
      add(windowSpinner_, "width 80, wrap");
   }

   /**
    * Reads the current device state and updates the UI to match.
    * Called once after the parent frame is fully constructed.
    */
   void syncWithDeviceState() {
      try {
         String value = studio_.core().getProperty(deviceLabel_, "Alignment Mode Enabled");
         if ("Yes".equals(value) && !inAlignmentMode_) {
            inAlignmentMode_ = true;
            alignmentModeButton_.setText(EXIT_LABEL);
            frame_.setStatus(true);
            if (studio_.live().getDisplay() != null) {
               finishEnteringAlignmentMode();
            }
            // Otherwise onLiveMode() handles it when the user starts live view.
         }
      } catch (Exception e) {
         studio_.logs().logError(e);
      }
   }

   private void onAlignmentModeButtonClicked() {
      if (inAlignmentMode_) {
         exitAlignmentMode();
      } else {
         enterAlignmentMode();
      }
   }

   private void enterAlignmentMode() {
      boolean needsToStart = !studio_.live().isLiveModeOn();
      if (needsToStart) {
         studio_.live().setLiveModeOn(true);
         waitForDisplayAndFinish();
      } else {
         finishEnteringAlignmentMode();
      }
   }

   /**
    * Polls on a background thread until the live display exists, then calls
    * finishEnteringAlignmentMode() on the EDT. Used when live mode is started
    * but the display is not yet available.
    *
    * Display creation goes: scheduler thread → invokeAndWait → EDT createDisplay().
    * A single invokeLater doesn't work because the display doesn't exist yet.
    */
   private void waitForDisplayAndFinish() {
      Thread t = new Thread(() -> {
         long deadline = System.currentTimeMillis() + 5000;
         while (System.currentTimeMillis() < deadline) {
            if (studio_.live().getDisplay() != null) {
               SwingUtilities.invokeLater(this::finishEnteringAlignmentMode);
               return;
            }
            try {
               Thread.sleep(20);
            } catch (InterruptedException ex) {
               Thread.currentThread().interrupt();
               return;
            }
         }
         SwingUtilities.invokeLater(() ->
               studio_.alerts().postAlert("iSIM", AlignmentPanel.class,
                     "Timed out waiting for live view to start. "
                     + "Please ensure a camera is configured."));
      }, "iSIM-wait-for-display");
      t.setDaemon(true);
      t.start();
   }

   private void finishEnteringAlignmentMode() {
      DisplayWindow dw = studio_.live().getDisplay();
      if (dw == null) {
         studio_.alerts().postAlert("iSIM", AlignmentPanel.class,
               "Could not obtain live view display. "
               + "Please ensure a camera is configured and snap an image first.");
         return;
      }

      overlay_ = new AlignmentOverlay(model_);
      dw.addOverlay(overlay_);

      dataProvider_ = dw.getDataProvider();
      dataProvider_.registerForEvents(this);

      if (model_.isDetectionEnabled()) {
         startDetection();
      }

      inAlignmentMode_ = true;
      alignmentModeButton_.setText(EXIT_LABEL);
      frame_.setStatus(true);
      try {
         studio_.core().setProperty(deviceLabel_, "Alignment Mode Enabled", "Yes");
      } catch (Exception e) {
         studio_.logs().logError(e);
      }
   }

   private void exitAlignmentMode() {
      stopDetection();

      if (dataProvider_ != null) {
         try {
            dataProvider_.unregisterForEvents(this);
         } catch (Exception ex) {
            // Display may have closed already; ignore.
         }
         dataProvider_ = null;
      }

      DisplayWindow dw = studio_.live().getDisplay();
      if (dw != null && overlay_ != null) {
         dw.removeOverlay(overlay_);
      }
      overlay_ = null;
      latestImage_ = null;

      inAlignmentMode_ = false;
      alignmentModeButton_.setText(ENTER_LABEL);
      frame_.setStatus(false);
      try {
         studio_.core().setProperty(deviceLabel_, "Alignment Mode Enabled", "No");
      } catch (Exception e) {
         studio_.logs().logError(e);
      }
   }

   private void startDetection() {
      if (executor_ != null && !executor_.isShutdown()) {
         return;
      }
      executor_ = Executors.newSingleThreadScheduledExecutor(r -> {
         Thread t = new Thread(r, "iSIM-spot-detection");
         t.setDaemon(true);
         return t;
      });
      executor_.scheduleAtFixedRate(
            this::runDetection, 0, DETECTION_PERIOD_MS, TimeUnit.MILLISECONDS);
   }

   private void stopDetection() {
      if (executor_ != null) {
         executor_.shutdownNow();
         executor_ = null;
      }
   }

   private void runDetection() {
      Image img = latestImage_;
      if (img == null) {
         return;
      }
      List<Point2D.Double> peaks = findLocalMaxima(img);
      SwingUtilities.invokeLater(() -> {
         if (overlay_ != null) {
            overlay_.updateSpots(peaks);
         }
      });
   }

   /**
    * Called from the DataProvider's EventBus thread; just stores the latest
    * image for the detection task to pick up.
    */
   @Subscribe
   public void onNewImage(DataProviderHasNewImageEvent event) {
      latestImage_ = event.getImage();
   }

   /**
    * Finds local maxima in the image above the configured threshold.
    * A pixel is a local maximum if it is strictly greater than all pixels
    * within the configured window size.
    */
   private List<Point2D.Double> findLocalMaxima(Image image) {
      int width = image.getWidth();
      int height = image.getHeight();
      int halfWindow = model_.getWindowPx() / 2;
      int threshold = model_.getThreshold();

      int[] pixels = toIntArray(image);
      List<Point2D.Double> peaks = new ArrayList<>();

      for (int y = halfWindow; y < height - halfWindow; y++) {
         for (int x = halfWindow; x < width - halfWindow; x++) {
            int val = pixels[y * width + x];
            if (val < threshold) {
               continue;
            }
            if (isLocalMax(pixels, x, y, width, halfWindow, val)) {
               peaks.add(new Point2D.Double(x, y));
            }
         }
      }
      return peaks;
   }

   private boolean isLocalMax(int[] pixels, int cx, int cy,
         int width, int halfWindow, int centerVal) {
      for (int dy = -halfWindow; dy <= halfWindow; dy++) {
         for (int dx = -halfWindow; dx <= halfWindow; dx++) {
            if (dx == 0 && dy == 0) {
               continue;
            }
            if (pixels[(cy + dy) * width + (cx + dx)] >= centerVal) {
               return false;
            }
         }
      }
      return true;
   }

   /**
    * Converts raw image pixels to an unsigned int array regardless of bit depth.
    */
   private int[] toIntArray(Image image) {
      Object raw = image.getRawPixels();
      int n = image.getWidth() * image.getHeight();
      int[] result = new int[n];
      if (raw instanceof short[]) {
         short[] shorts = (short[]) raw;
         for (int i = 0; i < n; i++) {
            result[i] = shorts[i] & 0xFFFF;
         }
      } else if (raw instanceof byte[]) {
         byte[] bytes = (byte[]) raw;
         for (int i = 0; i < n; i++) {
            result[i] = bytes[i] & 0xFF;
         }
      }
      return result;
   }

   /**
    * Triggers an overlay repaint when reference line parameters change.
    */
   private void repaintOverlay() {
      if (overlay_ != null) {
         overlay_.notifyChanged();
      }
   }

   /**
    * When live mode turns on while we are in alignment mode but the overlay is not
    * yet attached (i.e. the window was opened while the device was already in
    * alignment mode), wait for the display to appear and finish the setup.
    */
   @Subscribe
   public void onLiveMode(LiveModeEvent event) {
      if (!event.isOn()) {
         return;
      }
      if (!inAlignmentMode_ || overlay_ != null) {
         return;
      }
      waitForDisplayAndFinish();
   }

   /**
    * Responds to device property changes from the studio event bus.
    * Keeps the alignment mode state in sync with the iSIMWaveforms device property.
    */
   @Subscribe
   public void onPropertyChanged(PropertyChangedEvent event) {
      if (!event.getDevice().equals(deviceLabel_)) {
         return;
      }
      if (!event.getProperty().equals("Alignment Mode Enabled")) {
         return;
      }
      boolean requested = "Yes".equals(event.getValue());
      if (requested == inAlignmentMode_) {
         return;
      }
      SwingUtilities.invokeLater(() -> {
         if (requested) {
            enterAlignmentMode();
         } else {
            exitAlignmentMode();
         }
      });
   }

   /**
    * Called by the parent frame when the window is closing.
    */
   public void onWindowClosing() {
      if (inAlignmentMode_) {
         exitAlignmentMode();
      }
      studio_.events().unregisterForEvents(this);
      model_.save();
   }
}
