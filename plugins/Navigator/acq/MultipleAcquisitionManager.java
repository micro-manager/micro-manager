/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import gui.GUI;
import java.util.ArrayList;
import java.util.Arrays;
import javax.swing.JOptionPane;

/**
 *
 * @author Henry
 */
public class MultipleAcquisitionManager {
   
   private ArrayList<FixedAreaAcquisitionSettings> acquisitions_ = new ArrayList<FixedAreaAcquisitionSettings>();
   private ArrayList<Integer> numberInGroup_ = new ArrayList<Integer>();
   private String[] acqStatus_;
   private GUI gui_;
   private CustomAcqEngine eng_;
   private volatile boolean running_ = false;
   private Thread managerThread_;
   private volatile ParallelAcquisitionGroup currentAcqs_;
   
   public MultipleAcquisitionManager(GUI gui, CustomAcqEngine eng ) {
      gui_ = gui;
      acquisitions_.add(new FixedAreaAcquisitionSettings());
      eng_ = eng;
      eng_.setMultiAcqManager(this);
      numberInGroup_.add(1);
   }
      
   public FixedAreaAcquisitionSettings getAcquisition(int index) {
      return acquisitions_.get(index);
   }
   
   public int getSize() {
      return acquisitions_.size();
   }
   
   public String getAcquisitionName(int index) {
      return acquisitions_.get(index).name_;
   }
   
   /**
    * @return change in position of selected acq
    */
   public int moveUp(int index) {
      if (index == 0) {
         //nothing to do
         return 0;
      } else if (getIndexInGroup(index) != 0) {
         //if its within group, move up within group      
         acquisitions_.add(index - 1, acquisitions_.remove(index));
         return -1;
      } else {
         //move this group above entire group above
         int groupIndex = getGroupIndex(index);
         int insertIndex = getFirstIndexOfGroup(groupIndex - 1);
         //extract index should be last one in this group
         int extractIndex = getFirstIndexOfGroup(groupIndex) + getGroupSize(index) - 1;
         //remove in reverse order and readd to new position
         int groupSize = getGroupSize(index);
         for (int i = 0; i < groupSize; i++) {
            acquisitions_.add(insertIndex, acquisitions_.remove(extractIndex));
         }
         //swap num in group with one above
         numberInGroup_.add(groupIndex - 1, numberInGroup_.remove(groupIndex));
         return insertIndex - index;
      }
   }
   
   public int moveDown(int index) {
      if (index == acquisitions_.size() - 1) {
         //nothing to do
         return 0;
      } else if (getIndexInGroup(index) != getGroupSize(index) - 1) {
         //if its within group, move down within group      
         acquisitions_.add(index + 1, acquisitions_.remove(index));
         return 1;
      } else {
         //move group below above this group   
         int groupIndex = getGroupIndex(index);
         int insertIndex = getFirstIndexOfGroup(getGroupIndex(index) );
         int extractIndex = getFirstIndexOfGroup(getGroupIndex(index) + 1) + numberInGroup_.get(getGroupIndex(index) + 1) - 1;
         //remove in reverse order and readd to new position
         int groupSize = numberInGroup_.get(getGroupIndex(index) + 1);
         for (int i = 0; i < groupSize; i++) {
            acquisitions_.add(insertIndex, acquisitions_.remove(extractIndex));
         }     
         //swap num in group below with this one 
         numberInGroup_.add(groupIndex, numberInGroup_.remove(groupIndex + 1));
         return - index + extractIndex;
      }
   }
   
   public void addNew() {
      acquisitions_.add(new FixedAreaAcquisitionSettings());
            numberInGroup_.add(1);
   }
   
   public void remove(int index) {
      //must always have at least one acquisition
      if (index != -1 && acquisitions_.size() > 1) {
         acquisitions_.remove(index);
         int groupIndex = getGroupIndex(index);
         if (numberInGroup_.get(groupIndex) == 1) {
            numberInGroup_.remove(groupIndex);
         } else {
            numberInGroup_.add(groupIndex,numberInGroup_.remove(groupIndex) - 1);
         }
      }
   }
   
   public int getGroupIndex(int acqIndex) {
      int groupIndex = 0;
      int sum = -1;
      for (Integer i : numberInGroup_) {
         sum += i;
         if (acqIndex <= sum) {
            return groupIndex;
         }
         groupIndex++;
      }
      //shouldn't ever happen
      throw new RuntimeException();
   }
   
   public int getIndexInGroup(int index) {
      if (getGroupIndex(index) == 0) {
         return index;
      } else {
         int groupIndex = getGroupIndex(index);
         int firstInGroup = index;
         while (getGroupIndex(firstInGroup - 1) == groupIndex) {
            firstInGroup--;
         }
         return index - firstInGroup;
      }
   }
   
   public int getFirstIndexOfGroup(int groupIndex) {
      for (int i = 0; i < acquisitions_.size(); i++) {
         if (getGroupIndex(i) == groupIndex) {
            return i;
         }
      }
      return numberInGroup_.size();
   }
   
