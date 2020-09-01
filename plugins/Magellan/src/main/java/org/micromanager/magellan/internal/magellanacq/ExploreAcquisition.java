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
package org.micromanager.magellan.internal.magellanacq;

import java.awt.geom.Point2D;

import org.micromanager.acqj.api.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import mmcorej.org.json.JSONArray;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.Log;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.internal.acqengj.AcquisitionEventIterator;
import org.micromanager.acqj.api.AcqEventModules;
import org.micromanager.acqj.api.channels.ChannelSetting;
import org.micromanager.acqj.api.xystage.XYStagePosition;
import org.micromanager.acqj.internal.acqengj.Engine;
import org.micromanager.magellan.internal.channels.ChannelGroupSettings;
import org.micromanager.magellan.internal.channels.SingleChannelSetting;
import org.micromanager.magellan.internal.gui.GUI;

/**
 * A single time point acquisition that can dynamically expand in X,Y, and Z
 *
 * @author Henry
 */
public class ExploreAcquisition extends Acquisition implements MagellanAcquisition {

   private volatile double zTop_, zBottom_;
   private List<XYStagePosition> positions_;
   private ExploreAcqSettings settings_;

   //Map with slice index as keys used to get rid of duplicate events
   private ConcurrentHashMap<Integer, LinkedBlockingQueue<ExploreTileWaitingToAcquire>> queuedTileEvents_ = new ConcurrentHashMap<Integer, LinkedBlockingQueue<ExploreTileWaitingToAcquire>>();

   private ExecutorService submittedSequenceMonitorExecutor_ = Executors.newSingleThreadExecutor((Runnable r) -> {
      return new Thread(r, "Submitted sequence monitor");
   });

   private final double zOrigin_, zStep_;
   private int minSliceIndex_, maxSliceIndex_;

   public ExploreAcquisition(ExploreAcqSettings settings, DataSink sink) {
      super(sink);
      settings_ = settings;
      zStep_ = settings.zStep_;

      try {
         zStage_ = Magellan.getCore().getFocusDevice();
         //start at current z position
         zTop_ = Magellan.getCore().getPosition(zStage_);
         zOrigin_ = zTop_;
         zBottom_ = Magellan.getCore().getPosition(zStage_);
      } catch (Exception ex) {
         Log.log("Couldn't get focus device position", true);
         throw new RuntimeException();
      }
      int overlapX = (int) (Magellan.getCore().getImageWidth() * GUI.getTileOverlap() / 100);
      int overlapY = (int) (Magellan.getCore().getImageHeight() * GUI.getTileOverlap() / 100);
      initialize(overlapX, overlapY);
   }

   @Override
   public void addToSummaryMetadata(JSONObject summaryMetadata) {
      MagellanMD.setExploreAcq(summaryMetadata, true);
      MagellanMD.setSavingName(summaryMetadata, ((MagellanDataManager) dataSink_).getName());
      MagellanMD.setSavingName(summaryMetadata, ((MagellanDataManager) dataSink_).getDir());

      AcqEngMetadata.setZStepUm(summaryMetadata, ((ExploreAcqSettings) settings_).zStep_);
      createXYPositions();
   }

