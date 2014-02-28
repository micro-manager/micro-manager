package ch.epfl.leb.autolase;

/**
 * This is the list of properties for the laser device configuration.
 * 
 * deviceName : the name of the device (as in MicroManager)
 * propertyName : the property of the device to control 
 * minStep : the minimum step between control values
 * minValue : the minimum allowed value for the control variable
 * maxValue : the maximum allowed value for the control variable
 * 
 * @author Thomas Pengo
 */
public class LaserConfig {
    String      deviceName;
    String      propertyName;
    double      minStep;
    double      minValue;
    double      maxValue;
    double      startValue;
    
    //List<String> deviceNames;

    /**
     * Creates a default configuration with the following values:
     * 
     *     deviceName = "AOTF-DAC3";
     *     propertyName = "Volts";
     *     minStep = .05;
     *     minValue = 0.0;
     *     maxValue = 5.0;
     *     startValue = 0.2;
     */
    LaserConfig() {
        // Some default values
        deviceName = "AOTF-DAC3";
        propertyName = "Volts";
        minStep = .05;
        minValue = 0.0;
        maxValue = 5.0;
        startValue = 0.2;
   }
    //LaserConfig() {
    //    // Some default values
    //    deviceName = "D-DA-Laser1";
    //    propertyName = "Voltage";
    //    minStep = 0.05;
    //    minValue = 0;
    //    maxValue = 5;
    //    startValue = 0.2;
    //}

    ////////////////////////
    // GETTERS AND SETTERS
    ////////////////////////

    /*public List<String> getDeviceNames() {
        return deviceNames;
    }

    public void setDeviceNames(List<String> deviceNames) {
        this.deviceNames = deviceNames;
    }*/

    public String getDeviceName() {
        return deviceName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public double getMinStep() {
        return minStep;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public double getMinValue() {
        return minValue;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public void setMaxValue(double maxValue) {
        this.maxValue = maxValue;
    }

    public void setMinStep(double minStep) {
        this.minStep = minStep;
    }

    public void setMinValue(double minValue) {
        this.minValue = minValue;
    }

    public void setPropertyName(String propertyName) {
        this.propertyName = propertyName;
    }

   /**
    * @return the startValue
    */
   public double getStartValue() {
      return startValue;
   }

   /**
    * @param startValue the startValue to set
    */
   public void setStartValue(double startValue) {
      this.startValue = startValue;
   }
    
    
}
