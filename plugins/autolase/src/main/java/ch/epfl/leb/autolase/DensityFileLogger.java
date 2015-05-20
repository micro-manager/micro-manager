package ch.epfl.leb.autolase;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Logs the density and laser power levels to file.
 * 
 * @author Holden
 */
public class DensityFileLogger implements LaserPowerMonitor, DensityMonitor{

   private String savePathStub = "AutoLase_densityLog";
   private String fullSaveName = null;
   private float  minGoodDensity = 0, maxGoodDensity = 0;
   private double laser_minStep = 0;
   private double laser_minValue = 0;
   private double laser_maxValue = 0;
   private PrintWriter fOut = null;
   private boolean isLogRunning = false;
   private long startTime =0, elapsedTime = 0;
   
   public DensityFileLogger(){
      
   }
   public void laserPowerChanged(double newLaserPower) {
      if (fOut!=null){
         elapsedTime = System.currentTimeMillis() - startTime; 
         fOut.format("%d, NaN, %.3f%n", elapsedTime, newLaserPower);
      }
   }

   public void densityChanged(double density) {
      if (fOut!=null){
         elapsedTime = System.currentTimeMillis() - startTime; 
         fOut.format("%d, %.2f, NaN%n", elapsedTime, density);
      }
   }

   /*
    *  Return the full save path, appending date and time to savePathStub
    */
   private void updateSavePath(){
      //get current date and time
      DateFormat dateFormat = new SimpleDateFormat("_yyMMdd_HHmm");
      //get current date time with Date()
      Date date = new Date();
      //System.out.println(dateFormat.format(date));
      String dateStr = dateFormat.format(date);

      fullSaveName = savePathStub+dateStr+".txt";
   }

   /*
    * Start recording density  and laser power to log file
    */
   public void startLog(){
      //get the file name
      updateSavePath();
      //open the file
      initialiseLogFile();
      startTime =System.currentTimeMillis(); 
      isLogRunning = true;
   }
   
   private void initialiseLogFile(){
      try {
         fOut = new PrintWriter(new FileWriter(fullSaveName));
         // write the current params to the file header
         fOut.println("Min good density: "+minGoodDensity);
         fOut.println("Max good density: "+ maxGoodDensity);
         fOut.println("Min laser step: " + laser_minStep); 
         fOut.println("Min laser val: " + laser_minValue);
         fOut.println("Max laser val: " + laser_maxValue);
         fOut.println("Time, Density, LaserVoltage");
      } catch (IOException ex) {
         throw new RuntimeException(ex);
      }
   }
   
   /*
    * Stop recording density  and laser power 
    */
   public void stopLog(){
      if (fOut!=null){
         fOut.close();
         fOut=null;
      }
      isLogRunning = false;
   }
   
   public void dispose(){
      stopLog();   
   }
   
   //GETTERS AND SETTERS BELOW HERE
   /**
    * @return the savePathStub
    */
   public String getSavePathStub() {
      return savePathStub;
   }

   /**
    * @param savePathStub the savePathStub to set
    */
   public void setSavePathStub(String savePathStub) {
      this.savePathStub = savePathStub;
   }

   /**
    * @return the minGoodDensity
    */
   public float getMinGoodDensity() {
      return minGoodDensity;
   }

   /**
    * @param minGoodDensity the minGoodDensity to set
    */
   public void setMinGoodDensity(float minGoodDensity) {
      this.minGoodDensity = minGoodDensity;
   }

   /**
    * @return the maxGoodDensity
    */
   public float getMaxGoodDensity() {
      return maxGoodDensity;
   }

   /**
    * @param maxGoodDensity the maxGoodDensity to set
    */
   public void setMaxGoodDensity(float maxGoodDensity) {
      this.maxGoodDensity = maxGoodDensity;
   }

   /**
    * @return the laser_minStep
    */
   public double getLaser_minStep() {
      return laser_minStep;
   }

   /**
    * @param laser_minStep the laser_minStep to set
    */
   public void setLaser_minStep(double laser_minStep) {
      this.laser_minStep = laser_minStep;
   }

   /**
    * @return the laser_minValue
    */
   public double getLaser_minValue() {
      return laser_minValue;
   }

   /**
    * @param laser_minValue the laser_minValue to set
    */
   public void setLaser_minValue(double laser_minValue) {
      this.laser_minValue = laser_minValue;
   }

   /**
    * @return the laser_maxValue
    */
   public double getLaser_maxValue() {
      return laser_maxValue;
   }

   /**
    * @param laser_maxValue the laser_maxValue to set
    */
   public void setLaser_maxValue(double laser_maxValue) {
      this.laser_maxValue = laser_maxValue;
   }

   /**
    * @return the isLogRunning
    */
   public boolean isIsLogRunning() {
      return isLogRunning;
   }
   
}
