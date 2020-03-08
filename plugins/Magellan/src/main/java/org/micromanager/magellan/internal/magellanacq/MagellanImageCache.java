///////////////////////////////////////////////////////////////////////////////
//FILE:          MMImageCache.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein
// COPYRIGHT:    University of California, San Francisco, 2010
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
package org.micromanager.magellan.internal.magellanacq;

import com.google.common.eventbus.EventBus;
import java.awt.Color;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acqj.api.Acquisition;
import org.micromanager.acqj.api.DataSink;
import org.micromanager.acqj.api.XYStagePosition;
import org.micromanager.acqj.internal.acqengj.AcquisitionBase;
import org.micromanager.magellan.internal.channels.MagellanChannelGroupSettings;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.multiresstorage.MultiResMultipageTiffStorage;
import org.micromanager.ndviewer.api.DataSource;
import org.micromanager.ndviewer.internal.gui.DataViewCoords;
import org.micromanager.multiresviewer.NDViewer;
import org.micromanager.ndviewer.api.AcquisitionPlugin;

/**
 * This class manages a magellan dataset on disk, as well as the state of a view
 * into it (i.e. contrast settings/zoom, etc) and manages class that actually
 * forms the image
 */
public class MagellanImageCache implements DataSink, DataSource {

   private MultiResMultipageTiffStorage imageStorage_;
   private ExecutorService displayCommunicationExecutor_ = Executors.newSingleThreadExecutor((Runnable r) -> new Thread(r, "Image cache thread"));
   private final boolean loadedData_;
   private String dir_;
   private String name_;
   private Acquisition acq_;
   private final boolean showDisplay_;
   private PixelStageTranslator stageCoordinateTranslator_;
   private double pixelSizeZ_, pixelSizeXY_;
   private MagellanViewer display_;
   private JSONObject summaryMetadata_;
   private CopyOnWriteArrayList<String> channelNames_ = new CopyOnWriteArrayList<String>();

   public MagellanImageCache(String dir, boolean showDisplay) {
      dir_ = dir;
      loadedData_ = false;
      showDisplay_ = showDisplay;
   }

   //Constructor for opening loaded data
   public MagellanImageCache(String dir) throws IOException {
      imageStorage_ = new MultiResMultipageTiffStorage(dir);
      dir_ = dir;
      loadedData_ = true;
      showDisplay_ = true;
   }

   public double getPixelSize_um() {
      return pixelSizeXY_;

   }

   public void initialize(AcquisitionBase acq, JSONObject summaryMetadata) {
      summaryMetadata_ = summaryMetadata;
      acq_ = (MagellanAcquisition) acq;
      pixelSizeXY_ = MagellanMD.getPixelSizeUm(summaryMetadata);
      pixelSizeZ_ = MagellanMD.getZStepUm(summaryMetadata);
      stageCoordinateTranslator_ = new PixelStageTranslator(stringToTransform(MagellanMD.getAffineTransformString(summaryMetadata)),
              MagellanMD.getWidth(summaryMetadata), MagellanMD.getHeight(summaryMetadata),
              MagellanMD.getPixelOverlapX(summaryMetadata), MagellanMD.getPixelOverlapY(summaryMetadata),
              MagellanMD.getInitialPositionList(summaryMetadata));

      imageStorage_ = new MultiResMultipageTiffStorage(dir_, MagellanMD.getSavingPrefix(summaryMetadata),
              summaryMetadata, ((MagellanAcquisition) acq).getOverlapX(),
              ((MagellanAcquisition) acq).getOverlapY(),
              //TODO: in the futre may want to make multiple datasets if one
              //of these parameters changes, or better yet implement
              //in the thin the storage class to output different imaged
              //parameters to different files within the dataset
              MagellanMD.getWidth(summaryMetadata),
              MagellanMD.getHeight(summaryMetadata),
              MagellanMD.getBytesPerPixel(summaryMetadata));

      if (showDisplay_) {
         //create display
         try {
            display_ = new MagellanViewer(this, (AcquisitionPlugin) acq_, summaryMetadata);
            display_.setWindowTitle(getUniqueAcqName() + (acq != null ? (acq.isComplete() ? " (Finished)" : " (Running)") : " (Loaded)"));

         } catch (Exception e) {
            e.printStackTrace();
            Log.log("Couldn't create display succesfully");
         }
      }
      //storage class has determined unique acq name, so it can now be stored
      name_ = this.getUniqueAcqName();
   }

