package ch.epfl.leb.autolase;

/**
 * An interface for a simple generic Camera.
 * 
 * @author Thomas Pengo
 */
public interface Camera {
    /**
     * Returns a new image as an array of shorts.
     * 
     * @return
     * @throws Exception 
     */
    public short[] getNewImage() throws Exception;
    
    /**
     * Get the image width.
     * 
     * @return 
     */
    public int getWidth();
    
    /**
     * Get the image height.
     * 
     * @return 
     */
    public int getHeight();
    
    /**
     * Get the bytes per pixel.
     * 
     * @return 
     */
    public int getBytesPerPixel();
    
    /**
     * Returns true if the camera is acquiring.
     * 
     * @return 
     */
    public boolean isAcquiring();
}
