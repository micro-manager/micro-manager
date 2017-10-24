

package org.micromanager.acquiremultipleregions;

/**
 *
 * @author kthorn
 */
public class ShepardInterpolator {
    private double[] x_, y_, z_;
    public double exponent_;
    
    /**
     *
     * @param xin x position list
     * @param yin y position list
     * @param zin z position list
     * @param exp radial weighting exponent
     */
    public ShepardInterpolator(double[] xin, double[] yin, double[] zin, double exp) {
        this.exponent_ = exp;
        if (xin.length != yin.length || xin.length != zin.length ) {
            throw new IllegalArgumentException();
        }
        this.x_ = xin;
        this.y_ = yin;
        this.z_ = zin;
    }    
    
    public double interpolate(double xi, double yi){
        double weight, numerator, denominator;
        double epsilon = 0.001;
        double d;
        int i;
        numerator = 0;
        denominator = 0;
        for (i=0; i<x_.length; i++) {
            //calculate weight
            d = distance(x_[i], xi, y_[i], yi);
            if (d < epsilon) {
                //if we're on top of a point, return it's z coordinate,
                //otherwise d = 0, weight = infinity, and we return a NaN
                return z_[i];
            }
            weight = Math.pow(d, -exponent_);
            numerator += z_[i] * weight;
            denominator += weight;
        }
        return numerator / denominator;
    }
    

    
    private double distance(double x1, double x2, double y1, double y2) {
        return Math.pow((Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2)), 0.5);
    }
    
}
