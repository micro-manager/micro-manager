

package org.micromanager.internal.positionlist.utils;


/**
 * Generates a Z position from XY coordinates
 * 
 * @author kthorn
 */
public interface ZGenerator {
    public enum Type{
        SHEPINTERPOLATE ("Weighted Interpolation"),
        AVERAGE ("Average");
        public final String description_;
        
        Type(String description) {
            description_ = description;
        }
  
        @Override
        public String toString() {
            return description_;
        }
    }
    public abstract double getZ (double X, double Y, String zDevice); 
    public abstract String getDescription();
}