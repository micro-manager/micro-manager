package ch.epfl.leb.autolase;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class estimates the density of activations by sampling a Camera at 
 * regular intervals (default 20ms). The density at a particular point relates 
 * to the maximum time a certain pixel is "on", or above a certain threshold. 
 * The density is calculated as a moving average (default 1s).
 * 
 * The code only works for 2 bytes per pixel cameras for now. 
 * 
 * @author Thomas Pengo
 */
public class DensityThread implements Runnable {
    public static final int DEFAULT_THRESHOLD = 500;
    public static final int DEFAULT_WAIT_TIME = 20;
    public static final int NUM_ELEMS = 50;
    
    boolean running = true;
    boolean stopping = false;
    
    Camera camera;
    
    double currentDensity = 0;
    
    int threshold = DEFAULT_THRESHOLD;
    long timeInterval = DEFAULT_WAIT_TIME;
    int fifoNumElems = NUM_ELEMS;

    Queue<Double> density_fifo = new ArrayDeque<Double>(fifoNumElems);

    List <DensityMonitor>  monitors = 
            Collections.synchronizedList(new ArrayList <DensityMonitor> ());
    
    public void addDensityMonitor(DensityMonitor m) {
        if (!monitors.contains(m))
            monitors.add(m);
    }
    
    public void removeDensityMonitor(DensityMonitor m) {
        monitors.remove(m);
    }
    
    public void clearMonitors() {
        monitors.clear();
    }

    List <DensityMapMonitor>  mapMonitors = new ArrayList <DensityMapMonitor> ();

    public void addDensityMapMonitor(DensityMapMonitor m) {
        if (!mapMonitors.contains(m))
            mapMonitors.add(m);
    }
    
    public void removeDensityMapMonitor(DensityMapMonitor m) {
        mapMonitors.remove(m);
    }
    
    public void clearMapMonitors() {
        mapMonitors.clear();
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public void setTimeInterval(long timeInterval) {
        this.timeInterval = timeInterval;
    }
    
    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    public Camera getCamera() {
        return camera;
    }
    
    public DensityThread(Camera c) {
        camera = c;
    }
    
    public boolean isRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }
    
    public void stop() {
        stopping = true;
    }

    public double getCurrentDensity() {
        return currentDensity;
    }

    @Override
    public void run() {
        // Current image
        float[] accumulator = null;

        // Start timer
        long lastTime = System.currentTimeMillis();
        
        while(!stopping) {            
            // Only works with 2 bpp
            if (camera.getBytesPerPixel()!=2)
                throw new UnsupportedOperationException("Only works with 16-bit images");            
            
            // Check if we're in sequence acquisition
            if (running && camera.isAcquiring())
                // Get the current image
                try {
                    short[] image = camera.getNewImage();

                    // Reset accumulator if image size has changed
                    if (image!=null && accumulator!=null && (image.length != accumulator.length))
                        accumulator = null;
                    
                    // Threshold the image
                    boolean[] curMask = new boolean[image.length];
                    for (int i=0; i<curMask.length; i++)
                        curMask[i]=image[i]>threshold;

                    // Calculate accumulator
                    if (accumulator == null) {
                        // A_0 = I_0 > t; 
                        accumulator = new float[image.length];
                        for(int i=0; i<accumulator.length; i++)
                            if (curMask[i])
                                accumulator[i] = timeInterval;
                    } else {
                        // A_i = (I_i > t) (1 + A_i-1)
                        for(int i=0; i<accumulator.length; i++)
                            if (!curMask[i]) {
                                accumulator[i] = 0;
                            } else {
                                accumulator[i]+=timeInterval;
                            }
                    }

                    // Density measure: max(A_i)
                    double curd = 0;
                    for (int i=0; i<image.length; i++)
                        if (accumulator[i]>curd)
                            curd = accumulator[i];
                    
                    // Moving average estimate
                    if (density_fifo.size() == fifoNumElems)
                        density_fifo.remove();
                    density_fifo.offer(curd);

                    double mean_density = 0;
                    for (Double d : density_fifo)
                        mean_density+=d;
                    mean_density /= density_fifo.size();

                    currentDensity = mean_density;  
                    
                    for (DensityMonitor m : monitors)
                        m.densityChanged(currentDensity);

                    for (DensityMapMonitor m : mapMonitors)
                        m.densityMapChanged(camera.getWidth(),camera.getHeight(),accumulator);

                } catch (Exception ex) {
                    Logger.getLogger(DensityThread.class.getName()).log(Level.SEVERE, null, ex);
                }
            
            try {
                Thread.sleep(timeInterval);
            } catch (InterruptedException ex) {
                Logger.getLogger(DensityThread.class.getName()).log(Level.SEVERE, null, ex);
                
                stopping = true;
            }
        }
        
        stopping = false;
    }
}
