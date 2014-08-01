package org.micromanager.slideexplorer;

import ij.process.ImageProcessor;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;

import java.util.Hashtable;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import mmcorej.CMMCore;

import mmcorej.StrVector;
import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import org.micromanager.pixelcalibrator.PixelCalibratorPlugin;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

public class Hub {
   public static ScriptInterface appRef_;

   Controller controller_;
   MultiTileCache cache_;
   Dimension tileDimensions_;
   Display display_;
   Coordinates coords_;
   ModeManager modeMgr_;
   int zoomLevel_ = 0;
   int numZoomLevels_ = 8;
   public boolean stopTileGrabberThread_;
   private ImageProcessor blackImg_;
   private TileGrabberThread tgt_;
   private boolean running_ = false;
   private CMMCore core_;
   private ConfigurationDialog configDialog_;
   private static String angleKey = "angle";
   private static String pixelSizeKey = "pixelSize";
   private double angle_;
   private double pixelSize_;
   private final Preferences prefs_;
   private final MMStudio app_;
   private RoiManager roiManager_;
   private String surveyPixelSizeConfig_ = "";
   private String navigatePixelSizeConfig_ = "";
   private String currentPixelSizeConfig_ = "None";
   private Hashtable<String, OffsetsRow> offsetsData_ = new Hashtable<String, OffsetsRow>();
   private double autofocusOffset_ = 0;
//   private AcqControlDlgMosaic mosaicDlg_ = null;

   /*
    * Hub constructor.
    */
   public Hub(ScriptInterface app) {
      app_ = (MMStudio) app;
      Hub.appRef_ = app;
      core_ = app_.getMMCore();
      prefs_ = Preferences.systemNodeForPackage(this.getClass());
      angle_ = prefs_.getDouble(angleKey, 0.0);
      //pixelSize_ = prefs_.getDouble(pixelSizeKey, core.getPixelSizeUm());
      pixelSize_ = core_.getPixelSizeUm();


      int width = 950;
      int height = 600;

      applyVendorSpecificSettings();

      controller_ = new Controller(core_);

      AffineTransform transform = getCurrentAffineTransform(core_);

      if (transform != null) {
         controller_.specifyMapRelativeToStage(transform);
      } else {
         return;
      }

      int type = controller_.getImageType();
      tileDimensions_ = controller_.getTileDimensions();

      cache_ = new MultiTileCache(numZoomLevels_, tileDimensions_);

      blackImg_ = ImageUtils.makeProcessor(type, tileDimensions_.width, tileDimensions_.height);

      coords_ = new Coordinates();
      coords_.setTileDimensionsOnMap(tileDimensions_);
      coords_.setRoiDimensionsOnMap(controller_.getCurrentRoiDimensions());

      resize(new Dimension(width, height));

      display_ = new Display(this, type, width, height);
      display_.setCoords(coords_);

      modeMgr_ = new ModeManager();
      deployRoiManager();

      startupOffsets();

      configDialog_ = new ConfigurationDialog(core_, this);

      start();
   }


   public static AffineTransform getCurrentAffineTransform(CMMCore core) {
      Preferences prefs = Preferences.userNodeForPackage(MMStudio.class);

      AffineTransform transform = null;
      try {
         transform = JavaUtils.getObjectFromPrefs(prefs, "affine_transform_" + core.getCurrentPixelSizeConfig(), (AffineTransform) null);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }


      if (transform == null) {
         int result = JOptionPane.showConfirmDialog(null,
                 "The current magnification setting needs to be calibrated.\n" +
                 "Would you like to run automatic pixel calibration?",
                 "Pixel caliberatrion required.",
                 JOptionPane.YES_NO_OPTION);
         if (result == JOptionPane.YES_OPTION) {
            try {
               PixelCalibratorPlugin pc = new PixelCalibratorPlugin();
               pc.setApp(Hub.appRef_);
               pc.show();
            } catch (Exception ex) {
               ReportingUtils.showError("Unable to load Pixel Calibrator Plugin.");
            }
         }
      }

      return transform;
   }

