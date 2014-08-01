package ch.epfl.leb.autolase;

import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.CMMCore;
import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;

/**
 * Main class for Autolase, an automatic activation control plugin for Micro-Manager
 * 
 * @author Thomas Pengo
 */
public enum AutoLase {
    INSTANCE;
        
    LaseThread laseThread;
    DensityThread densityThread;
    
    LaserConfig config = new LaserConfig();
    
    Thread lt1;
    Thread dt1;
    Thread dmmt;
    
    CMMCore core;
    MMStudio gui;
    
    AutoLaseDialog dlg;
    
    DensityMap dmm = new DensityMap();
    DensityFileLogger densityLogger=null;
    DensityProfiler densityProfiler =null;

    boolean logFileIsStarted = false;
    
    /**
     * Set the laser power to the minimum allowed power.
     * 
     */
    public void setLaserToMinPower() {
        if(laseThread!=null)
            laseThread.setLaserToMinPower();
    }

    /**
     * Set the laser power to the initial power.
     */
    public void setLaserToStartPower() {
        if(laseThread!=null)
            laseThread.setLaserToStartPower();
    }
    
    ////////////////////////////////////////////
    // Logging                                //
    ////////////////////////////////////////////
    
    public String LOG_NAME = "AutoLase";
    public boolean verbose = true;
    void log(String msg) {
        if (verbose)
            Logger.getLogger(LOG_NAME).log(Level.INFO, msg);
    }

    ////////////////////////////////////////////
    // MMPlugin adaptor                       //
    ////////////////////////////////////////////
    
    /**
     * Always call this method before doing anything with the plugin. This
     * means that it actually needs to be called by MM or by another plugin
     * to work.
     * 
     * @param app 
     */
    public void setup(ScriptInterface app) {
        gui = (MMStudio) app;
        core = gui.getMMCore();
        
        log("Starting lasing thread and density calculation thread");
        
        // Prepare laser
        if (laseThread == null)
            laseThread = new LaseThread(core,config);
        
        // Prepare density monitor
        if (densityThread == null)
            densityThread = new DensityThread(new MMCamera(core));
    }
    
    /**
     * Show the dialog box for interactive autolasing.
     */
    public void show() {
        if (dlg==null) {
            dlg = new AutoLaseDialog(gui.getFrame(), false, this);
        }
        dlg.setVisible(true);        
    }
    
    /**
     * Stops all auxiliary threads.
     */
    public void dispose() {
        if (laseThread!=null) laseThread.stop();
        if (densityThread!=null) densityThread.stop();
        if (dmm!=null) dmm.stop();
        if (densityLogger!=null) {
           densityLogger.stopLog();
           densityLogger.dispose();
        }
        if (densityProfiler!=null) {
          densityProfiler.dispose();
       }
        
    }
    
    
    ////////////////////////////////////////////
    // Getters and setters                    //
    ////////////////////////////////////////////

    /**
     * Specify the minimum acceptable density. 
     * 
     * @param m 
     */
    public void setMinGoodDensity(float m) {
        log("Setting min good density to "+m);
        
        if (dmm != null)
            dmm.setMinGoodDensity(m);
        else
            log("Density monitor not initialized. No action taken.");
        
        if (laseThread != null)
            laseThread.setDensityLower(m);
        
    }
    
    /**
     * Specify the maximum acceptable density.
     * 
     * @param m 
     */
    public void setMaxGoodDensity(float m) {
        log("Setting max good density to "+m);
        
        if (dmm != null)
            dmm.setMaxGoodDensity(m);
        else
            log("Density monitor not initialized. No action taken.");

        if (laseThread != null)
            laseThread.setDensityUpper(m);
    }

    /**
     * Set the threshold to indicate the presence of (at least) one fluorophore.
     * 
     * @param threshold 
     */
    public void setThreshold(int threshold) {
        log("Setting threshold density to "+threshold);
        
        if (densityThread != null) {
            densityThread.setThreshold(threshold);
        
            dmm.clearMap();
        } else
            log("Density thread not initialized. No action taken.");
    }
    
    @Deprecated
    public void preBleach() {
        log ("Pre-bleaching not implemented yet.");
        
        // TODO Prebleacing
    }
    
    /**
     * Change the camera to the specified one.
     * 
     * @param c 
     */
    public void setCamera(Camera c) {
        densityThread.setCamera(c);
    }
    
    /**
     * Get the current camera.
     * 
     * @return 
     */
    public Camera getCamera() {
        return densityThread.getCamera();
    }
    
