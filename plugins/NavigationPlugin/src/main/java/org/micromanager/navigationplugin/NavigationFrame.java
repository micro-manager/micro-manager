/**
 * NavigationFrame - Main UI window for the Navigation Plugin
 *
 * Provides controls for loading reference images, managing calibration points,
 * and navigating the microscope stage.
 *
 * LICENSE:      This file is distributed under the BSD license.
 */

package org.micromanager.navigationplugin;

import com.google.common.eventbus.Subscribe;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import mmcorej.CMMCore;
import org.micromanager.Studio;
import org.micromanager.events.XYStagePositionChangedEvent;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;

public class NavigationFrame extends JFrame {

   private static final String LAST_IMAGE_PATH_KEY = "lastImagePath";
   private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(
         Arrays.asList("jpg", "jpeg", "png", "tif", "tiff", "bmp", "gif"));

   private final Studio studio_;
   private final NavigationState state_;
   private final ImagePanel imagePanel_;
   private final ExecutorService executorService_;
   private final MutablePropertyMapView settings_;

   private JLabel statusLabel_;
   private JLabel pointCountLabel_;
   private JLabel stagePositionLabel_;
   private JButton clearButton_;

   private String currentImagePath_;

   public NavigationFrame(Studio studio) {
      super("Navigation Plugin");
      this.studio_ = studio;
      this.state_ = new NavigationState();
      this.imagePanel_ = new ImagePanel(state_);
      this.executorService_ = Executors.newSingleThreadExecutor();
      this.settings_ = studio_.profile().getSettings(this.getClass());
      this.currentImagePath_ = null;

      // Set the Micro-Manager icon
      super.setIconImage(Toolkit.getDefaultToolkit().getImage(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));

      initializeUI();
      setupEventHandlers();
      setupDragAndDrop();

      // Register for events
      studio_.events().registerForEvents(this);

      setSize(900, 700);
      setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

      // Setup window position memory
      WindowPositioning.setUpBoundsMemory(this, this.getClass(), null);
   }

   private void initializeUI() {
      setLayout(new BorderLayout(10, 10));

      // Top control panel
      JPanel controlPanel = createControlPanel();
      add(controlPanel, BorderLayout.NORTH);

      // Center image panel
      add(imagePanel_, BorderLayout.CENTER);

      // Bottom status panel
      JPanel statusPanel = createStatusPanel();
      add(statusPanel, BorderLayout.SOUTH);
   }

   private JPanel createControlPanel() {
      JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
      panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

      JButton loadButton = new JButton("Load Reference Image");
      loadButton.addActionListener(e -> loadReferenceImage());
      panel.add(loadButton);

      clearButton_ = new JButton("Clear Calibration Points");
      clearButton_.addActionListener(e -> clearCalibrationPoints());
      clearButton_.setEnabled(false);
      panel.add(clearButton_);

      return panel;
   }

   private JPanel createStatusPanel() {
      JPanel panel = new JPanel(new GridLayout(3, 1, 5, 5));
      panel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

      statusLabel_ = new JLabel("Status: No image loaded");
      pointCountLabel_ = new JLabel("Calibration Points: 0 (need at least 3)");
      stagePositionLabel_ = new JLabel("Stage Position: --");

      panel.add(statusLabel_);
      panel.add(pointCountLabel_);
      panel.add(stagePositionLabel_);

      return panel;
   }

   private void setupEventHandlers() {
      imagePanel_.setClickListener(this::handleImageClick);
   }

   private void loadReferenceImage() {
      JFileChooser fileChooser = new JFileChooser();
      fileChooser.setDialogTitle("Select Reference Image");
      FileNameExtensionFilter filter = new FileNameExtensionFilter(
            "Image files", "jpg", "jpeg", "png", "tif", "tiff", "bmp", "gif");
      fileChooser.setFileFilter(filter);

      // Set starting directory from last opened image path
      String lastImagePath = settings_.getString(LAST_IMAGE_PATH_KEY, null);
      if (lastImagePath != null) {
         File lastImageFile = new File(lastImagePath);
         File parentDir = lastImageFile.getParentFile();
         if (parentDir != null && parentDir.exists()) {
            fileChooser.setCurrentDirectory(parentDir);
         }
      }

      int result = fileChooser.showOpenDialog(this);
      if (result == JFileChooser.APPROVE_OPTION) {
         loadImageFile(fileChooser.getSelectedFile());
      }
   }

