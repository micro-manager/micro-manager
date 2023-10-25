///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
//

package org.micromanager.magellan.internal.explore;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.api.AcqEngJDataSink;
import org.micromanager.acqj.api.AcquisitionHook;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.acqj.main.AcquisitionEvent;
import org.micromanager.acqj.main.XYTiledAcquisition;
import org.micromanager.acqj.util.AcqEventModules;
import org.micromanager.acqj.util.AcquisitionEventIterator;
import org.micromanager.acqj.util.ChannelSetting;
import org.micromanager.acqj.util.xytiling.XYStagePosition;
import org.micromanager.ndtiffstorage.NDTiffAPI;
import org.micromanager.ndviewer.api.NDViewerAcqInterface;
import org.micromanager.remote.PycroManagerCompatibleAcq;

/**
 * A single time point acquisition that can dynamically expand in X,Y, and Z.
 *
 * @author Henry
 */
public class ExploreAcquisition extends XYTiledAcquisition
        implements PycroManagerCompatibleAcq, NDViewerAcqInterface {

   private List<XYStagePosition> positions_;

   private Set<HashMap<String, Object>> queuedTileEvents_ = new CopyOnWriteArraySet<>();

   private ExecutorService submittedSequenceMonitorExecutor_ =
         Executors.newSingleThreadExecutor((Runnable r) -> {
            return new Thread(r, "Submitted sequence monitor");
         });

   private Consumer<String> logger_;
   ChannelGroupSettings channels_;

   public ExploreAcquisition(int pixelOverlapX, int pixelOverlapY, double zStep,
                             ChannelGroupSettings channels,
                             ExploreAcqUIAndStorage adapter) throws Exception {
      this(pixelOverlapX, pixelOverlapY, zStep, channels, adapter, (String s) -> {
      });
   }

   public ExploreAcquisition(int pixelOverlapX, int pixelOverlapY, double zStep,
                             ChannelGroupSettings channels,
                             AcqEngJDataSink adapter,
                             Consumer<String> logger) throws Exception {
      super(adapter, pixelOverlapX, pixelOverlapY, zStep,
         // Add metadata specific to Magellan explore
         new Consumer<JSONObject>() {
            @Override
            public void accept(JSONObject summaryMetadata) {
               AcqEngMetadata.setExploreAcq(summaryMetadata, true);
               AcqEngMetadata.setZStepUm(summaryMetadata, zStep);
            }
         });
      logger_ = logger;
      channels_ = channels;

      createXYPositions();

      // Add acquisition hook that removes tiles from queue just before they are acquired
      // so that the display can reflect this
      addHook(new AcquisitionHook() {
         @Override
         public AcquisitionEvent run(AcquisitionEvent event) {
            if (queuedTileEvents_.contains(event.getAxisPositions())) {
               queuedTileEvents_.remove(event.getAxisPositions());
            }
            return event;
         }

         @Override
         public void close() {

         }
      }, Acquisition.BEFORE_HARDWARE_HOOK);
   }

   public double getZOrigin(String name) {
      return getZAxes().get(name).zOrigin_um_;
   }

   public double getZStep(String name) {
      return getZAxes().get(name).zStep_um_;
   }


   @Override
   public boolean isFinished() {
      return areEventsFinished() && getDataSink().isFinished();
   }

   @Override
   public void abort() {
      super.abort();
      submittedSequenceMonitorExecutor_.shutdownNow();
   }

   /**
    * Submit a iterator of acquisition events for execution.
    *
    * @param iter an iterator of acquisition events
    * @return
    */
   @Override
   public Future submitEventIterator(Iterator<AcquisitionEvent> iter) {
      return submittedSequenceMonitorExecutor_.submit(() -> {
         Future iteratorFuture = null;
         try {
            iteratorFuture = super.submitEventIterator(iter);
            iteratorFuture.get();
         } catch (InterruptedException ex) {
            iteratorFuture.cancel(true);
         } catch (ExecutionException ex) {
            ex.printStackTrace();
            logger_.accept(ex.getMessage());
         }
      });

   }


   private void createXYPositions() {
      try {
         positions_ = new ArrayList<XYStagePosition>();
         positions_.add(new XYStagePosition(new Point2D.Double(Engine.getCore().getXPosition(),
                 Engine.getCore().getYPosition()), 0, 0));

      } catch (Exception e) {
         e.printStackTrace();
         logger_.accept("Problem with Acquisition's XY positions. Check acquisition settings");
         throw new RuntimeException();
      }
   }

   /**
    *
    * @return
    */
   public LinkedBlockingQueue<HashMap<String, Object>> getTilesWaitingToAcquireAtSlice(
           HashMap<String, Integer> zAxisPositions) {
      if (queuedTileEvents_ == null) {
         return null;
      }
      LinkedBlockingQueue<HashMap<String, Object>> tiles = new LinkedBlockingQueue<>();
      for (HashMap<String, Object> axes : queuedTileEvents_) {
         boolean match = true;
         for (String zName : zAxisPositions.keySet()) {
            if (!axes.get(zName).equals(zAxisPositions.get(zName))) {
               match = false;
               break;
            }
         }
         if (match) {
            tiles.add(axes);
         }
      }
      return tiles;
   }

   public void acquireTileAtCurrentLocation() throws Exception {
      double xPos;
      double yPos;
      double zPos;

      try {
         //get current XY and Z Positions
         xPos = Engine.getCore().getXPosition();
         yPos = Engine.getCore().getYPosition();
      } catch (Exception ex) {
         logger_.accept("Couldnt get device positions from core");
         return;
      }


      HashMap<String, Integer> zTopIndex = new HashMap<String, Integer>();
      HashMap<String, Integer> zBottomIndex = new HashMap<String, Integer>();
      // get slice axes for all z devices
      for (String zName : getZDeviceNames()) {
         double zPosition = Engine.getCore().getPosition(zName);
         int zIndex = (int) Math.round((zPosition - getZOrigin(zName)) / getZStep(zName));
         zTopIndex.put(zName, zIndex);
         zBottomIndex.put(zName, zIndex);
      }


      int posIndex = pixelStageTranslator_.getFullResPositionIndexFromStageCoords(xPos, yPos);

      submitEvents(new int[]{(int) pixelStageTranslator_.getXYPosition(posIndex).getGridRow()},
              new int[]{(int) pixelStageTranslator_.getXYPosition(posIndex).getGridCol()},
              zTopIndex, zBottomIndex);
   }

   public void acquireTiles(final int r1, final int c1, final int r2, final int c2) {
      //So it doesnt slow down GUI
      new Thread(() -> {
         HashMap<String, Integer> zMinIndices = new HashMap<String, Integer>();
         HashMap<String, Integer> zMaxIndices = new HashMap<String, Integer>();

         for (String zName : getZDeviceNames()) {
            try {
               zMinIndices.put(zName, getZLimitLowerSliceIndex(zName));
               zMaxIndices.put(zName, getZLimitUpperSliceIndex(zName));
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         }


         //order tile indices properly
         int row1 = Math.min(r1, r2);
         int row2 = Math.max(r1, r2);
         int col1 = Math.min(c1, c2);
         int col2 = Math.max(c1, c2);
         //Get position Indices from manager based on row and column
         //it will create new metadata as needed
         int[] newPositionRows = new int[(row2 - row1 + 1) * (col2 - col1 + 1)];
         int[] newPositionCols = new int[(row2 - row1 + 1) * (col2 - col1 + 1)];
         for (int r = row1; r <= row2; r++) {
            for (int c = col1; c <= col2; c++) {
               int relativeRow = (r - row1);
               int relativeCol = (c - col1);
               int numRows = (1 + row2 - row1);
               int i = ((relativeCol % 2 == 0) ? relativeRow : (numRows - relativeRow - 1))
                     + numRows * relativeCol;
               newPositionRows[i] = r;
               newPositionCols[i] = c;
            }
         }
         submitEvents(newPositionRows, newPositionCols, zMinIndices, zMaxIndices);

      }).start();

   }

   private void submitEvents(int[] newPositionRows, int[] newPositionCols,
                             HashMap<String, Integer> zMinIndices,
                               HashMap<String, Integer> zMaxIndices) {
      int[] posIndices = getPixelStageTranslator()
              .getPositionIndices(newPositionRows, newPositionCols);
      List<XYStagePosition> allPositions = getPixelStageTranslator().getPositionList();
      List<XYStagePosition> selectedXYPositions = new ArrayList<XYStagePosition>();
      for (int i : posIndices) {
         selectedXYPositions.add(allPositions.get(i));
      }
      ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions
              = new ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>>();
      acqFunctions.add(positions(selectedXYPositions));
      for (String zName :  getZDeviceNames()) {
         acqFunctions.add(AcqEventModules.moveStage(zName, zMinIndices.get(zName),
                 zMaxIndices.get(zName) + 1, getZStep(zName), getZOrigin(zName)));
      }
      if (channels_ != null && channels_.getNumChannels() > 0) {
         ArrayList<ChannelSetting> channels = new ArrayList<ChannelSetting>();
         for (String name : channels_.getChannelNames()) {
            if (channels_.getChannelSetting(name).use_) {
               channels.add(channels_.getChannelSetting(name));
            }
         }
         acqFunctions.add(AcqEventModules.channels(channels));
      }

      AcquisitionEvent baseEvent = new AcquisitionEvent(this);
      Iterator<AcquisitionEvent> iterator = new AcquisitionEventIterator(baseEvent,
              acqFunctions, monitorSliceIndices());

      //Get rid of duplicates, send to acquisition engine 
      Predicate<AcquisitionEvent> predicate = filterExistingEventsAndDisplayQueuedTiles();
      List<AcquisitionEvent> eventList = new LinkedList<AcquisitionEvent>();
      while (iterator.hasNext()) {
         AcquisitionEvent event = iterator.next();
         if (predicate.test(event)) {
            eventList.add(event);
         }
      }


      ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> list
              = new ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>>();
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> fn = (AcquisitionEvent t) -> {
         return eventList.iterator();
      };
      list.add(fn);

      AcquisitionEvent event = new AcquisitionEvent(this);
      submitEventIterator(new AcquisitionEventIterator(event, list));
   }

   private Function<AcquisitionEvent, Iterator<AcquisitionEvent>>
            positions(List<XYStagePosition> positions) {
      return (AcquisitionEvent event) -> {
         Stream.Builder<AcquisitionEvent> builder = Stream.builder();
         if (positions == null) {
            builder.accept(event);
         } else {
            for (int index = 0; index < positions.size(); index++) {
               AcquisitionEvent posEvent = event.copy();
               posEvent.setAxisPosition(AcqEngMetadata.AXES_GRID_ROW, positions.get(index)
                     .getGridRow());
               posEvent.setAxisPosition(AcqEngMetadata.AXES_GRID_COL, positions.get(index)
                     .getGridCol());
               posEvent.setX(positions.get(index).getCenter().x);
               posEvent.setY(positions.get(index).getCenter().y);
               builder.accept(posEvent);
            }
         }
         return builder.build().iterator();
      };
   }

   private Predicate<AcquisitionEvent> filterExistingEventsAndDisplayQueuedTiles() {
      return (AcquisitionEvent event) -> {
         if (queuedTileEvents_.contains(event.getAxisPositions())) {
            return false; //This tile is already waiting to be acquired
         }
         // add tile to list waiting to acquire for drawing purposes
         queuedTileEvents_.add(event.getAxisPositions());
         return true;
      };
   }

   private Function<AcquisitionEvent, AcquisitionEvent> monitorSliceIndices() {
      return (AcquisitionEvent event) -> {
         try {
            for (String name : getZDeviceNames()) {
               getZAxes().get(name).lowestExploredZIndex_ =
                     Math.min(getZAxes().get(name).lowestExploredZIndex_,
                           getZLimitLowerSliceIndex(name));
               getZAxes().get(name).highestExploredZIndex_ =
                     Math.max(getZAxes().get(name).highestExploredZIndex_,
                           getZLimitUpperSliceIndex(name));
            }
         } catch (Exception e) {
            e.printStackTrace();
         }
         return event;
      };
   }

   /**
    * get min slice index for according to z limit sliders.
    *
    * @return
    */
   private int getZLimitLowerSliceIndex(String name) {

      return getZAxes().get(name).exploreLowerZIndexLimit_;
   }

   /**
    * get max slice index for current settings in explore acquisition.
    */
   private int getZLimitUpperSliceIndex(String name) {
      return getZAxes().get(name).exploreUpperZIndexLimit_;
   }

   public void setZLimits(String name, double zTop, double zBottom) {
      getZAxes().get(name).exploreLowerZIndexLimit_ =
              (int) ((Math.min(zTop, zBottom) - getZOrigin(name)) / getZAxes().get(name).zStep_um_);
      getZAxes().get(name).exploreUpperZIndexLimit_ =
              (int) ((Math.max(zTop, zBottom) - getZOrigin(name)) / getZAxes().get(name).zStep_um_);
   }

   @Override
   public NDTiffAPI getStorage() {
      return ((ExploreAcqUIAndStorage) dataSink_).getStorage();
   }

   @Override
   public int getEventPort() {
      return -1;
   }


}

