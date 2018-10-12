package ch.epfl.leb.autolase;

import javax.swing.JFrame;
import org.micromanager.graph.GraphData;
import org.micromanager.graph.GraphFrame;

/**
 * A DensityProfiler displays a live graph of the density.
 * 
 * @author Holden
 */
public class DensityProfiler implements DensityMonitor{
   private static final int NPOINTS_ = 100;
   private int nCur_ = 0;
   private GraphData densProfileData_;
   private double[] densData_;
   private boolean doUpdateProfiler = false;
           
   GraphFrame profileWin_ = null;
   
   @Override
   public void densityChanged(double density) {
      if (doUpdateProfiler){
         addDataPoint(density);
         updateProfiler(0,nCur_-1);
      }
   }

   /**
    * Starts the graph plotting
    */
   public void startProfiler(){
      densData_ = new double[NPOINTS_];
      if (densProfileData_ == null) {
         densProfileData_ = new GraphData();
      }
      densProfileData_.setData(densData_);
      updateProfiler(0,NPOINTS_);
      doUpdateProfiler = true;
   }
      
   /**
    * Updates the graph X limits.
    * 
    * @param xmin
    * @param xmax 
    */
   private void updateProfiler(double xmin, double xmax){
      if (profileWin_==null){
         profileWin_ = new GraphFrame();
         profileWin_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
         profileWin_.setTitle("Live density profile");
      }

      profileWin_.setAutoScale();
      profileWin_.setData(densProfileData_);
      profileWin_.setXLimits(xmin, xmax);
      profileWin_.setVisible(true);
   }
   
   /**
    * Stops the live graph update.
    * 
    */
   public void stopProfiler(){
      doUpdateProfiler=false;
      nCur_=0;
      for ( int ii=0; ii< densData_.length; ii++){
         densData_[ii]=0;
      }
      
   }

   public void dispose(){
      stopProfiler();
      profileWin_.dispose();
      profileWin_=null;
   }

   private void addDataPoint(double density) {
      if (nCur_<NPOINTS_){//just append the latest datapoint
         densData_[nCur_]=density;
         System.out.println("Current data point: " + densData_[nCur_]);
         nCur_++;
      } else{//shift everthing over one
         for(int ii = 0;ii<NPOINTS_-1;ii++){
            densData_[ii]=densData_[ii+1];
         }
         densData_[NPOINTS_-1] = density;
         System.out.println("Current data point: " + densData_[NPOINTS_-1]);
      }
         

      System.out.println("Current min: " + getMinValue(densData_));
      System.out.println("Current max: " + getMaxValue(densData_));
      densProfileData_.setData(densData_);
   }
   
   /**
    * Find maximum (largest) value in array using loop  
    */
   private static double getMaxValue(double [] numbers){  
       double maxValue = numbers[0];  
       for(int i=1;i<numbers.length;i++){  
           if(numbers[i] > maxValue){  
               maxValue = numbers[i];  
           }  
       }  
       return maxValue;  
   }  
     
   /**
    * Find minimum (lowest) value in array using loop  
    */
   private static double getMinValue(double [] numbers){  
       double minValue = numbers[0];  
       for(int i=1;i<numbers.length;i++){  
           if(numbers[i] < minValue){  
               minValue = numbers[i];  
           }  
       }  
       return minValue;  
   }  
  
}
