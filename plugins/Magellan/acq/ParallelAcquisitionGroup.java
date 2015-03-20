package acq;

import ij.IJ;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.utils.ReportingUtils;

/**
 * Class to encapsulate several FixedAreaAcquisitions that are run in parallel
 */
public class ParallelAcquisitionGroup implements AcquisitionEventSource {

   private MultipleAcquisitionManager multiAcqManager_;
   protected List<FixedAreaAcquisition> acqs_;
   private volatile int activeIndex_;
   private LinkedBlockingQueue<AcquisitionEvent> pendingFinishingEvents_ = new LinkedBlockingQueue<AcquisitionEvent>();
   private ExecutorService parallelGroupExecutor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {

      @Override
      public Thread newThread(Runnable r) {
         return new Thread(r, "Parallel acq group thread");
      }
   });

   /**
    * constructor for a single acquisition (nothing actually in parallel)
    */
   public ParallelAcquisitionGroup(final List<FixedAreaAcquisitionSettings> settingsList,
           MultipleAcquisitionManager acqManager) {
      multiAcqManager_ = acqManager;
      acqs_ = new ArrayList<FixedAreaAcquisition>();
      //create all
      for (int i = 0; i < settingsList.size(); i++) {
         acqs_.add(new FixedAreaAcquisition(settingsList.get(i), ParallelAcquisitionGroup.this));
      }
      try {
         //start first
         acqs_.get(0).signalReadyForNextTP();
      } catch (Exception ex) {
         ReportingUtils.showError("Couldn't start acq group");
         throw new RuntimeException();
      }
      //now that first one is started, return so that multi acquisition manager has a reference to the group for aborting purposes
      //each acquisition will call finished time point when it is done, allowing parallel group to move to the next
      //in the case that an individual acqusiition is aborted, it will call acqAborted so parallel group knows to move on
   }

   public void signalAborted(FixedAreaAcquisition acq) {
      synchronized (this) {
         if (multiAcqManager_ != null) {
            //just so it shows as aborted on GUI
            multiAcqManager_.markAsAborted(acq.getSettings());
         }
         //-if abort happend while acquisition was acitve, it may have never reached 
         //the end of time point cylic barrier that the parallel group is waiting on ( signalReaadyForNextTP() )
         //In that case, parallel group needs finished time point signal to move on to the next one
         //-If abort happend while acquisition was not running, the parallel group needs to to pass its
         //finishing event to the acqusition engine to so that it gets properly finished
         //-by the time this is called, event generator is shut down, which means finishedTimePoint
         //would have already been called for this acquisition
         try {
            //so wait for any pending calls to finish timepoint to complete
            parallelGroupExecutor_.submit(new Runnable() {

               @Override
               public void run() {
               }
            }).get();
         } catch (Exception e) {
            ReportingUtils.showError("Unexpected exeption whiule aborting"); //no way this happens
         }
         //now if this acquisition is the active one
         if (activeIndex_ == acqs_.indexOf(acq)) {
            // move on to next   
            //if this one is active, it never got to call finishedTimePoint
            //A finishing time point event has been added, so wait for that to get read
            //and propogate through to finish the acq. Then call finished time point
            //to switch to next in group or finish group
            acq.imageSink_.waitToDie();
            finishedTimePoint(acq);
         } else {
            try {
               //This acqusition is not the one actively generating events, wait for it to finsih before retuyrning      
               //If this acqusitions queue is not actively being read, we want to 
               //propogate the finishing event which should be the only one left in its queue
               AcquisitionEvent finishEvent = acq.events_.poll();
               pendingFinishingEvents_.put(finishEvent);
               //this method is synchronized with finishedTimePoint so active index cant change while its running
               acqs_.get(activeIndex_).events_.put(AcquisitionEvent.createReQuerieEventQueueEvent()); //make sure engine isnt stuck
               //dont return until acqusition is marked as finished
               acq.imageSink_.waitToDie();
            } catch (InterruptedException ex) {
               ReportingUtils.showError("Unexpected exception while propogating finishing event");
            }
         }
      }
   }

   /**
    * Called by acquisition to signal that it has completed its time point
    *
    * @param acq *
    */
   public void finishedTimePoint(final FixedAreaAcquisition acq) {
      synchronized (this) {
         parallelGroupExecutor_.submit(new Runnable() {

            @Override
            public void run() {
               int currentIndex = acqs_.indexOf(acq);
               int nextIndex = (currentIndex + 1) % acqs_.size();
               //skip over finished acquisitions when determining which to run next
               for (int i = nextIndex; i < nextIndex + acqs_.size(); i++) {
                  int index = i % acqs_.size();
                  if (!acqs_.get(index).isFinished()) {
                     try {
                        acqs_.get(index).signalReadyForNextTP();
                     } catch (Exception ex) {
                        //This can happen in rare case when acqusition has been aborted since calling finishedTimePoint
                        continue;
                     }
                     activeIndex_ = index;
                     //add a dummy event to previous acqusiiton queue to get acq engine out of blocking on getNextEvent
                     acqs_.get(currentIndex).events_.add(AcquisitionEvent.createReQuerieEventQueueEvent());
                     return;
                  }
               }
               //all acquisitions finished, shutdown executor
               parallelGroupExecutor_.shutdown();
               //let the engine return from its current task
               pendingFinishingEvents_.add(AcquisitionEvent.createEngineTaskFinishedEvent());
               //make sure it gets the message
               acqs_.get(currentIndex).events_.add(AcquisitionEvent.createReQuerieEventQueueEvent());
               if (multiAcqManager_ != null) {
                  multiAcqManager_.parallelAcqGroupFinished();
               }
            }
         });
      }
   }

   public boolean isFinished() {
      return acqs_.get(activeIndex_).isFinished();
   }

   @Override
   public AcquisitionEvent getNextEvent() throws InterruptedException {
      return pendingFinishingEvents_.isEmpty() ? acqs_.get(activeIndex_).getNextEvent() : pendingFinishingEvents_.take();
   }

   /**
    * abort all acquisitions in group Individual acquisitions can be aborted by
    * Xing their windows,
    */
   public void abort() {
      for (Acquisition acq : acqs_) {
         acq.abort();
      }
   }
}