   private static AffineTransform stringToTransform(String s) {
      if (s.equals("Undefined")) {
         return null;
      }
      double[] mat = new double[4];
      String[] vals = s.split("_");
      for (int i = 0; i < 4; i++) {
         mat[i] = parseDouble(vals[i]);
      }
      return new AffineTransform(mat);
   }

   private static double parseDouble(String s) {
      try {
         return DecimalFormat.getNumberInstance().parse(s).doubleValue();
      } catch (ParseException ex) {
         throw new RuntimeException(ex);
      }
   }

   /**
    * Called when images done arriving
    */
   public void finished() {
      if (!imageStorage_.isFinished()) {
         imageStorage_.finishedWriting();
      }
      display_.setWindowTitle(getUniqueAcqName() + " (Finished)");
   }

   public boolean isFinished() {
      return imageStorage_.isFinished();
   }

   public String getDiskLocation() {
      return imageStorage_.getDiskLocation();
   }

   /**
    * Used for data loaded from disk
    *
    * @return
    */
   public JSONObject getDisplayJSON() {
      try {
         return imageStorage_.getDisplaySettings() == null ? null : new JSONObject(imageStorage_.getDisplaySettings().toString());
      } catch (JSONException ex) {
         throw new RuntimeException("THis shouldnt happen");
      }
   }

