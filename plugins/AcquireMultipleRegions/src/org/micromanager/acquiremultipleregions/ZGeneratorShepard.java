/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquiremultipleregions;


import java.util.HashMap;
import java.util.Map;
import org.micromanager.api.MultiStagePosition;
import org.micromanager.api.PositionList;
import org.micromanager.api.StagePosition;

/**
 *
 * Allows construction of Z positions by interpolation using Shepard Interpolation
 * 
 * @author kthorn
 */
class ZGeneratorShepard implements ZGenerator {
    ZGeneratorType type_;
    Map <String, ShepardInterpolator> interpolators_;

    /**
     *
     * @param PL
     * @param type
     */
    public ZGeneratorShepard (PositionList PL, ZGeneratorType type) {  
        //use default exponent of 2
        createInterpolator (PL, type, 2.0);
    }
    
    public ZGeneratorShepard (PositionList PL, ZGeneratorType type, double exponent) {
        createInterpolator (PL, type, exponent);
    }
    
    private void createInterpolator (PositionList PL, ZGeneratorType type, double exp){
        int nPositions;
        double x[], y[], z[]; //positions to be passed to interpolator
        MultiStagePosition MSP;
        StagePosition SP;

        type_ = type; //remember type of ZGenerator
        //initialize arrays
        nPositions = PL.getNumberOfPositions();
        x = new double[nPositions];
        y = new double[nPositions];

        
        interpolators_ = new HashMap<String, ShepardInterpolator>(5);
        //Loop over all positions and extract X and Y values
        for (int p=0; p<nPositions; p++){
             MSP = PL.getPosition(p);
             x[p] = MSP.getX();
             y[p] = MSP.getY();
        }

        //now repeat for each single axis stage and create an interpolator for each one             
       MSP =  PL.getPosition(0);        
       for (int a=0; a<MSP.size(); a++){
           z = new double[nPositions];
           SP = MSP.get(a); //get an axis
           if (SP.numAxes == 1){
              for (int p=0; p<nPositions; p++){
                  z[p] = PL.getPosition(p).get(a).x;                
              }              
              interpolators_.put(SP.stageName, new ShepardInterpolator(x, y, z, exp)); //store the interpolator for this axis
           }
       }        
    }

    /**
     *
     * @param X
     * @param Y
     * @param axis
     * @return 
     */
    @Override
    public double getZ(double X, double Y, String axis) {
        ShepardInterpolator interpolator;
        interpolator = interpolators_.get(axis);
        return interpolator.interpolate(X, Y);
    }
}