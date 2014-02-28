package ch.epfl.leb.autolase;

import ij.ImagePlus;
import ij.gui.ImageRoi;
import ij.gui.Overlay;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.LUT;
import java.util.Arrays;

/**
 * Gives a visual feedback for the current density levels. Green indicates 
 * density above 0 and below minimum. White indicates above minimum and below 
 * maximum. Red indicates above maximum.
 * 
 * @author Thomas Pengo
 */
public class DensityMap  implements DensityMapMonitor, Runnable {
    public static final int DEFAULT_UPDATE_TIME = 50;
    
    ImagePlus theImage;
    
    float[] density;
    int width, height;
    private float minGoodDensity = 1;
    private float maxGoodDensity = 2;
    
    Overlay densityOverlay;

    long updateTime = DEFAULT_UPDATE_TIME;
    
    boolean stopping = false;
    boolean running = true;
    boolean changed = false;

    /**
     * Sets the minimum good density.
     * 
     * @param minGoodDensity 
     */
    public void setMinGoodDensity(float minGoodDensity) {
        this.minGoodDensity = minGoodDensity;
    }

    /**
     * Sets the maximum good density.
     * 
     * @param maxGoodDensity 
     */
    public void setMaxGoodDensity(float maxGoodDensity) {
        this.maxGoodDensity = maxGoodDensity;
    }
    
    /**
     * Reset the density map.
     * 
     */
    public void clearMap() {
        density = null;
        if (theImage!=null)
            theImage.setHideOverlay(true);
        
        changed = false;
    }
    
    /** 
     * Returns true if the density mapping thread is active.
     * 
     * @return 
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Starts or stops the density mapping thread.
     * 
     * @param running 
     */
    public void setRunning(boolean running) {
        this.running = running;
    }
    
    /**
     * Stops the density mapping thread.
     */
    public void stop() {
        stopping = true;
    }
    
    /**
     * Calculates an automatic threshold using the default ImageJ method.
     * 
     * @return 
     */
    public int getAutoThreshold() {
        if (theImage!=null && theImage.getProcessor()!=null)
            return theImage.getProcessor().getAutoThreshold(theImage.getStatistics().histogram);
        else
            return 0;
    }
    
    /**
     * This is called when the density map is changed. It updates the image overlay
     * with the appropriate color code.
     * 
     * @param width
     * @param height
     * @param density 
     */
    @Override
    public void densityMapChanged(int width, int height, float[] density) {
        this.density = Arrays.copyOf(density, density.length);
        this.width = width;
        this.height = height;
        
        // Create color overlay
        byte[] overlay = new byte[density.length];
        for (int i=0; i<density.length; i++) {
            if (density[i]>0 && density[i]<minGoodDensity)
                overlay[i]=2;
            else if (density[i]<maxGoodDensity)
                overlay[i]=1;
            else
                overlay[i]=3;
        }
        
        densityOverlay = new Overlay(
                new ImageRoi(0,0,
                new ByteProcessor(width, height, overlay, buildLUT())));
        
        changed = true;
    }
    
    boolean visible = false;

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    LUT buildLUT() {
        byte[] r = new byte[] {0,(byte) 0xFF,          0, (byte) 0xFF};
        byte[] g = new byte[] {0,(byte) 0xFF,(byte) 0xFF,           0};
        byte[] b = new byte[] {0,(byte) 0xFF,          0,           0};
        
        return new LUT(wrap(r),wrap(g),wrap(b));
    }
    
    byte[] wrap(byte[] arr) {
        byte[] a = new byte[256];
        for (int i=0; i<a.length; i++) {
            if (i<4) a[i] = arr[i];
            else a[i] = (byte)0xFF;
        } 
        return a;
    }
    
    @Override
    public void run() {
        theImage = new ImagePlus();
        
        long lastTime = System.currentTimeMillis();
        while(!stopping) {
            
            if (running && changed) {
                // Draw the density
                FloatProcessor fp = new FloatProcessor(width,height,density,null);

                theImage.setProcessor("Density Map",fp);

                theImage.setOverlay(densityOverlay);
                theImage.setHideOverlay(false);
                
                if (visible)
                    theImage.show();
                else 
                    theImage.hide();
                
            }
            
            long waitTime = lastTime+updateTime-System.currentTimeMillis();
            if (waitTime>0)
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException e) {
                    stopping = true;
                }
            
            lastTime = System.currentTimeMillis();
        }
    }

   /**
    * @return the minGoodDensity
    */
   public float getMinGoodDensity() {
      return minGoodDensity;
   }

   /**
    * @return the maxGoodDensity
    */
   public float getMaxGoodDensity() {
      return maxGoodDensity;
   }
}
