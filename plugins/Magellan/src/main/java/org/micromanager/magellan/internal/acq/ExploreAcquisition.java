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
package org.micromanager.magellan.internal.acq;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.Log;
import org.json.JSONArray;
import org.micromanager.data.DataProvider;

/**
 * A single time point acquisition that can dynamically expand in X,Y, and Z
 *
 * @author Henry
 */
public class ExploreAcquisition extends Acquisition {

   private volatile double zTop_, zBottom_;
   //Map with slice index as keys used to get rid of duplicate events
   private ConcurrentHashMap<Integer, LinkedBlockingQueue<ExploreTileWaitingToAcquire>> queuedTileEvents_ = new ConcurrentHashMap<Integer, LinkedBlockingQueue<ExploreTileWaitingToAcquire>>();
   private ArrayList<Future> submittedStreams_ = new ArrayList<Future>();
   public final ExploreAcqSettings settings_;

   public ExploreAcquisition(ExploreAcqSettings settings) {
      super(settings);
      settings_ = settings;
      channels_ = settings.channels_;
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
      initialize(settings.dir_, settings.name_, settings.tileOverlap_, settings.zStep_);
   }

   public void start() {
      //wait for user input
   }

   /**
    *
    * @param sliceIndex 0 based slice index
    * @return
    */
   public LinkedBlockingQueue<ExploreTileWaitingToAcquire> getTilesWaitingToAcquireAtSlice(int sliceIndex) {
      return queuedTileEvents_.get(sliceIndex);
   }

   @Override
   public void abort() {
      queuedTileEvents_.clear();
      super.abort();
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
      int posIndex = dataProvider_.getFullResPositionIndexFromStageCoords(xPos, yPos);
//      controls.setZLimitSliderValues(sliceIndex);

      submitEvents(new int[]{(int) dataProvider_.getXYPosition(posIndex).getGridRow()},
              new int[]{(int) dataProvider_.getXYPosition(posIndex).getGridCol()}, sliceIndex, sliceIndex);
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
      int[] posIndices = dataProvider_.getPositionIndices(newPositionRows, newPositionCols);
      ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions
              = new ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>>();
      acqFunctions.add(positions(posIndices, dataProvider_.getPositionList()));
      acqFunctions.add(zStack(minZIndex, maxZIndex + 1));
      if (channels_ != null) {
         acqFunctions.add(channels(channels_));
      }

      Stream<AcquisitionEvent> eventStream = makeEventStream(acqFunctions);
      eventStream = eventStream.map(monitorSliceIndices());

      //Get rid of duplicates, send to acquisition engine 
      eventStream = eventStream.filter(filterExistingEventsAndDisplayQueuedTiles());
      //Do a terminal operation now, so that tiles explore tiles waiting to collect can be shown
      List<AcquisitionEvent> eventList = eventStream.collect(Collectors.toList());
      Stream<AcquisitionEvent> newStream = eventList.stream();
      //Add a function that removes from queue of waiting tiles after each one is done
      newStream = newStream.map((AcquisitionEvent e) -> {
         queuedTileEvents_.get(e.zIndex_).remove(new ExploreTileWaitingToAcquire(e.xyPosition_.getGridRow(), e.xyPosition_.getGridCol(),
                 e.zIndex_, e.channelName_));
         return e;
      });

      MagellanEngine.getInstance().submitEventStream(newStream, this);
   }

   private Predicate<AcquisitionEvent> filterExistingEventsAndDisplayQueuedTiles() {
      return (AcquisitionEvent event) -> {
         try {
            //add tile tile to list waiting to acquire for drawing purposes
            if (!queuedTileEvents_.containsKey(event.zIndex_)) {
               queuedTileEvents_.put(event.zIndex_, new LinkedBlockingQueue<ExploreTileWaitingToAcquire>());
            }

            ExploreTileWaitingToAcquire tile = new ExploreTileWaitingToAcquire(event.xyPosition_.getGridRow(),
                    event.xyPosition_.getGridCol(), event.zIndex_, event.channelName_);
            if (queuedTileEvents_.get(event.zIndex_).contains(tile)) {
               return false; //This tile is already waiting to be acquired
            }
            queuedTileEvents_.get(event.zIndex_).put(tile);
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

   public double getZTop() {
      return zTop_;
   }

   public double getZBottom() {
      return zBottom_;
   }

   @Override
   protected JSONArray createInitialPositionList() {
      try {
         //create empty position list that gets filled in as tiles are explored
         JSONArray pList = new JSONArray();
         return pList;
      } catch (Exception e) {
         Log.log("Couldn't create initial position list", true);
         return null;
      }
   }

   @Override
   protected void shutdownEvents() {
      for (Future f : submittedStreams_) {
         f.cancel(true);
      }
   }

   @Override
   public boolean waitForCompletion() {
      for (Future f : submittedStreams_) {
         while (!f.isDone()) {
            try {
               Thread.sleep(5);
            } catch (InterruptedException ex) {
               Log.log("Interrupt while waiting for finish");
            }
         }
      }
      while (!finished_) {
         
      }
      return true;
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
