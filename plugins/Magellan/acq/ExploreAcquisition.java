/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import gui.GUI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import misc.Log;
import mmcorej.CMMCore;
import org.json.JSONArray;
import org.micromanager.MMStudio;
import org.micromanager.utils.ReportingUtils;

/**
 * A single time point acquisition that can dynamically expand in X,Y, and Z
 *
 * @author Henry
 */
public class ExploreAcquisition extends Acquisition {

    private static final int EXPLORE_EVENT_QUEUE_CAP = 250; //big so you can see a lot of tiles waiting to be acquired
    
    
   private volatile double zTop_, zBottom_;
   private volatile int lowestSliceIndex_ = 0, highestSliceIndex_ = 0;
   private ExecutorService eventAdderExecutor_ = Executors.newSingleThreadExecutor();
   private int imageFilterType_;
   private ConcurrentHashMap<Integer, LinkedBlockingQueue<ExploreTileWaitingToAcquire>> queuedTileEvents_ = new ConcurrentHashMap<Integer, LinkedBlockingQueue<ExploreTileWaitingToAcquire>>();
   private double zOrigin_;

   public ExploreAcquisition(ExploreAcqSettings settings) throws Exception {
      super(settings.zStep_);
      try {
         //start at current z position
         zTop_ = core_.getPosition(zStage_);
         zOrigin_ = zTop_;
         zBottom_ = core_.getPosition(zStage_);
      } catch (Exception ex) {
         Log.log("Couldn't get focus device position");
         throw new RuntimeException();
      }
      imageFilterType_ = settings.filterType_;
      initialize(settings.dir_, settings.name_, settings.tileOverlap_);
   }

   public void clearEventQueue() {
      events_.clear();
      queuedTileEvents_.clear();
   }

   public void abort() {
      if (this.isPaused()) {
         this.togglePaused();
      }
      eventAdderExecutor_.shutdownNow();
      //wait for shutdown
      try {
         //wait for it to exit
         while (!eventAdderExecutor_.awaitTermination(5, TimeUnit.MILLISECONDS)) {}
      } catch (InterruptedException ex) {
         ReportingUtils.showError("Unexpected interrupt whil trying to abort acquisition");
         //shouldn't happen
      }
      //abort all pending events
      events_.clear();     
      queuedTileEvents_.clear();
      //signal acquisition engine to start finishigng process
      try {
//          IJ.log("Adding finishing events");
         events_.put(AcquisitionEvent.createAcquisitionFinishedEvent(this));         
         events_.put(AcquisitionEvent.createEngineTaskFinishedEvent());
      } catch (InterruptedException ex) {
         ReportingUtils.showError("Unexpected interrupted exception while trying to abort"); //shouldnt happen
      }
      imageSink_.waitToDie();
      //image sink will call finish when it completes
   }
   
   /**
    * 
    * @param sliceIndex 0 based slice index
    * @return 
    */
   public LinkedBlockingQueue<ExploreTileWaitingToAcquire> getTilesWaitingToAcquireAtSlice(int sliceIndex) {
      return queuedTileEvents_.get(sliceIndex);
   }
   
   //called by acq engine
   public void eventAcquired(AcquisitionEvent e) {
      //remove from tile queue for overlay drawing purposes
      int sliceIndex = e.sliceIndex_;
      queuedTileEvents_.get(sliceIndex).remove(new ExploreTileWaitingToAcquire(e.xyPosition_.getGridRow(), e.xyPosition_.getGridCol(), sliceIndex));
   }

