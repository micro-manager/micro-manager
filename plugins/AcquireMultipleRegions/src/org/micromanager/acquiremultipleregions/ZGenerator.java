/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquiremultipleregions;

/**
 * Generates a Z position from XY coordinates
 * 
 * @author kthorn
 */
interface ZGenerator {
    
    public enum ZGeneratorType {
        SHEPINTERPOLATE ("Weighted Interpolation"),
        AVERAGE ("Average");
        
        public final String description;
        
        ZGeneratorType(String description) {
            this.description = description;
        }
        
        @Override
        public String toString() {
            return description;
        }
    }
    
    public abstract double getZ (double X, double Y, String axis);    
}