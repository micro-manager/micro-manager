package ch.epfl.leb.autolase;

/**
 * A DensityMonitor listens for changes to the density.
 * 
 * @author Thomas Pengo
 */
public interface DensityMonitor {
    
    /**
     * This method is called when the density is changed.
     * 
     * @param density The new value for the density.
     */
    public void densityChanged(double density);
    
}
