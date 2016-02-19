package ch.epfl.leb.autolase;

/**
 * An interface for the density listeners.
 * 
 * @author Thomas Pengo
 */
interface DensityMapMonitor {
    public void densityMapChanged(int width, int height, float[] density);
}
