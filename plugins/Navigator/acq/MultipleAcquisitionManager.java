/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package acq;

import gui.GUI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class MultipleAcquisitionManager {
   
   private ArrayList<FixedAreaAcquisitionSettings> acquisitions_ = new ArrayList<FixedAreaAcquisitionSettings>();
   private String[] acqStatus_;
   private GUI gui_;
   private CustomAcqEngine eng_;
   private volatile boolean running_ = false;
   private Thread managerThread_;
   private volatile FixedAreaAcquisition currentAcq_;
   
   public MultipleAcquisitionManager(GUI gui, CustomAcqEngine eng ) {
      gui_ = gui;
      acquisitions_.add(new FixedAreaAcquisitionSettings());
      eng_ = eng;
      eng_.setMultiAcqManager(this);
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
   
   public boolean moveUp(int index) {
      if (index > 0) {
         acquisitions_.add(index-1, acquisitions_.remove(index));
         return true;
      }
      return false;
   }
   
   public boolean moveDown(int index) {      
      if (index < acquisitions_.size() - 1) {
         acquisitions_.add(index+1, acquisitions_.remove(index));
         return true;
      }
      return false;
   }
   
   public void addNew() {
      acquisitions_.add(new FixedAreaAcquisitionSettings());
   }
   
   public void remove(int index) {
      if (index != -1 && acquisitions_.size() > 1) {
         acquisitions_.remove(index);
         if (index == acquisitions_.size()) {
            index--;
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
      //abort current acquisition
      if (currentAcq_ != null) {
         currentAcq_.abort();
      }      
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
            for (FixedAreaAcquisitionSettings settings : acquisitions_) {               
               if(managerThread_.isInterrupted()) {
                  break; //user aborted
               }
               acqStatus_[acquisitions_.indexOf(settings)] = "Running";
               gui_.repaint();
               currentAcq_ = eng_.runFixedAreaAcquisition(settings);
               while (currentAcq_ != null) {
                  try {
                     Thread.sleep(50);
                  } catch (InterruptedException ex) {
                     managerThread_.interrupt();
                  }
               }
               acqStatus_[acquisitions_.indexOf(settings)] = "Finished";
               gui_.repaint();
            } 
            running_ = false;
            acqStatus_ = null;            
            gui_.enableMultiAcquisitionControls(true);            
         }
      }, "Multiple acquisition manager thread");
     managerThread_.start();
   }
   
   /**
    * Called by fixed area acquisition when it is finished so that manager knows to move onto next one
    */
   public void acquisitionFinished() {
      System.out.println("Acquisition finished");
      currentAcq_ = null;
   }
   
}
