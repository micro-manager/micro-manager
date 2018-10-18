

package org.micromanager.internal.positionlist.utils;


/**
 * Generates a Z position from XY coordinates
 * 
 * @author kthorn
 */
interface ZGenerator {
    public abstract double getZ (double X, double Y, String zDevice); 
    public abstract String getDescription();
}