   public int getGroupSize(int index) {
      return numberInGroup_.get(getGroupIndex(index));
   }
   
   public void addToParallelGrouping(int index) {
      //make sure there is one below 
      if (index != acquisitions_.size() - 1) {
         //if one below is in same group, try again with one below that
         if (getGroupIndex(index) == getGroupIndex(index+1)) {
            addToParallelGrouping(index + 1);
            return;
         }
         //group em
         int gIndex = getGroupIndex(index);
         numberInGroup_.add(gIndex,numberInGroup_.remove(gIndex) + numberInGroup_.remove(gIndex));      
      }
   }

   public void removeFromParallelGrouping(int index) {
      int groupIndex = getGroupIndex(index);
      int groupSize = getGroupSize(index);
      if (groupSize != 1) {
         //if theyre actually grouped to begin with, remove this one from group
         //move higher or lower depending on position in group
         //find position in group and see if its on top or bottom half

         if (getIndexInGroup(index) > groupSize / 2.0) {
            //move down
            numberInGroup_.add(groupIndex, numberInGroup_.remove(groupIndex) - 1);
            numberInGroup_.add(groupIndex+1,1);
            acquisitions_.add(getFirstIndexOfGroup(groupIndex+1), acquisitions_.remove(index));
         } else {
            //move up
            numberInGroup_.add(groupIndex, numberInGroup_.remove(groupIndex) - 1);
            numberInGroup_.add(groupIndex,1);
            acquisitions_.add(getFirstIndexOfGroup(groupIndex), acquisitions_.remove(index));
         }
      } 
   }
   
   public String getAcqStatus(int index) {
      if (acqStatus_ == null || index >= acqStatus_.length) {
         return "";
      }
      return acqStatus_[index];
   }
   
   public boolean isRunning() {
      return running_;
   }
   
   public void abort() {
      int result = JOptionPane.showConfirmDialog(null, "Abort current acquisition and cancel future ones?", "Finish acquisitions?", JOptionPane.OK_CANCEL_OPTION);
      if (result != JOptionPane.OK_OPTION) {
         return;
      }
      
      //stop future acquisitions
      managerThread_.interrupt();
      //abort current parallel acquisition group
      if (currentAcqs_ != null) {
         currentAcqs_.abort();
      }      
      //abort blocks until all the acquisition stuff is closed, so can reset GUI here
      //this call is redundant to normal finishing mechanism but useful in case of acq errors
      multipleAcquisitionsFinsihed();           
   }

   public void runAllAcquisitions() {
     managerThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            gui_.enableMultiAcquisitionControls(false); //disallow changes while running
            running_ = true;
            acqStatus_ = new String[acquisitions_.size()];
            Arrays.fill(acqStatus_, "Waiting");
            gui_.repaint();
            //run acquisitions
            for (int groupIndex = 0; groupIndex < numberInGroup_.size(); groupIndex++) {
               if (managerThread_.isInterrupted()) {
                  break; //user aborted
               }
               //mark as running
               for (int i = 0; i < numberInGroup_.get(groupIndex); i++) {
                  acqStatus_[getFirstIndexOfGroup(groupIndex) + i] = "Running";
                  gui_.repaint();
               }
               //run one or more acquisitions in parallel group 
               currentAcqs_ = eng_.runInterleavedAcquisitions(acquisitions_.subList(
                       getFirstIndexOfGroup(groupIndex), getFirstIndexOfGroup(groupIndex) + getGroupSize(getFirstIndexOfGroup(groupIndex)) ));
               while (currentAcqs_ != null) {
                  try {
                     Thread.sleep(50);
                  } catch (InterruptedException ex) {
                     managerThread_.interrupt();
                  }
               }
               //mark as finished, unless already marked as aborted
               for (int i = 0; i < numberInGroup_.get(groupIndex); i++) {
                  if (!getAcqStatus( getFirstIndexOfGroup(groupIndex) + i).equals("Aborted") ) {
                     acqStatus_[getFirstIndexOfGroup(groupIndex) + i] = "Finished";
                  }
               }
               gui_.repaint();
            }
            multipleAcquisitionsFinsihed();          
         }
      }, "Multiple acquisition manager thread");
     managerThread_.start();
   }
   
   private void multipleAcquisitionsFinsihed() {
      running_ = false;
      acqStatus_ = null;
      gui_.enableMultiAcquisitionControls(true);
   }
   
   public void markAsAborted(FixedAreaAcquisitionSettings settings) {
      if (acqStatus_ != null) {
         acqStatus_[acquisitions_.indexOf(settings)] = "Aborted";
         gui_.repaint();
      }
   }
   
   /**
    * Called by parallel acquisition group when it is finished so that manager knows to move onto next one
    */
   public void parallelAcqGroupFinished() {
      currentAcqs_ = null;
   }
   
}