   /**
    * Load an image file as the reference image.
    * This method is called both from the file chooser and from drag-and-drop.
    *
    * @param file the image file to load
    */
   private void loadImageFile(File file) {
      try {
         BufferedImage image = ImageIO.read(file);
         if (image == null) {
            showError("Failed to load image. The file may not be a valid image format.");
            return;
         }

         currentImagePath_ = file.getAbsolutePath();
         state_.setReferenceImage(image);
         state_.clearAllPoints();

         // Save last opened image path to profile
         settings_.putString(LAST_IMAGE_PATH_KEY, currentImagePath_);

         // Try to load previously saved calibration for this image
         loadCalibrationFromProfile();

         imagePanel_.repaint();
         updateStatusDisplay();

         studio_.logs().logMessage("Loaded reference image: " + file.getName());

      } catch (IOException ex) {
         showError("Error loading image: " + ex.getMessage());
         studio_.logs().logError(ex);
      }
   }

   /**
    * Check if a file has a supported image extension.
    *
    * @param file the file to check
    * @return true if the file extension is supported
    */
   private boolean isSupportedImageFile(File file) {
      String name = file.getName().toLowerCase();
      int dotIndex = name.lastIndexOf('.');
      if (dotIndex < 0) {
         return false;
      }
      String extension = name.substring(dotIndex + 1);
      return SUPPORTED_EXTENSIONS.contains(extension);
   }

