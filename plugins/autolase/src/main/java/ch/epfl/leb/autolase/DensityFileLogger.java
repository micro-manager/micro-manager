package ch.epfl.leb.autolase;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Logs the density and laser power levels to file.
 *
 * @author Holden
 */
public class DensityFileLogger implements LaserPowerMonitor, DensityMonitor {

   private String savePathStub = "AutoLase_densityLog";
   private String fullSaveName = null;
   private float minGoodDensity = 0;
   private float  maxGoodDensity = 0;
   private double laserMinStep = 0;
   private double laserMinValue = 0;
   private double laserMaxValue = 0;
   private PrintWriter fOut = null;
   private boolean isLogRunning = false;
   private long startTime = 0;
   private long elapsedTime = 0;

   public DensityFileLogger() {

   }

   public void laserPowerChanged(double newLaserPower) {
      if (fOut != null) {
         elapsedTime = System.currentTimeMillis() - startTime;
         fOut.format("%d, NaN, %.3f%n", elapsedTime, newLaserPower);
      }
   }

   public void densityChanged(double density) {
      if (fOut != null) {
         elapsedTime = System.currentTimeMillis() - startTime;
         fOut.format("%d, %.2f, NaN%n", elapsedTime, density);
      }
   }

   /*
    *  Return the full save path, appending date and time to savePathStub
    */
   private void updateSavePath() {
      //get current date and time
      DateFormat dateFormat = new SimpleDateFormat("_yyMMdd_HHmm");
      //get current date time with Date()
      Date date = new Date();
      //System.out.println(dateFormat.format(date));
      String dateStr = dateFormat.format(date);

      fullSaveName = savePathStub + dateStr + ".txt";
   }

   /*
    * Start recording density  and laser power to log file
    */
   public void startLog() {
      //get the file name
      updateSavePath();
      //open the file
      initialiseLogFile();
      startTime = System.currentTimeMillis();
      isLogRunning = true;
   }

   private void initialiseLogFile() {
      try {
         fOut = new PrintWriter(new FileWriter(fullSaveName));
         // write the current params to the file header
         fOut.println("Min good density: " + minGoodDensity);
         fOut.println("Max good density: " + maxGoodDensity);
         fOut.println("Min laser step: " + laserMinStep);
         fOut.println("Min laser val: " + laserMinValue);
         fOut.println("Max laser val: " + laserMaxValue);
         fOut.println("Time, Density, LaserVoltage");
      } catch (IOException ex) {
         throw new RuntimeException(ex);
      }
   }

   /*
    * Stop recording density  and laser power
    */
   public void stopLog() {
      if (fOut != null) {
         fOut.close();
         fOut = null;
      }
      isLogRunning = false;
   }

   public void dispose() {
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
   public double getLaserMinStep() {
      return laserMinStep;
   }

   /**
    * @param laserMinStep the laser_minStep to set
    */
   public void setLaserMinStep(double laserMinStep) {
      this.laserMinStep = laserMinStep;
   }

   /**
    * @return the laser_minValue
    */
   public double getLaserMinValue() {
      return laserMinValue;
   }

   /**
    * @param laserMinValue the laser_minValue to set
    */
   public void setLaserMinValue(double laserMinValue) {
      this.laserMinValue = laserMinValue;
   }

   /**
    * @return the laser_maxValue
    */
   public double getLaserMaxValue() {
      return laserMaxValue;
   }

   /**
    * @param laserMaxValue the laser_maxValue to set
    */
   public void setLaserMaxValue(double laserMaxValue) {
      this.laserMaxValue = laserMaxValue;
   }

   /**
    * @return the isLogRunning
    */
   public boolean isIsLogRunning() {
      return isLogRunning;
   }

}