   public void addToImageMetadata(JSONObject tags) {

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
    * @param callback an ExceptionCallback for asynchronously handling
    * exceptions
    *
    */
   public void submitEventIterator(Iterator<AcquisitionEvent> iter, ExceptionCallback callback) {
      submittedSequenceMonitorExecutor_.submit(() -> {
         Future iteratorFuture = null;
         try {
            iteratorFuture = Engine.getInstance().submitEventIterator(iter, this);
            iteratorFuture.get();
         } catch (InterruptedException ex) {
            iteratorFuture.cancel(true);
         } catch (ExecutionException ex) {
            callback.run(ex);
         }
      });
   }

   private void createXYPositions() {
      try {
         positions_ = new ArrayList<XYStagePosition>();
         int fullTileWidth = (int) Magellan.getCore().getImageWidth();
         int fullTileHeight = (int) Magellan.getCore().getImageHeight();
         positions_.add(new XYStagePosition(new Point2D.Double(Magellan.getCore().getXPosition(),
                 Magellan.getCore().getYPosition()), 0, 0));

      } catch (Exception e) {
         e.printStackTrace();
         Log.log("Problem with Acquisition's XY positions. Check acquisition settings");
         throw new RuntimeException();
      }
   }

   /**
    *
    * @param sliceIndex 0 based slice index
    * @return
    */
   public LinkedBlockingQueue<ExploreTileWaitingToAcquire> getTilesWaitingToAcquireAtSlice(int sliceIndex) {
      return queuedTileEvents_.get(sliceIndex);
   }

   public void acquireTileAtCurrentLocation() {
      double xPos, yPos, zPos;

      try {
         //get current XY and Z Positions
         zPos = Magellan.getCore().getPosition(Magellan.getCore().getFocusDevice());
         xPos = Magellan.getCore().getXPosition();
         yPos = Magellan.getCore().getYPosition();
      } catch (Exception ex) {
         Log.log("Couldnt get device positions from core");
         return;
      }

      int sliceIndex = (int) Math.round((zPos - zOrigin_) / zStep_);
      int posIndex = ((MagellanDataManager) dataSink_).getFullResPositionIndexFromStageCoords(xPos, yPos);
//      controls.setZLimitSliderValues(sliceIndex);

      submitEvents(new int[]{(int) ((MagellanDataManager) dataSink_).getXYPosition(posIndex).getGridRow()},
              new int[]{(int) ((MagellanDataManager) dataSink_).getXYPosition(posIndex).getGridCol()}, sliceIndex, sliceIndex);
   }

   public void acquireTiles(final int r1, final int c1, final int r2, final int c2) {
      //So it doesnt slow down GUI
      new Thread(() -> {
         int minZIndex = getZLimitMinSliceIndex();
         int maxZIndex = getZLimitMaxSliceIndex();

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
               int i = ((relativeCol % 2 == 0) ? relativeRow : (numRows - relativeRow - 1)) + numRows * relativeCol;
//               int i = (r - row1) + (1 + row2 - row1) * (c - col1);
               newPositionRows[i] = r;
               newPositionCols[i] = c;
            }
         }
         submitEvents(newPositionRows, newPositionCols, minZIndex, maxZIndex);

      }).start();

   }

   private void submitEvents(int[] newPositionRows, int[] newPositionCols, int minZIndex, int maxZIndex) {
      int[] posIndices = ((MagellanDataManager) dataSink_).getPositionIndices(newPositionRows, newPositionCols);
      List<XYStagePosition> allPositions = ((MagellanDataManager) dataSink_).getPositionList();
      List<XYStagePosition> selectedXYPositions = new ArrayList<XYStagePosition>();
      for (int i : posIndices) {
         selectedXYPositions.add(allPositions.get(i));
      }
      ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions
              = new ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>>();
      acqFunctions.add(positions(selectedXYPositions, posIndices));
      acqFunctions.add(AcqEventModules.zStack(minZIndex, maxZIndex + 1, zStep_, zOrigin_));
      if (settings_.channels_ != null) {
         ArrayList<ChannelSetting> channels = new ArrayList<ChannelSetting>();
         if (getChannels() != null) {
            for (String name : getChannels().getChannelNames()) {
               SingleChannelSetting s = getChannels().getChannelSetting(name);
               if (s.use_) {
                  channels.add(s);
               }
            }
         }
         acqFunctions.add(AcqEventModules.channels(channels));
      }

      Iterator<AcquisitionEvent> iterator = new AcquisitionEventIterator(
              new AcquisitionEvent(this), acqFunctions, monitorSliceIndices());

      //Get rid of duplicates, send to acquisition engine 
      Predicate<AcquisitionEvent> predicate = filterExistingEventsAndDisplayQueuedTiles();
      List<AcquisitionEvent> eventList = new LinkedList<AcquisitionEvent>();
      while (iterator.hasNext()) {
         AcquisitionEvent event = iterator.next();
         if (predicate.test(event)) {
            eventList.add(event);
         }
      }

      Function<AcquisitionEvent, AcquisitionEvent> removeTileToAcquireFn = (AcquisitionEvent e) -> {
         queuedTileEvents_.get(e.getZIndex()).remove(new ExploreTileWaitingToAcquire(e.getGridRow(),
                 e.getGridCol(), e.getZIndex(), e.getChannelConfig()));
         return e;
      };

      ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> list
              = new ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>>();
      Function<AcquisitionEvent, Iterator<AcquisitionEvent>> fn = (AcquisitionEvent t) -> {
         return eventList.iterator();
      };
      list.add(fn);

      submitEventIterator(new AcquisitionEventIterator(null, list, removeTileToAcquireFn), new ExceptionCallback() {
         @Override
         public void run(Exception e) {
            Log.log(e, true);
         }
      });
   }

   private Function<AcquisitionEvent, Iterator<AcquisitionEvent>> positions(
           List<XYStagePosition> positions, int[] posIndices) {
      return (AcquisitionEvent event) -> {
         Stream.Builder<AcquisitionEvent> builder = Stream.builder();
         if (positions == null) {
            builder.accept(event);
         } else {
            for (int index = 0; index < positions.size(); index++) {
               AcquisitionEvent posEvent = event.copy();
               posEvent.setGridCol(positions.get(index).getGridCol());
               posEvent.setGridRow(positions.get(index).getGridRow());
               posEvent.setX(positions.get(index).getCenter().x);
               posEvent.setY(positions.get(index).getCenter().y);
               posEvent.setAxisPosition(MagellanMD.POSITION_AXIS, posIndices[index]);
               builder.accept(posEvent);
            }
         }
         return builder.build().iterator();
      };
   }

   private Predicate<AcquisitionEvent> filterExistingEventsAndDisplayQueuedTiles() {
      return (AcquisitionEvent event) -> {
         try {
            //add tile tile to list waiting to acquire for drawing purposes
            if (!queuedTileEvents_.containsKey(event.getZIndex())) {
               queuedTileEvents_.put(event.getZIndex(), new LinkedBlockingQueue<ExploreTileWaitingToAcquire>());
            }

            ExploreTileWaitingToAcquire tile = new ExploreTileWaitingToAcquire(event.getGridRow(),
                    event.getGridCol(), event.getZIndex(), event.getChannelConfig());
            if (queuedTileEvents_.get(event.getZIndex()).contains(tile)) {
               return false; //This tile is already waiting to be acquired
            }
            queuedTileEvents_.get(event.getZIndex()).put(tile);
            return true;
         } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
         }
      };

   }

   private Function<AcquisitionEvent, AcquisitionEvent> monitorSliceIndices() {
      return (AcquisitionEvent event) -> {
         minSliceIndex_ = Math.min(minSliceIndex_, getZLimitMinSliceIndex());
         maxSliceIndex_ = Math.max(maxSliceIndex_, getZLimitMaxSliceIndex());
         return event;
      };
   }

   /**
    * get min slice index for according to z limit sliders
    *
    * @return
    */
   private int getZLimitMinSliceIndex() {
      return (int) Math.round((zTop_ - zOrigin_) / zStep_);
   }

   /**
    * get max slice index for current settings in explore acquisition
    */
   private int getZLimitMaxSliceIndex() {
      return (int) Math.round((zBottom_ - zOrigin_) / zStep_);
   }

   /**
    * get z coordinate for slice position
    */
   private double getZCoordinate(int sliceIndex) {
      return zOrigin_ + zStep_ * sliceIndex;
   }

   public void setZLimits(double zTop, double zBottom) {
      //Convention: z top should always be lower than zBottom
      zBottom_ = Math.max(zTop, zBottom);
      zTop_ = Math.min(zTop, zBottom);
   }

   @Override
   public double getZOrigin() {
      return zOrigin_;
   }

   @Override
   public double getZStep() {
      return ((ExploreAcqSettings) settings_).zStep_;
   }

   @Override
   public ChannelGroupSettings getChannels() {
      return settings_.channels_;
   }

   @Override
   public MagellanGenericAcquisitionSettings getAcquisitionSettings() {
      return settings_;
   }

   //slice and row/col index of an acquisition event in the queue
   public class ExploreTileWaitingToAcquire {

      public long row, col, sliceIndex;
      public String channelName = null;

      public ExploreTileWaitingToAcquire(long r, long c, int z, String ch) {
         row = r;
         col = c;
         sliceIndex = z;
         channelName = ch;
      }

      @Override
      public boolean equals(Object other) {
         String otherChannel = ((ExploreTileWaitingToAcquire) other).channelName;
         if (((ExploreTileWaitingToAcquire) other).col == col && ((ExploreTileWaitingToAcquire) other).row == row
                 && ((ExploreTileWaitingToAcquire) other).sliceIndex == sliceIndex) {
            if (otherChannel == null && channelName == null) {
               return true;
            }
            return otherChannel.equals(channelName);
         }
         return false;
      }

   }
}