   public void applyVendorSpecificSettings() {
      // Keep ASI stages from being too slow.
      String stage = core_.getXYStageDevice();
      try {
         if (core_.hasProperty(stage, "Description")) {
            String stageDescription = core_.getProperty(stage, "Description");
            if (stageDescription.contains("ASI XY")) {
               if (core_.hasProperty(stage, "Error-E(nm)")) {
                  core_.setProperty(stage, "Error-E(nm)", "500");
               }
               if (core_.hasProperty(stage, "FinishError-PCROS(nm)")) {
                  core_.setProperty(stage, "FinishError-PCROS(nm)", "500");
               }
            }
         }
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   public void start() {


      if (!running_) {
         controller_.setMapOriginToCurrentStagePosition();
         coords_.setViewCenterInMap(new Point(0, 0));
         display_.fillWithBlack();
         stopTileGrabberThread_ = false;
         //modeMgr_.setMode(ModeManager.SURVEY);
         tgt_ = new TileGrabberThread();
         tgt_.start();
         running_ = true;
         survey();
      }
   }

   public void stop() {
      if (running_) {
         stopTileGrabberThread_ = true;
         try {
            tgt_.join();
         } catch (InterruptedException e) {
            ReportingUtils.showError(e);
         }
         cache_.clear();
         running_ = false;
      }
   }

   public void shutdown() {
      if (configDialog_ != null) {
         configDialog_.dispose();
      }
      stop();
   }

   // Methods called when the view changes:

   /*
    * The size of the display changed.
    */
   public void resize(Dimension newViewDimensionsOnScreen) {
      coords_.setViewDimensionsOnScreen(newViewDimensionsOnScreen);
      coords_.setViewDimensionsOffScreen(new Dimension(3 * newViewDimensionsOnScreen.width, 3 * newViewDimensionsOnScreen.height));
   }


   /*
    * Move view by some displacement dx, dy.
    */
   public void panBy(int dx, int dy) {
      panTo(new Point(-dx, -dy));
   }

   /*
    * Move center of view to some target offScreen.
    */
   public void panTo(Point panTargetOffScreen) {
      coords_.panTo(panTargetOffScreen);
      updateView();
   }

   /*
    * Zoom relative to center of view.
    */
   public void zoomBy(int zoomStep /* +-1 */) {
      zoomBy(zoomStep, new Point(0, 0));
   }

   /*
    * Zoom relative to some point offScreen.
    */
   public void zoomBy(int zoomStep /* +-1 */, Point zoomTargetOffScreen) {
      zoomTo(zoomLevel_ + zoomStep, zoomTargetOffScreen);

   }

   public void zoomTo(int zoomLevel, Point zoomTargetOffScreen) {
      int originalZoomLevel = zoomLevel_;
      zoomLevel_ = zoomLevel;

      if (zoomLevel_ > 0) {
         zoomLevel_ = 0;
      }
      if (zoomLevel_ <= -numZoomLevels_) {
         zoomLevel_ = -numZoomLevels_ + 1;
      }

      if (zoomLevel_ != originalZoomLevel) {
         if (zoomLevel_ > originalZoomLevel) {
            coords_.zoomIn(zoomTargetOffScreen);
         } else {
            coords_.zoomOut(zoomTargetOffScreen);
         }
         updateView();
         display_.update();
      }
   }

   public void updateView() {
      SwingUtilities.invokeLater(new GUIUpdater(null));
   }

   protected void finalize() throws Throwable {
      configDialog_.dispose();
      shutdown();
      super.finalize();
   }

   public void navigate(Point offScreenPos) {
      tgt_.navigate(coords_.offScreenClickToMap(offScreenPos));
      display_.update();
   }

   public void survey() {
      tgt_.survey();
      display_.update();
   }

   public void pauseSlideExplorer() {
      tgt_.pauseSlideExplorer();
      display_.update();
   }

   public void writeOffsets() {
   }

   void showConfig() {
      configDialog_.setVisible(true);
   }

   void deployRoiManager() {
      roiManager_ = RoiManager.getInstance();
      if (roiManager_ == null) {
         roiManager_ = new RoiManager(this);
      } else {
         roiManager_.setHub(this);
      }

      roiManager_.setVisible(false);
      display_.setRoiManager(roiManager_);
   }

   ScriptInterface getApp() {
      return app_;
   }

   public Dimension getTileDimensions() {
      return tileDimensions_;
   }

   public Dimension getRoiDimensionsOnMap() {
      return coords_.getRoiDimensionsOnMap();
   }

   Coordinates getCoordinates() {
      return coords_;
   }

   Controller getController() {
      return controller_;
   }

   public int getMode() {
      return modeMgr_.getMode();
   }

   void acquireMosaics() {
      roiManager_.updateMappings();
      try {
         app_.setPositionList(roiManager_.convertRoiManagerToPositionList());
         app_.showXYPositionList();
      } catch (MMScriptException ex) {
         ex.printStackTrace();
      }
   }

   int getZoomLevel() {
      return zoomLevel_;
   }

   public void startupOffsets() {
      loadOffsets();
      try {
         surveyPixelSizeConfig_ = core_.getCurrentPixelSizeConfig();
         if (surveyPixelSizeConfig_.length() == 0) {
            return;
         }
         navigatePixelSizeConfig_ = surveyPixelSizeConfig_;
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
      setOffsets(surveyPixelSizeConfig_);
   }

   public Hashtable<String, OffsetsRow> getOffsets() {
      return offsetsData_;
   }

   public void loadOffsets() {
      StrVector resolutionNames = core_.getAvailablePixelSizeConfigs();
      for (int i = 0; i < resolutionNames.size(); ++i) {
         OffsetsRow offsetsRow = new OffsetsRow();
         offsetsRow.resolutionName = resolutionNames.get(i);
         offsetsRow.readFromPrefs(prefs_);
         offsetsData_.put(offsetsRow.resolutionName, offsetsRow);
      }
   }

   public void saveOffsets() {
      for (OffsetsRow offsetsRow : offsetsData_.values()) {
         offsetsRow.writeToPrefs(prefs_);
      }
   }

   public void setOffsets(String resolutionSettingName) {
      setOffsets(offsetsData_.get(resolutionSettingName));

   }

   public void setOffsets(OffsetsRow offsetsRow) {
      controller_.setOffsets(offsetsRow.x, offsetsRow.y, offsetsRow.z);
   }

   public void reapplyOffsets() {
      if (modeMgr_.getMode() == ModeManager.NAVIGATE) {
         setOffsets(navigatePixelSizeConfig_);
      } else if (modeMgr_.getMode() == ModeManager.SURVEY) {
         setOffsets(surveyPixelSizeConfig_);
      }
   }

   public void applyNavigationSystemSettings() {
      boolean navigationConfigChanged = false;
      int prevMode = modeMgr_.getMode();
      modeMgr_.setMode(ModeManager.IDLE);

      String curConfig = "";
      try {
         curConfig = core_.getCurrentPixelSizeConfig();
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }


      if (prevMode == ModeManager.NAVIGATE && !curConfig.contentEquals(navigatePixelSizeConfig_)) {
         navigatePixelSizeConfig_ = curConfig;
         navigationConfigChanged = true;
      }

      if (navigationConfigChanged || prevMode != ModeManager.NAVIGATE) {
         applySystemSettings(navigatePixelSizeConfig_);
      }
   }

   public void applySurveySystemSettings() {
      int prevMode = modeMgr_.getMode();
      modeMgr_.setMode(ModeManager.IDLE);

      if (prevMode == ModeManager.NAVIGATE) {
         try {
            navigatePixelSizeConfig_ = core_.getCurrentPixelSizeConfig();
         } catch (Exception ex) {
            ReportingUtils.showError(ex);
         }
         applySystemSettings(surveyPixelSizeConfig_);

      }

   }

   public void applySystemSettings(String pixelConfig) {


      try {
         core_.waitForSystem();
         controller_.rememberZPosition();
         core_.waitForSystem();
         turnOffContinuousAutofocus();
         //core_.setPixelSizeConfig(pixelConfig);
         //core_.waitForSystem();
         setOffsets(pixelConfig);
         /*try {
            if (core_.hasProperty("PFS-Offset", "Position")) {
               if (pixelConfig.equals(surveyPixelSizeConfig_)) {
                  core_.setProperty("PFS-Offset", "Position", "2.824");
               } else {
                  core_.setProperty("PFS-Offset", "Position", "12.355");
               }
            }
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }*/
         turnOnContinuousAutofocus();

      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
      coords_.setRoiDimensionsOnMap(controller_.getCurrentRoiDimensions());
   }

   public void turnOffContinuousAutofocus() {
      //TODO: Make this general.
      try {
         String autofocusDevice = core_.getAutoFocusDevice();
         if (autofocusDevice.length() > 0) {
            autofocusOffset_ = core_.getAutoFocusOffset();
            //core_.enableContinuousFocus(false);

            if (autofocusDevice.equals("PerfectFocus")) {
               core_.setProperty(autofocusDevice, "State", "off");
            }
            core_.waitForDevice(autofocusDevice);

         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   public void turnOnContinuousAutofocus() {
      //TODO: Make this general.
      try {
         String autofocusDevice = core_.getAutoFocusDevice();
         if (autofocusDevice.length() > 0) {
            core_.enableContinuousFocus(true);

            //if (autofocusDevice.equals("PerfectFocus")) {
            //   core_.setProperty(autofocusDevice, "State", "on");
            //}

            core_.waitForDevice(autofocusDevice);
         }
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   void snap() {
      app_.snapSingleImage();
   }
/*
   public class AcqControlDlgMosaic extends AcqControlDlg {

      protected static final long serialVersionUID = 1L;

      public AcqControlDlgMosaic(MMAcquisitionEngineMTMosaic eng, ScriptInterface app) {
         super(eng, null, (DeviceControlGUI) app_);

         //multiPosCheckBox_.setVisible(false);
         positionsPanel_.setSelected(true);
         listButton_.setVisible(false);
         JLabel slideexplorerRoiLabel = new JLabel("(Each SlideExplorer ROI makes one mosaic.)");
         
         slideexplorerRoiLabel.setFont(new Font("Arial", Font.PLAIN, 10));
         slideexplorerRoiLabel.setBounds(15, 23, 200, 19);
         positionsPanel_.add(slideexplorerRoiLabel);
         positionsPanel_.removeActionListeners();
         positionsPanel_.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
               ReportingUtils.showMessage("To acquire mosaics, you must use multiple positions.\n\n" +
                     "To use the standard Multi-Dimensional Acquisition, click the Multi-D Acq. button\n" +
                     "on the main Micro-Manager window.");
               positionsPanel_.setSelected(true);
            }
         });
         
         setTitle(this.getTitle() + " (SlideExplorer Mosaics)");
         displayModeCombo_.removeItemAt(2); // Remove the single window option.
         setVisible(true);

      }
   }*/

   /*
    * Draws tiles and the ROI at the end of the Swing Event Dispatcher Thread Queue.
    */

   class GUIUpdater implements Runnable {

      private Point tileIndex_ = null;
      private boolean regenerate_ = true;

      GUIUpdater(Point tileIndex) {
         tileIndex_ = tileIndex;
      }

      GUIUpdater() {
         tileIndex_ = null;
      }

      GUIUpdater(boolean regenerate) {
         regenerate_ = regenerate;
      }

      public void run() {
         if (tileIndex_ == null) {
            if (regenerate_) {
               regenerateView();
            }
            if (modeMgr_.getMode() == ModeManager.NAVIGATE) {
               Point offScreenPos = coords_.mapToOffScreen(controller_.getCurrentMapPosition());
               coords_.setRoiDimensionsOnMap(controller_.getCurrentRoiDimensions());
               display_.showRoiAt(coords_.offScreenToRoiRect(offScreenPos));
            } else {
               display_.hideRoi();
            }
         } else {
            drawTile(tileIndex_);
         }
         display_.updateAndDraw();
      }

      /*
       * Draw an individual tile to the view by drawing its corresponding multitile.
       */
      public void drawTile(Point tileIndex) {
         Point3D multiTileIndex = coords_.tileToMultiTile(tileIndex);
         drawMultiTile(multiTileIndex);
      }

      /*
       * Draw a particular multitile to the view.
       */
      public void drawMultiTile(Point3D multiTileIndex) {
         Point offScreenPosition = coords_.multiTileToOffScreen(multiTileIndex);
         if (cache_.hasImage(multiTileIndex)) {
            ImageProcessor img = cache_.getImage(multiTileIndex);
            display_.placeImage(offScreenPosition, img);
         } else {
            display_.placeImage(offScreenPosition, blackImg_);
         }
      }

      /*
       * Draw all multitiles needed in the current view.
       */
      public void regenerateView() {
         ArrayList<Point3D> onScreenMultiTiles = coords_.getMultiTilesOnScreen();
         for (Point3D multiTile : onScreenMultiTiles) {
            drawMultiTile(multiTile);
         }
         ArrayList<Point3D> offScreenMultiTiles = coords_.getMultiTilesOffScreen();
         for (Point3D multiTile : offScreenMultiTiles) {
            if (!onScreenMultiTiles.contains(multiTile)) {
               drawMultiTile(multiTile);
            }
         }
      }
   }

   /*
    * Hardware-bound thread.
    */
   class TileGrabberThread extends Thread {

      double tol_;

      public TileGrabberThread() {
         setName("SlideExplorer hardware thread");
      }

      public void run() {
         while (stopTileGrabberThread_ == false) {

            if (modeMgr_.getMode() == ModeManager.SURVEY) {
               ArrayList<Point> missingTiles = findMissingTiles();
               if (missingTiles.size() > 0) {
                  try {
                     Point tile = findBestTile(missingTiles);
                     acquireNewTile(tile);
                     SwingUtilities.invokeLater(new GUIUpdater(tile));
                  } catch (Throwable e) {
                     ReportingUtils.logError(e);
                  }
               } else {
                  try {
                     sleep(20);
                  } catch (InterruptedException e) {
                     ReportingUtils.logError(e, "tileGrabberThread sleep resulted in an exception.");
                  }
               }
            } else {
               //SwingUtilities.invokeLater(new GUIUpdater(false));
               try {
                  sleep(20);
               } catch (InterruptedException e) {
                  ReportingUtils.logError(e, "tileGrabberThread sleep resulted in an exception.");
               }
            }
         }
      }

      public ArrayList<Point> findMissingTiles() {
         ArrayList<Point> tiles = coords_.getTilesOnScreen();
         for (int i = tiles.size() - 1; i >= 0; i--) {
            if (cache_.hasImage(tiles.get(i))) {
               tiles.remove(i);
            }
         }
         return tiles;
      }

      public void navigate(Point mapPos) {
         applyNavigationSystemSettings();
         modeMgr_.setMode(ModeManager.NAVIGATE);
         controller_.goToMapPosition(mapPos);
         updateView();
      }

      public void survey() {
         applySurveySystemSettings();
         modeMgr_.setMode(ModeManager.SURVEY);
         updateView();
      }

      public void pauseSlideExplorer() {
         applyNavigationSystemSettings();
         modeMgr_.setMode(ModeManager.NAVIGATE);
         updateView();
      }

      /*
       * Grab an image at a given position and cache it in multitiles.
       */
      protected void acquireNewTile(final Point tileIndex) {
         Point mapPosition = coords_.tileToMap(tileIndex);

         if (!cache_.hasImage(tileIndex)) {
            app_.getSnapLiveManager().setLiveMode(false);
            final ImageProcessor img = controller_.grabImageAtMapPosition(mapPosition);
            cache_.addImage(tileIndex, img);
         }
      }

      protected Point findBestTile(ArrayList<Point> neededTiles) {
         ArrayList<Point> nearestTiles = findNearestTiles(neededTiles);
         //return gravitateToBufferTiles(nearestTiles);
         return findMostNeighborlyTile(nearestTiles);
      }

      protected ArrayList<Point> findNearestTiles(ArrayList<Point> neededTiles) {
         double tileDist;
         double tol = 0.01;
         double minDist = java.lang.Double.POSITIVE_INFINITY;
         ArrayList<Point> nearestTiles = new ArrayList<Point>();
         Point curTile = coords_.getNearestTileFromMapPosition(controller_.getCurrentMapPosition());

         // Find tile(s) closest to current stage position.
         for (int i = 0; i < neededTiles.size(); ++i) {
            try {
               Point neededTile = neededTiles.get(i);
               tileDist = neededTile.distance(curTile);
               if (tileDist < (minDist + tol)) {
                  if (tileDist < (minDist - tol)) {
                     minDist = tileDist;
                     nearestTiles.clear();
                  }
                  nearestTiles.add(neededTile);
               }
            } catch (ArrayIndexOutOfBoundsException e) {
               ReportingUtils.logError(e);
            } catch (NullPointerException e) {
               ReportingUtils.logError(e);
            }
         }
         return nearestTiles;
      }

      protected Point findMostNeighborlyTile(ArrayList<Point> nearestTiles) {
         // Among nearestTiles, find tile with =most buffered neighbors.
         double neighborliness;
         double maxNeighborliness = -1.0;
         Point nearbyTile;
         Point chosenTile = null;

         for (Point candidateTile : nearestTiles) {
            neighborliness = 0;
            for (int i = -2; i <= 2; i++) {
               for (int j = -2; j <= 2; j++) {
                  if (i != 0 || j != 0) {
                     nearbyTile = new Point(candidateTile.x + i, candidateTile.y + j);
                     if (cache_.hasImage(nearbyTile)) {
                        neighborliness += 1. / Math.pow(nearbyTile.distance(candidateTile), 3.);
                     }
                  }
               }
            }

            if (neighborliness > maxNeighborliness) {
               maxNeighborliness = neighborliness;
               chosenTile = candidateTile;
            }
         }

         return chosenTile;
      }
   }

   class ModeManager {

      public ModeManager() {
      }
      public static final int IDLE = 0;
      public static final int SURVEY = 1;
      public static final int NAVIGATE = 2;
      public static final int MOSAIC5D = 3;
      protected int mode_ = SURVEY;
      protected Point2D.Double position_;

      public synchronized void setMode(int mode) {
         mode_ = mode;
      }

      public synchronized int getMode() {
         return mode_;
      }

      public synchronized void setPosition(Point2D.Double position) {
         position_ = position;
      }

      public synchronized Point2D.Double getPosition() {
         return position_;
      }
   }

   public class OffsetsRow {

      String resolutionName;
      double x = 0;
      double y = 0;
      double z = 0;

      public void writeToPrefs(Preferences prefs) {
         prefs.putDouble(resolutionName + "-xoffset", x);
         prefs.putDouble(resolutionName + "-yoffset", y);
         prefs.putDouble(resolutionName + "-zoffset", z);
      }

      public void readFromPrefs(Preferences prefs) {
         x = prefs.getDouble(resolutionName + "-xoffset", 0);
         y = prefs.getDouble(resolutionName + "-yoffset", 0);
         z = prefs.getDouble(resolutionName + "-zoffset", 0);
      }
   }
}