    ////////////////////////////////////////////////
    // Monitors management                        //
    ////////////////////////////////////////////////
    
    /**
     * Add a monitor for the density.
     * 
     * @param m 
     */
    public void addDensityMonitor(DensityMonitor m) {
        densityThread.addDensityMonitor(m);
    }
    
    /**
     * Remove the specified monitor.
     * 
     * @param m 
     */
    public void removeDensityMonitor(DensityMonitor m) {
        densityThread.removeDensityMonitor(m);
    }
    
    /**
     * Remove all density monitors.
     */
    public void clearDensityMonitors() {
        densityThread.clearMonitors();
    }
    
    /**
     * Start the density monitor.
     */
    public void startDensityMonitor() {
        log("Density monitor started.");
        
        if (dt1 == null || !dt1.isAlive()) {
            dt1 = new Thread(densityThread);
            dt1.start();
            
            log("Using existing thread.");
        }
        
        densityThread.setRunning(true);
    }
    
    /**
     * Pause the density monitor.
     */
    public void pauseDensityMonitor() {
        if (densityThread == null || dt1 == null)
            return;
        
        log("Density monitor stopped.");
        
        densityThread.setRunning(false);
    }
    
    /**
     * Add another laser power monitor.
     * 
     * @param l 
     */
    void addLaserPowerMonitor(LaserPowerMonitor l) {
        laseThread.addLaserPowerMonitor(l);
    }
    
    /**
     * Remove the specified laser power monitor.
     * 
     * @param l 
     */
    void removeLaserPowerMonitor(LaserPowerMonitor l) {
        laseThread.removeLaserPowerMonitor(l);
    }
    
    /**
     * Remove all laser power monitors.
     */
    void clearLaserPowerMonitors() {
        laseThread.clearMonitors();
    }
    
    /**
     * Start the laser controller.
     */
    public void startLaserControl() {
        log("Starting laser control.");
        
        if (lt1 == null || !lt1.isAlive()) {
            lt1 = new Thread(laseThread);
            lt1.start();
            
            log("Using existing thread.");
        }
        
        laseThread.setRunning(true);

        addDensityMonitor(laseThread);
    }
    
    /**
     * Pasues the laser controller.
     */
    public void pauseLaserControl() {
        log("Stopping laser control.");
        
        if (laseThread == null || lt1 == null) {
            log("No laser thread, no action taken.");
            
            return;
        }
        
        laseThread.setRunning(false);
    }
    
    /**
     * Get the automatic threshold from the density monitor.
     * 
     * @return 
     */
    public int getAutoThreshold() {
        return dmm.getAutoThreshold();
    }
    
    /**
     * Show the density map monitor.
     * 
     * @param enabled 
     */
    void showDensityMapMonitor(boolean enabled) {
        log("Setting density map visible to "+enabled);
        
        if (dmm==null)
            dmm = new DensityMap();
        
        dmm.setVisible(enabled);
        dmm.setRunning(enabled);
        
        if (densityThread!=null)
            densityThread.addDensityMapMonitor(dmm);
        
        if (dmmt==null || !dmmt.isAlive()) {
            log("Creating new density map.");
            
            dmmt = new Thread(dmm);
            dmmt.start();
        }
    }
    
    /**
     * Show the density profile monitor.
     * 
     * @param enabled 
     */
    void showDensityProfileMonitor(boolean enabled){
      log("Setting density profiler visible to "+enabled);
      if (densityProfiler ==null){
         densityProfiler = new DensityProfiler();
      }
      addDensityMonitor(densityProfiler);

      if (enabled){
         densityProfiler.startProfiler();
      }else{
         densityProfiler.stopProfiler();
      }
       
    }
    
    void startLogFile(){
       System.out.println("Start log");
       logFileIsStarted = true;
       if (densityLogger==null){
          densityLogger = new DensityFileLogger();
       }
       densityLogger.setLaser_minStep(config.minStep);
       densityLogger.setLaser_minValue(config.minValue);
       densityLogger.setLaser_minValue(config.maxValue);
       densityLogger.setMinGoodDensity(dmm.getMinGoodDensity());
       densityLogger.setMaxGoodDensity(dmm.getMaxGoodDensity());
       densityLogger.startLog();
       addDensityMonitor(densityLogger);
       addLaserPowerMonitor(densityLogger);

    }
    
    void stopLogFile(){
       System.out.println("Stop log");
       logFileIsStarted = false;
       densityLogger.stopLog();

    }
}
