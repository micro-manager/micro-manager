package acq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * Class to encapsulate several FixedAreaAcquisitions that are run in parallel
 * @author henrypinkard
 */
public class ParallelAcquisitionGroup {
   
   private MultipleAcquisitionManager multiAcqManager_;
   private CustomAcqEngine eng_;
   private ArrayList<FixedAreaAcquisition> acqs_;
   private volatile int activeIndex_;
   
   /**
    * constructor for a single acquisition (nothing actually in parallel
    */
   public ParallelAcquisitionGroup(final List<FixedAreaAcquisitionSettings> settingsList, 
           MultipleAcquisitionManager acqManager, CustomAcqEngine eng) {
      multiAcqManager_ = acqManager;
      eng_ = eng;
      acqs_ = new ArrayList<FixedAreaAcquisition>();
      //create all
      for (int i = 0; i < settingsList.size(); i++) {
         acqs_.add(new FixedAreaAcquisition(settingsList.get(i), multiAcqManager_, eng_, ParallelAcquisitionGroup.this));
      }
      //start first
      acqs_.get(0).readyForNextTimePoint();
   }

   /**
    * Called by acquisition when it is aborted so group doesn't get stuck
    * @param acq 
    */
   public void acqAborted(FixedAreaAcquisition acq) {
      if (activeIndex_ == acqs_.indexOf(acq)) {
         //if this one was active, move on to next
         finishedTimePoint(acq);
      }
   }
   
   /**
    * Called by acquisition to signal that it has completed its time point
    * @param acq 
    */
   public void finishedTimePoint(FixedAreaAcquisition acq) {
      int currentIndex = acqs_.indexOf(acq);   
      int nextIndex = (currentIndex + 1) % acqs_.size();
      //skip over finished acquisitions when determining which to run next
      for (int i = nextIndex; i < nextIndex + acqs_.size(); i++) {
         int index = i % acqs_.size();
         if (!acqs_.get(index).isFinished()) {
            //signal next acq to begin
            acqs_.get(nextIndex).readyForNextTimePoint();
            activeIndex_ = nextIndex;
            return;
         }
      }   
   }
   
   public boolean isPaused() {
      return acqs_.get(activeIndex_).isPaused();
   }
   
   public boolean isFinished() {
      return acqs_.get(activeIndex_).isFinished();
   }
   
   public BlockingQueue<AcquisitionEvent> getEventQueue() {
      return acqs_.get(activeIndex_).getEventQueue();
   }
   
   public void abort() {
      //TODO: check how this works
      acqs_.get(activeIndex_).abort();
   }

   
}
