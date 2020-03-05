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
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acqj.api.AcqEngMetadata;
import org.micromanager.acqj.api.Acquisition;
import org.micromanager.acqj.api.DataSink;
import org.micromanager.acqj.api.XYStagePosition;
import org.micromanager.acqj.internal.acqengj.AcquisitionBase;
import org.micromanager.magellan.internal.channels.MagellanChannelGroupSettings;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.multiresstorage.MultiResMultipageTiffStorage;
import org.micromanager.multiresviewer.DisplaySettings;
import org.micromanager.multiresviewer.MagellanDisplayController;

/**
 * This class manages a magellan dataset on disk, as well as the state of a view
 * into it (i.e. contrast settings/zoom, etc) and manages class that actually
 * forms the image
 */
public class MagellanImageCache implements DataSink {

   private MultiResMultipageTiffStorage imageStorage_;
   private EventBus dataProviderBus_ = new EventBus();
   private ExecutorService displayCommunicationExecutor_ = Executors.newSingleThreadExecutor((Runnable r) -> new Thread(r, "Image cache thread"));
   private final boolean loadedData_;
   private String dir_;
   private String name_;
   private Acquisition acq_;
   private final boolean showDisplay_;
   private PixelStageTranslator stageTranslator_;
   private double pixelSizeZ_, pixelSizeXY_;

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
      acq_ = (MagellanAcquisition) acq;
      pixelSizeXY_ = MagellanMD.getPixelSizeUm(summaryMetadata);
      pixelSizeZ_ = MagellanMD.getZStepUm(summaryMetadata);
      stageTranslator_ = new PixelStageTranslator(stringToTransform(MagellanMD.getAffineTransformString(summaryMetadata)),
         MagellanMD.getWidth(summaryMetadata), MagellanMD.getHeight(summaryMetadata), 
              MagellanMD.getPixelOverlapX(summaryMetadata), MagellanMD.getPixelOverlapY(summaryMetadata),
              MagellanMD.getInitialPositionList(summaryMetadata));
      
      DisplaySettings displaySettings = new DisplaySettings((MagellanChannelGroupSettings) acq.getChannels(), summaryMetadata);
      if (showDisplay_) {
         //create display
         try {
            new MagellanDisplayController(this, displaySettings, (MagellanAcquisition) acq_);
         } catch (Exception e) {
            e.printStackTrace();
            Log.log("Couldn't create display succesfully");
         }
      }
      imageStorage_ = new MultiResMultipageTiffStorage(dir_, summaryMetadata, displaySettings.toJSON());
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
         mat[i] = NumUtils.parseDouble(vals[i]);
      }
      return new AffineTransform(mat);
   }

   /**
    * Called when images done arriving
    */
   public void finished() {
      if (!imageStorage_.isFinished()) {
         imageStorage_.finishedWriting();
      }
      dataProviderBus_.post(new ImageCacheFinishedEvent());
   }

   public boolean isFinished() {
      return imageStorage_.isFinished();
   }

   public String getDiskLocation() {
      return imageStorage_.getDiskLocation();
   }

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
   public void close() {
      if (imageStorage_.isFinished()) {
         imageStorage_.close();
         imageStorage_ = null;
         displayCommunicationExecutor_.shutdownNow();
         displayCommunicationExecutor_ = null;
         acq_.onDataSinkClosing();
      } else {
         //keep resubmitting so that finish, which comes from a different thread, happens first
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               MagellanImageCache.this.close();
            }
         });
      }
   }

   public void putImage(final TaggedImage taggedImg) {
      imageStorage_.putImage(taggedImg);

      //put on different thread to not slow down acquisition
      displayCommunicationExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            dataProviderBus_.post(new MagellanNewImageEvent(taggedImg.tags));
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
      return stageTranslator_.getTileHeight();
   }

   public int getTileWidth() {
      return stageTranslator_.getTileWidth();
   }

   public boolean isRGB() {
      return false;
   }

   public boolean isExploreAcquisition() {
      return MagellanMD.isExploreAcq(imageStorage_.getSummaryMetadata());
   }

   public double getZStep() {
      return pixelSizeZ_;
   }

   public boolean isXYBounded() {
      return loadedData_ || !isExploreAcquisition();
   }

   public long[] getImageBounds() {
      if (isExploreAcquisition()) {
         return null;
      }
      return imageStorage_.getImageBounds();
   }

   public TaggedImage getImageForDisplay(int channel, MagellanDataViewCoords dataCoords) {
      int imagePixelWidth = (int) (dataCoords.getSourceDataSize().x / dataCoords.getDownsampleFactor());
      int imagePixelHeight = (int) (dataCoords.getSourceDataSize().y / dataCoords.getDownsampleFactor());
      long viewOffsetAtResX = (long) (dataCoords.getViewOffset().x / dataCoords.getDownsampleFactor());
      long viewOffsetAtResY = (long) (dataCoords.getViewOffset().y / dataCoords.getDownsampleFactor());

      return imageStorage_.getImageForDisplay(channel,
              dataCoords.getAxisPosition("z"),
              dataCoords.getAxisPosition("t"),
              dataCoords.getResolutionIndex(),
              viewOffsetAtResX, viewOffsetAtResY,
              imagePixelWidth, imagePixelHeight);
   }

   public void registerForEvents(Object obj) {
      dataProviderBus_.register(obj);
   }

   public void unregisterForEvents(Object obj) {
      dataProviderBus_.unregister(obj);
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
      return stageTranslator_.getFullResPositionIndexFromStageCoords(xPos, yPos);
   }

    /**
    *
    * @param xAbsolute x coordinate in the full Res stitched image
    * @param yAbsolute y coordinate in the full res stitched image
    * @return stage coordinates of the given pixel position
    */
   public Point2D.Double stageCoordinateFromPixelCoordinate(long absoluteX, long absoluteY) {
      return stageTranslator_.getStageCoordsFromPixelCoords(absoluteX, absoluteY);
   }
   
   /* 
    * @param stageCoords x and y coordinates of image in stage space
    * @return absolute, full resolution pixel coordinate of given stage posiiton
    */
   public Point pixelCoordsFromStageCoords(double x, double y) {
      return stageTranslator_.getPixelCoordsFromStageCoords(x, y);
   }

   public XYStagePosition getXYPosition(int posIndex) {
      return stageTranslator_.getXYPosition(posIndex);
   }

   public int[] getPositionIndices(int[] newPositionRows, int[] newPositionCols) {
      return stageTranslator_.getPositionIndices(newPositionRows, newPositionCols);
   }

   public List<XYStagePosition> getPositionList() {
      return stageTranslator_.getPositionList();
   }

   int getMaxResolutionIndex() {
      return imageStorage_.getNumResLevels() - 1;
   }

   Point2D.Double getFullResolutionSize() {
      long[] bounds = getImageBounds();
      return new Point2D.Double(bounds[2] - bounds[0], bounds[3] - bounds[1]);
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

   Set<Point> getTileIndicesWithDataAt(int zIndex) {
      return imageStorage_.getTileIndicesWithDataAt(zIndex);
   }

}