   public void acquireTiles(final int r1, final int c1, final int r2, final int c2) {
      eventAdderExecutor_.submit(new Runnable() {

         @Override
         public void run() {
            //update positionList and get index
            int[] posIndices = null;
            try {
               int row1, row2, col1, col2;
               //order tile indices properly
               if (r1 > r2) {
                  row1 = r2;
                  row2 = r1;
               } else {
                  row1 = r1;
                  row2 = r2;
               }
               if (c1 > c2) {
                  col1 = c2;
                  col2 = c1;
               } else {
                  col1 = c1;
                  col2 = c2;
               }

               //Get position Indices from manager based on row and column
               //it will create new metadata as needed
               int[] newPositionRows = new int[(row2 - row1 + 1) * (col2 - col1 + 1)];
               int[] newPositionCols = new int[(row2 - row1 + 1) * (col2 - col1 + 1)];
               for (int r = row1; r <= row2; r++) {
                  for (int c = col1; c <= col2; c++) {
                     int i = (r - row1) + (1 + row2 - row1) * (c - col1);
                     newPositionRows[i] = r;
                     newPositionCols[i] = c;
                  }
               }
               posIndices = getPositionManager().getPositionIndices(newPositionRows, newPositionCols);
            } catch (Exception e) {
               e.printStackTrace();
               ReportingUtils.showError("Problem with position metadata: couldn't add tile");
               return;
            }

            //create set of hardware instructions for an acquisition event
            for (int i = 0; i < posIndices.length; i++) {       
               //update lowest slice for the benefit of the zScrollbar in the viewer
               updateLowestAndHighestSlices();
               //Add events for each channel, slice            
               for (int sliceIndex = getMinSliceIndex(); sliceIndex <= getMaxSliceIndex(); sliceIndex++) {
                  try {
                     //in case interupt occurs in between blocking calls of a really big loop
                     if (Thread.interrupted()){
                        throw new InterruptedException();
                     }
                     //add tile tile to list waiting to acquire for drawing purposes
                     if (!queuedTileEvents_.containsKey(sliceIndex)) {
                        queuedTileEvents_.put(sliceIndex, new LinkedBlockingQueue<ExploreTileWaitingToAcquire>());
                     }
                     
                     ExploreTileWaitingToAcquire tile = new ExploreTileWaitingToAcquire(posManager_.getXYPosition(posIndices[i]).getGridRow(), 
                             posManager_.getXYPosition(posIndices[i]).getGridCol(), sliceIndex);
                     if (queuedTileEvents_.get(sliceIndex).contains(tile)) {
                        continue; //ignor commands for duplicates
                     }
                     queuedTileEvents_.get(sliceIndex).put(tile);
                     events_.put(new AcquisitionEvent(ExploreAcquisition.this, 0, 0, sliceIndex, posIndices[i], getZCoordinate(sliceIndex), 
                             posManager_.getXYPosition(posIndices[i]), null));
                  } catch (InterruptedException ex) {
                     //aborted acqusition
                     return;
                  }
               }
            }
         }
      });
   }

   @Override
   public double getZCoordinateOfSlice(int sliceIndex) {
      //No frames in explorer acquisition
      sliceIndex += lowestSliceIndex_;
      return zOrigin_ + zStep_ * sliceIndex;
   }

   @Override
   public int getSliceIndexFromZCoordinate(double z) {
      return (int) Math.round((z - zOrigin_) / zStep_) - lowestSliceIndex_;
   }

   /**
    * return the slice index of the lowest slice seen in this acquisition
    *
    * @return
    */
   public int getLowestExploredSliceIndex() {
      return lowestSliceIndex_;
   }

   public int getHighestExploredSliceIndex() {
      return highestSliceIndex_;
   }

   public void updateLowestAndHighestSlices() {
      //keep track of this for the purposes of the viewer
      lowestSliceIndex_ = Math.min(lowestSliceIndex_, getMinSliceIndex());
      highestSliceIndex_ = Math.max(highestSliceIndex_, getMaxSliceIndex());
   }

   /**
    * get min slice index for according to z limit sliders
    *
    * @return
    */
   public int getMinSliceIndex() {
      return (int) Math.round((zTop_ - zOrigin_) / zStep_);
   }

   /**
    * get max slice index for current settings in explore acquisition
    *
    * @return
    */
   public int getMaxSliceIndex() {
      return (int) Math.round((zBottom_ - zOrigin_) / zStep_);
   }

   /**
    * get z coordinate for slice position
    *
    * @return
    */
   public double getZCoordinate(int sliceIndex) {
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
         CMMCore core = MMStudio.getInstance().getCore();
         JSONArray pList = new JSONArray();
         return pList;
      } catch (Exception e) {
         ReportingUtils.showError("Couldn't create initial position list");
         return null;
      }
   }

   @Override
   public double getRank() {
       //get from gui so it can be changed dynamically within a single explore
      return GUI.getExploreRankSetting();
   }

   @Override
   public int getFilterType() {
      return imageFilterType_;
   }

    @Override
    public int getAcqEventQueueCap() {
        return EXPLORE_EVENT_QUEUE_CAP;
    }
   
   //slice and row/col index of an acquisition event in the queue
   public class ExploreTileWaitingToAcquire {
      public int row, col, sliceIndex;
      
      public ExploreTileWaitingToAcquire(int r, int c, int z) {
         row = r;
         col = c;
         sliceIndex = z;
      }
      
      @Override 
      public boolean equals(Object other) {
         return ((ExploreTileWaitingToAcquire) other).col == col && ((ExploreTileWaitingToAcquire) other).row == row && ((ExploreTileWaitingToAcquire) other).sliceIndex == sliceIndex;
      }
      
      
   }
}