   /**
    * Setup drag and drop support for loading reference images.
    */
   private void setupDragAndDrop() {
      DropTargetListener dropListener = new DropTargetListener() {
         @Override
         public void dragEnter(DropTargetDragEvent dtde) {
            if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
               dtde.acceptDrag(DnDConstants.ACTION_COPY);
            } else {
               dtde.rejectDrag();
            }
         }

         @Override
         public void dragOver(DropTargetDragEvent dtde) {
            // No action needed
         }

         @Override
         public void dropActionChanged(DropTargetDragEvent dtde) {
            // No action needed
         }

         @Override
         public void dragExit(DropTargetEvent dte) {
            // No action needed
         }

         @Override
         @SuppressWarnings("unchecked")
         public void drop(DropTargetDropEvent dtde) {
            try {
               if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                  dtde.rejectDrop();
                  return;
               }

               dtde.acceptDrop(DnDConstants.ACTION_COPY);
               Transferable transferable = dtde.getTransferable();
               List<File> files = (List<File>) transferable
                     .getTransferData(DataFlavor.javaFileListFlavor);

               if (files.isEmpty()) {
                  dtde.dropComplete(false);
                  return;
               }

               // Take only the first file
               File file = files.get(0);

               if (!isSupportedImageFile(file)) {
                  showError("Unsupported file type. Please drop an image file "
                        + "(jpg, jpeg, png, tif, tiff, bmp, gif).");
                  dtde.dropComplete(false);
                  return;
               }

               loadImageFile(file);
               dtde.dropComplete(true);

            } catch (Exception ex) {
               studio_.logs().logError(ex, "Error handling dropped file");
               dtde.dropComplete(false);
            }
         }
      };

      new DropTarget(this, dropListener);
   }

   private void clearCalibrationPoints() {
      int result = JOptionPane.showConfirmDialog(this,
            "Clear all calibration points?",
            "Confirm Clear",
            JOptionPane.YES_NO_OPTION);

      if (result == JOptionPane.YES_OPTION) {
         state_.clearAllPoints();

         // Clear calibration from profile
         if (currentImagePath_ != null) {
            clearCalibrationFromProfile();
         }

         imagePanel_.repaint();
         updateStatusDisplay();
         studio_.logs().logMessage("Cleared all calibration points");
      }
   }

   private void handleImageClick(Point2D.Double imageCoord) {
      if (state_.isCalibrated()) {
         // Navigation mode - move stage to clicked location
         navigateToImageLocation(imageCoord);
      } else {
         // Calibration mode - record correspondence point
         recordCalibrationPoint(imageCoord);
      }
   }

   private void recordCalibrationPoint(Point2D.Double imageCoord) {
      try {
         CMMCore core = studio_.getCMMCore();

         // Check if XY stage is available
         String xyStage = core.getXYStageDevice();
         if (xyStage == null || xyStage.isEmpty()) {
            showError("No XY stage device configured. Please configure an XY stage in the Hardware Configuration Wizard.");
            return;
         }

         // Get current stage position
         double stageX = core.getXPosition(xyStage);
         double stageY = core.getYPosition(xyStage);
         Point2D.Double stageCoord = new Point2D.Double(stageX, stageY);

         // Add calibration point
         state_.addCalibrationPoint(imageCoord, stageCoord);

         studio_.logs().logMessage(String.format(
               "Added calibration point %d: Image(%.1f, %.1f) -> Stage(%.2f, %.2f)",
               state_.getPointCount(), imageCoord.x, imageCoord.y, stageX, stageY));

         // Save calibration to profile
         saveCalibrationToProfile();

         // Update display
         imagePanel_.repaint();
         updateStatusDisplay();

         // Check if transform calculation failed
         if (state_.getPointCount() >= 3 && !state_.isCalibrated()) {
            showError("Could not calculate transformation. Points may be collinear. " +
                  "Please ensure calibration points are not in a straight line.");
         }

      } catch (Exception ex) {
         showError("Error recording calibration point: " + ex.getMessage());
         studio_.logs().logError(ex);
      }
   }

   private void navigateToImageLocation(Point2D.Double imageCoord) {
      Point2D.Double stageTarget = state_.imageToStage(imageCoord);
      if (stageTarget == null) {
         return;
      }

      studio_.logs().logMessage(String.format(
            "Navigating to: Image(%.1f, %.1f) -> Stage(%.2f, %.2f)",
            imageCoord.x, imageCoord.y, stageTarget.x, stageTarget.y));

      // Move stage asynchronously to avoid blocking UI
      executorService_.submit(() -> {
         try {
            CMMCore core = studio_.getCMMCore();
            String xyStage = core.getXYStageDevice();

            core.setXYPosition(stageTarget.x, stageTarget.y);
            core.waitForDevice(xyStage);

            // Update UI on EDT
            SwingUtilities.invokeLater(() -> {
               updateStagePosition();
               imagePanel_.repaint();
            });

         } catch (Exception ex) {
            SwingUtilities.invokeLater(() -> {
               showError("Failed to move stage: " + ex.getMessage());
               studio_.logs().logError(ex);
            });
         }
      });
   }

   private void updateStatusDisplay() {
      NavigationState.Mode mode = state_.getCurrentMode();
      int pointCount = state_.getPointCount();

      switch (mode) {
         case NO_IMAGE:
            statusLabel_.setText("Status: No image loaded");
            pointCountLabel_.setText("Calibration Points: 0 (need at least 3)");
            clearButton_.setEnabled(false);
            break;

         case CALIBRATING:
            if (pointCount == 0) {
               statusLabel_.setText("Status: Calibrating - Move stage to a feature, then click the corresponding point in the image");
            } else {
               statusLabel_.setText(String.format("Status: Calibrating - Add %d more point(s) to enable navigation",
                     Math.max(0, 3 - pointCount)));
            }
            pointCountLabel_.setText(String.format("Calibration Points: %d (need at least 3)", pointCount));
            clearButton_.setEnabled(pointCount > 0);
            break;

         case CALIBRATED:
            statusLabel_.setText("Status: Calibrated - Click anywhere on the image to move the stage");
            pointCountLabel_.setText(String.format("Calibration Points: %d (add more to improve accuracy)", pointCount));
            clearButton_.setEnabled(true);
            break;
      }

      updateStagePosition();
   }

   private void updateStagePosition() {
      try {
         CMMCore core = studio_.getCMMCore();
         String xyStage = core.getXYStageDevice();

         if (xyStage != null && !xyStage.isEmpty()) {
            double x = core.getXPosition(xyStage);
            double y = core.getYPosition(xyStage);
            stagePositionLabel_.setText(String.format("Stage Position: (%.2f, %.2f) \u00b5m", x, y));

            // Update stage position indicator in image panel
            imagePanel_.setCurrentStagePosition(new Point2D.Double(x, y));
         } else {
            stagePositionLabel_.setText("Stage Position: No XY stage configured");
         }
      } catch (Exception ex) {
         stagePositionLabel_.setText("Stage Position: Error reading position");
      }
   }

   /**
    * Event handler for XY stage position changes
    */
   @Subscribe
   public void onXYStagePositionChanged(XYStagePositionChangedEvent event) {
      SwingUtilities.invokeLater(() -> {
         double x = event.getXPos();
         double y = event.getYPos();
         stagePositionLabel_.setText(String.format("Stage Position: (%.2f, %.2f) \u00b5m", x, y));

         // Update stage position indicator in image panel
         imagePanel_.setCurrentStagePosition(new Point2D.Double(x, y));
      });
   }

   private void showError(String message) {
      JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
   }

   /**
    * Save calibration points to the user profile
    */
   private void saveCalibrationToProfile() {
      if (currentImagePath_ == null) {
         return;
      }

      List<CalibrationPoint> points = state_.getCalibrationPoints();
      if (points.isEmpty()) {
         return;
      }

      // Create keys based on the image path
      String imageKey = makeImageKey(currentImagePath_);
      String imageCoordXKey = imageKey + "_imageX";
      String imageCoordYKey = imageKey + "_imageY";
      String stageCoordXKey = imageKey + "_stageX";
      String stageCoordYKey = imageKey + "_stageY";

      // Build lists of coordinates
      List<Double> imageXList = new ArrayList<>();
      List<Double> imageYList = new ArrayList<>();
      List<Double> stageXList = new ArrayList<>();
      List<Double> stageYList = new ArrayList<>();

      for (CalibrationPoint point : points) {
         imageXList.add(point.getImageCoord().x);
         imageYList.add(point.getImageCoord().y);
         stageXList.add(point.getStageCoord().x);
         stageYList.add(point.getStageCoord().y);
      }

      // Save to profile
      settings_.putString(imageKey + "_path", currentImagePath_);
      settings_.putDoubleList(imageCoordXKey, imageXList);
      settings_.putDoubleList(imageCoordYKey, imageYList);
      settings_.putDoubleList(stageCoordXKey, stageXList);
      settings_.putDoubleList(stageCoordYKey, stageYList);

      studio_.logs().logMessage("Saved calibration to profile for: " + currentImagePath_);
   }

   /**
    * Load calibration points from the user profile
    */
   private void loadCalibrationFromProfile() {
      if (currentImagePath_ == null) {
         return;
      }

      String imageKey = makeImageKey(currentImagePath_);
      String imageCoordXKey = imageKey + "_imageX";
      String imageCoordYKey = imageKey + "_imageY";
      String stageCoordXKey = imageKey + "_stageX";
      String stageCoordYKey = imageKey + "_stageY";

      // Check if calibration exists for this image
      if (!settings_.containsKey(imageCoordXKey)) {
         return;
      }

      try {
         // Load coordinate lists
         List<Double> imageXList = new ArrayList<>();
         List<Double> imageYList = new ArrayList<>();
         List<Double> stageXList = new ArrayList<>();
         List<Double> stageYList = new ArrayList<>();

         for (Double val : settings_.getDoubleList(imageCoordXKey)) {
            imageXList.add(val);
         }
         for (Double val : settings_.getDoubleList(imageCoordYKey)) {
            imageYList.add(val);
         }
         for (Double val : settings_.getDoubleList(stageCoordXKey)) {
            stageXList.add(val);
         }
         for (Double val : settings_.getDoubleList(stageCoordYKey)) {
            stageYList.add(val);
         }

         // Verify all lists have the same length
         if (imageXList.size() != imageYList.size() ||
             imageXList.size() != stageXList.size() ||
             imageXList.size() != stageYList.size()) {
            studio_.logs().showError("Calibration data corrupted for this image");
            return;
         }

         // Restore calibration points
         for (int i = 0; i < imageXList.size(); i++) {
            Point2D.Double imageCoord = new Point2D.Double(imageXList.get(i), imageYList.get(i));
            Point2D.Double stageCoord = new Point2D.Double(stageXList.get(i), stageYList.get(i));
            state_.addCalibrationPoint(imageCoord, stageCoord);
         }

         studio_.logs().logMessage(String.format("Loaded %d calibration points from profile",
               imageXList.size()));

      } catch (Exception ex) {
         studio_.logs().showError(ex, "Error loading calibration from profile");
      }
   }

   /**
    * Clear calibration from the user profile
    */
   private void clearCalibrationFromProfile() {
      if (currentImagePath_ == null) {
         return;
      }

      String imageKey = makeImageKey(currentImagePath_);
      List<Double> emptyList = new ArrayList<>();
      settings_.putString(imageKey + "_path", "");
      settings_.putDoubleList(imageKey + "_imageX", emptyList);
      settings_.putDoubleList(imageKey + "_imageY", emptyList);
      settings_.putDoubleList(imageKey + "_stageX", emptyList);
      settings_.putDoubleList(imageKey + "_stageY", emptyList);
   }

   /**
    * Create a safe key for profile storage from an image path
    */
   private String makeImageKey(String imagePath) {
      // Use a hash of the absolute path to create a unique but safe key
      return "calibration_" + Integer.toHexString(imagePath.hashCode());
   }

   @Override
   public void setVisible(boolean visible) {
      super.setVisible(visible);
      if (visible) {
         updateStatusDisplay();
      }
   }

   @Override
   public void dispose() {
      studio_.events().unregisterForEvents(this);
      executorService_.shutdown();
      super.dispose();
   }
}
