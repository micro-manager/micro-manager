package ch.epfl.leb.autolase;

import mmcorej.CMMCore;

/**
 * Implements a simple generic Camera (@see Camera) with MicroManager.
 * 
 * @author Thomas Pengo
 */
public class MMCamera implements Camera {

    CMMCore core;
    
    /**
     * Creates a new camera which wraps around the default MicroManager class.
     * 
     * @param core 
     */
    public MMCamera(CMMCore core) {
        this.core = core;
    }
    
    /**
     * Returns the latest image from the camera in form of an array of shorts.
     * 
     * @return
     * @throws Exception 
     */
    public short[] getNewImage() throws Exception {
        return (short[]) core.getLastImage();
    }

    @Override
    public int getWidth() {
        return (int)core.getImageWidth();
    }

    @Override
    public int getHeight() {
        return (int)core.getImageHeight();
    }

    @Override
    public int getBytesPerPixel() {
        return (int) core.getBytesPerPixel();
    }

    @Override
    public boolean isAcquiring() {
        return core.isSequenceRunning();
    }
}