   /**
    * Call when display and acquisition both done
    */
   public void viewerClosing() {
      if (imageStorage_.isFinished()) {
         //Get most up to date display settings
         JSONObject displaySettings = display_.getDisplaySettingsJSON();
         imageStorage_.setDisplaySettings(displaySettings);

         //TODO: should this happen here
         display_.close();

         display_ = null;

         imageStorage_.close();
         imageStorage_ = null;
         displayCommunicationExecutor_.shutdownNow();
         displayCommunicationExecutor_ = null;
         acq_.onDataSinkClosing();
         display_.onDataSourceClosing();
      } else {
         //keep resubmitting so that finish, which comes from a different thread, happens first
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               MagellanImageCache.this.viewerClosing();
            }
         });
      }
   }

   public void putImage(final TaggedImage taggedImg) {
      String channelName = MagellanMD.getChannelName(taggedImg.tags);
      boolean newChannel = !channelNames_.contains(channelName);
      if (newChannel) {
         channelNames_.add(channelName);
      }

      imageStorage_.putImage(taggedImg, MagellanMD.getAxes(taggedImg.tags),
              MagellanMD.getChannelName(taggedImg.tags),
              (int) MagellanMD.getGridRow(taggedImg.tags),
              (int) MagellanMD.getGridCol(taggedImg.tags));

      //put on different thread to not slow down acquisition
      displayCommunicationExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            if (newChannel) {
               //Insert a preferred color. Make a copy just in case concurrency issues
               String chName = MagellanMD.getChannelName(taggedImg.tags);
               Color c = ((MagellanChannelGroupSettings) acq_.getChannels()).getPreferredChannelColor(chName);
               display_.setChannelColor(chName, c);
            }
            HashMap<String, Integer> axes = MagellanMD.getAxes(taggedImg.tags);
            String channelName = MagellanMD.getChannelName(taggedImg.tags);
            int bitDepth = MagellanMD.getBitDepth(taggedImg.tags);
            display_.newImageArrived(axes, channelName, bitDepth);
         }
      });

   }

   public JSONObject getSummaryMD() {
      if (imageStorage_ == null) {
         Log.log("imageStorage_ is null in getSummaryMetadata", true);
         return null;
      }
      try {
         return new JSONObject(imageStorage_.getSummaryMetadata().toString());
      } catch (JSONException ex) {
         throw new RuntimeException("This shouldnt happen");
      }
   }

   public int getTileHeight() {
      return stageCoordinateTranslator_.getTileHeight();
   }

   public int getTileWidth() {
      return stageCoordinateTranslator_.getTileWidth();
   }

   public boolean isRGB() {
      return false;
   }

   public boolean isExploreAcquisition() {
      return MagellanMD.isExploreAcq(summaryMetadata_);
   }

   public double getZStep() {
      return pixelSizeZ_;
   }

   public boolean isXYBounded() {
      return loadedData_ || !isExploreAcquisition();
   }

   public int[] getImageBounds() {
      if (isExploreAcquisition()) {
         return null;
      }
      return imageStorage_.getImageBounds();
   }

   @Override
   public TaggedImage getImageForDisplay(String channelName, HashMap<String, Integer> axes, int resolutionindex,
           double xOffset, double yOffset, int imageWidth, int imageHeight) {

      return imageStorage_.getImageForDisplay(
              channelNames_.indexOf(channelName), axes.get("z"), axes.get("t"),
              resolutionindex,
              (int) xOffset, (int) yOffset,
              imageWidth, imageHeight);
   }

   public boolean anythingAcquired() {
      return !imageStorage_.imageKeys().isEmpty();
   }

   public String getUniqueAcqName() {
      if (loadedData_) {
         return dir_;
      }
      return imageStorage_.getUniqueAcqName();
   }

   public int getFullResPositionIndexFromStageCoords(double xPos, double yPos) {
      return stageCoordinateTranslator_.getFullResPositionIndexFromStageCoords(xPos, yPos);
   }

   /**
    *
    * @param xAbsolute x coordinate in the full Res stitched image
    * @param yAbsolute y coordinate in the full res stitched image
    * @return stage coordinates of the given pixel position
    */
   public Point2D.Double stageCoordinateFromPixelCoordinate(long absoluteX, long absoluteY) {
      return stageCoordinateTranslator_.getStageCoordsFromPixelCoords(absoluteX, absoluteY);
   }

   /* 
    * @param stageCoords x and y coordinates of image in stage space
    * @return absolute, full resolution pixel coordinate of given stage posiiton
    */
   public Point pixelCoordsFromStageCoords(double x, double y) {
      return stageCoordinateTranslator_.getPixelCoordsFromStageCoords(x, y);
   }

   public XYStagePosition getXYPosition(int posIndex) {
      return stageCoordinateTranslator_.getXYPosition(posIndex);
   }

   public int[] getPositionIndices(int[] newPositionRows, int[] newPositionCols) {
      return stageCoordinateTranslator_.getPositionIndices(newPositionRows, newPositionCols);
   }

   public List<XYStagePosition> getPositionList() {
      return stageCoordinateTranslator_.getPositionList();
   }

   public int getMaxResolutionIndex() {
      return imageStorage_.getNumResLevels() - 1;
   }

   public int getNumChannels() {
      return imageStorage_.getNumChannels();
   }

   public int getNumFrames() {
      return imageStorage_.getNumFrames();
   }

   public int getMinZIndexLoadedData() {
      return imageStorage_.getMinSliceIndexOpenedDataset();
   }

   public int getMaxZIndexLoadedData() {
      return imageStorage_.getMaxSliceIndexOpenedDataset();
   }

   public List<String> getChannelNames() {
      return imageStorage_.getChannelNames();
   }

   public Set<Point> getTileIndicesWithDataAt(int zIndex) {
      return imageStorage_.getTileIndicesWithDataAt(zIndex);
   }

}
