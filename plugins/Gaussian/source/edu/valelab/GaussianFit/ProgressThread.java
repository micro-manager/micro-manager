package edu.valelab.GaussianFit;


import edu.valelab.GaussianFit.data.GaussianSpotData;
import java.util.concurrent.BlockingQueue;


/**
 *
 * @author nico
 */
public class ProgressThread  implements Runnable {

   Thread t_;
   BlockingQueue<GaussianSpotData> sourceList_;



   public ProgressThread(BlockingQueue<GaussianSpotData> sourceList) {
      sourceList_ = sourceList;
   }

   public void init() {
      t_ = new Thread(this);
      t_.start();
   }

   public void join() throws InterruptedException {
      if (t_ != null)
         t_.join();
   }

   
   @Override
   public void run() {
      int maxNr = sourceList_.size();
      int size = maxNr;
      while (sourceList_ != null && size > 0) {
         ij.IJ.wait(2000);
         size = sourceList_.size();
         ij.IJ.showStatus("Fitting remaining Gaussians...");
         ij.IJ.showProgress(maxNr - size, maxNr);
      }
      ij.IJ.showStatus("");
   }
}
