package ch.epfl.leb.autolase;

import java.util.List;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;

/**
 * LaseThread implements the laser controller. It decreases power if density is
 * above a certain maximum threshold and increases power if density is below a 
 * minimum threshold.
 * 
 * @author Thomas Pengo
 */
public class LaseThread implements Runnable, DensityMonitor {
    long WAIT_TIME = 1000;
    
    CMMCore core;
    
    boolean running = false;
    boolean stopping = false;
    
    double laserPower = 0;
    
    LaserConfig lc;
    
    /**
     * Stops the controller thread. 
     */
    public void stop() {
        stopping = true;
    }

    double curDensity = 0;
    double densityLower = 0, densityUpper = 1;
        
   ///////////////////////////////
    // Constructors and init     //
    ///////////////////////////////
    
    /**
     * Creates a new controller by tying it to a MicroManager instance and 
     * specifying the configuration for the device in LaserConfig.
     * 
     * @param core  The MicroManager instance
     * @param lc    The laser device configuration
     */
    public LaseThread(CMMCore core, LaserConfig lc) {
        this.core = core;
        this.lc = lc;

        init();
    }
    
    void init() {
        // TODO: Read minimums and maximums
    }

    ///////////////////////////////
    // Getters and setters       //
    ///////////////////////////////
    
    /**
     * Returns the density lower threshold.
     * 
     * @return 
     */
    public double getDensityLower() {
        return densityLower;
    }

    /**
     * Returns the density upper threshold.
     * 
     * @return 
     */
    public double getDensityUpper() {
        return densityUpper;
    }

    /**
     * Sets the density lower threshold to the specified level.
     * 
     * @param densityLower 
     */
    public void setDensityLower(double densityLower) {
        this.densityLower = densityLower;
    }

    /**
     * Sets the density upper threshold to the specified level.
     * 
     * @param densityUpper 
     */
    public void setDensityUpper(double densityUpper) {
        this.densityUpper = densityUpper;
    }
    
    /**
     * Starts or stops the instance.
     * 
     * @param running 
     */
    public void setRunning(boolean running) {
        this.running = running;
    }

    /**
     * Returns true if the controller thread is running.
     * 
     * @return 
     */
    public boolean isRunning() {
        return running;
    }
    
    /**
     * Set the laser to the minimum specified power.
     */
    public void setLaserToMinPower() {
        setPower(lc.minValue);
    }

    /**
     * Sets the laser to the startup power.
     */
    public void setLaserToStartPower(){
        setPower(lc.startValue);
    }
    
    ///////////////////////////////
    // Laser power monitor code  //
    ///////////////////////////////
    
    List <LaserPowerMonitor>  monitors = 
            Collections.synchronizedList(new ArrayList <LaserPowerMonitor> ());

    /**
     * Adds a new laser power monitor, which will get notified each time the 
     * laser power changes.
     * 
     * @param m 
     */
    public void addLaserPowerMonitor(LaserPowerMonitor m) {
        if (!monitors.contains(m))
            monitors.add(m);
    }
    
    /**
     * Removes the specified listener.
     * 
     * @param m 
     */
    public void removeLaserPowerMonitor(LaserPowerMonitor m) {
        monitors.remove(m);
    }
    
    /**
     * Removes all listeners.
     */
    public void clearMonitors() {
        monitors.clear();
    }

    ////////////////////////////////////////////
    // Logging                                //
    ////////////////////////////////////////////
    
    public String LOG_NAME = "AutoLase.LaseThread";
    public boolean verbose = true;
    private void log(String msg) {
        if (verbose)
            Logger.getLogger(LOG_NAME).log(Level.INFO, msg);
    }

    ///////////////////////////////
    // Main                      //
    ///////////////////////////////
    
    @Override
    public void run() {
        log("Start the laser control algorithm");
        
        // Set to minimum
        
        
        while(!stopping) {
            
            // Check running flag
            if (running) {

                // If below lowerThreshold
                if (curDensity < densityLower)
                   incPower();
                
                // If above upperThreshold
                if (curDensity > densityUpper)
                    decPower();
            }
            try {
                Thread.sleep(WAIT_TIME);
            } catch (InterruptedException ex) {
                Logger.getLogger(LaseThread.class.getName()).log(Level.SEVERE, null, ex);
                
                stopping = true;
            }
        }
        
        stopping = false;
        
    }

   @Override
   public void densityChanged(double density) {
        curDensity = density;
   }
   
   private void incPower() { setPower(laserPower+lc.minStep); }   
   private void decPower() { setPower(laserPower-lc.minStep); }
   
   private void setPower(double power) {
       if (power <= lc.maxValue && power >= lc.minValue) {
            try {
                // Change power
                core.setProperty(lc.deviceName, lc.propertyName, power);
                laserPower = power;
            } catch(Exception e) {
                core.logMessage("Autolase was not able to set the laser power of "+lc.deviceName+"/"+lc.propertyName+" to "+laserPower+": "+e.getMessage());
                return;
            }
            
            // Notify listeners
            for (LaserPowerMonitor lp : monitors) {
                lp.laserPowerChanged(laserPower);
            }
        } else 
           core.logMessage("Autolase: setPower to power: "+power+" is outside limits ("+lc.minValue+", "+lc.maxValue+"). No action taken.");
   }

}
