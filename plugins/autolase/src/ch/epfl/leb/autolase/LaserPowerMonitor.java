package ch.epfl.leb.autolase;

/**
 * A laser power monitor indicates a class interested in changes to the laser
 * power.
 * 
 * @author Thomas Pengo
 */
public interface LaserPowerMonitor {
    
    public void laserPowerChanged(double newLaserPower);
    
}
