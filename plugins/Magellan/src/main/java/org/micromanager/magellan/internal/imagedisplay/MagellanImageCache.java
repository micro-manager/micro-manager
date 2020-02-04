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
package org.micromanager.magellan.internal.imagedisplay;

import com.google.common.eventbus.EventBus;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.micromanager.magellan.internal.datasaving.MultiResMultipageTiffStorage;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.internal.coordinates.XYStagePosition;
import org.micromanager.magellan.internal.imagedisplay.events.ImageCacheClosingEvent;
import org.micromanager.magellan.internal.imagedisplay.events.ImageCacheFinishedEvent;
import org.micromanager.magellan.internal.imagedisplay.events.MagellanNewImageEvent;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.misc.LongPoint;
import org.micromanager.magellan.internal.misc.MD;

/**
 * This class manages a magellan dataset on disk, as well as the state of a view
 * into it (i.e. contrast settings/zoom, etc) and manages class that actually
 * forms the image
 */
public class MagellanImageCache {

   private MultiResMultipageTiffStorage imageStorage_;
   private EventBus dataProviderBus_ = new EventBus();
   private ExecutorService displayCommunicationExecutor_ = Executors.newSingleThreadExecutor((Runnable r) -> new Thread(r, "Image cache thread"));
   private final boolean loadedData_;
   private String dir_;
   
   public MagellanImageCache(String dir, JSONObject summaryMetadata, DisplaySettings displaySettings) {
      imageStorage_ = new MultiResMultipageTiffStorage(dir, summaryMetadata);
      imageStorage_.setDisplaySettings(displaySettings);
      loadedData_ = false;
   }
   
   //Constructor for opening loaded data
   public MagellanImageCache(String dir) throws IOException {
      imageStorage_ = new MultiResMultipageTiffStorage(dir);
      dir_ = dir;
      loadedData_ = true;
   }

   public double getPixelSize_um() {
      return imageStorage_.getPixelSizeXY();

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
         dataProviderBus_.post(new ImageCacheClosingEvent());
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
      return imageStorage_.getTileHeight();
   }

   public int getTileWidth() {
      return imageStorage_.getTileWidth();
   }

   public boolean isRGB() {
      return false;
   }

   public boolean isExploreAcquisition() {
      return MD.isExploreAcq(imageStorage_.getSummaryMetadata());
   }

   public double getZStep() {
      return imageStorage_.getPixelSizeZ();
   }

   public boolean isXYBounded() {
      return loadedData_ || !isExploreAcquisition();
   }

   public long[] getImageBounds() {
      int tileHeight = imageStorage_.getTileHeight();
      int tileWidth = imageStorage_.getTileWidth();
      if (!isExploreAcquisition()) {
         return new long[]{0, 0, imageStorage_.getNumCols() * tileWidth, imageStorage_.getNumRows() * tileHeight};
      } else if (loadedData_) {
         long yMin = (long) (imageStorage_.getMinRow() * tileHeight);
         long xMin = (long) (imageStorage_.getMinCol() * tileWidth);
         long xMax = imageStorage_.getNumCols() * tileWidth + xMin;
         long yMax = imageStorage_.getNumRows() * tileHeight + yMin;
         return new long[]{xMin, yMin, xMax, yMax};
      }
      return null; //No image bounds for explore acquisiiton
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
      return imageStorage_.getPosManager().getFullResPositionIndexFromStageCoords(xPos, yPos);
   }
   
   public Point2D.Double stageCoordinateFromPixelCoordinate(long absoluteX, long absoluteY) {
      return imageStorage_.getStageCoordsFromPixelCoords(absoluteX, absoluteY);
   }
   
   public LongPoint pixelCoordsFromStageCoords(double x, double  y) {
      return imageStorage_.getPixelCoordsFromStageCoords(x, y);
   }

   public XYStagePosition getXYPosition(int posIndex) {
      return imageStorage_.getPosManager().getXYPosition(posIndex);
   }

   public int[] getPositionIndices(int[] newPositionRows, int[] newPositionCols) {
      return imageStorage_.getPosManager().getPositionIndices(newPositionRows, newPositionCols);
   }

   public List<XYStagePosition> getPositionList() {
      return imageStorage_.getPosManager().getPositionList();
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
