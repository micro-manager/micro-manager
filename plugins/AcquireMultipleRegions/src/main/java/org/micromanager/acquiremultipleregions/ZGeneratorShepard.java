

package org.micromanager.acquiremultipleregions;


import java.util.HashMap;
import java.util.Map;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;

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
     * @param positionList
     * @param type
     */
    public ZGeneratorShepard (PositionList positionList, ZGeneratorType type) {  
        //use default exponent of 2
        createInterpolator (positionList, type, 2.0);
    }
    
    public ZGeneratorShepard (PositionList positionList, 
            ZGeneratorType type, double exponent) {
        createInterpolator (positionList, type, exponent);
    }
    
    private void createInterpolator (PositionList positionList, 
            ZGeneratorType type, double exp){
        int nPositions;
        double x[], y[], z[]; //positions to be passed to interpolator
        MultiStagePosition msp;
        StagePosition sp;

        type_ = type; //remember type of ZGenerator
        //initialize arrays
        nPositions = positionList.getNumberOfPositions();
        x = new double[nPositions];
        y = new double[nPositions];

        
        interpolators_ = new HashMap<String, ShepardInterpolator>(5);
        //Loop over all positions and extract X and Y values
        for (int p=0; p<nPositions; p++){
             msp = positionList.getPosition(p);
             x[p] = msp.getX();
             y[p] = msp.getY();
        }

        //now repeat for each single axis stage and create an interpolator for each one             
       msp =  positionList.getPosition(0);        
       for (int a=0; a<msp.size(); a++){
           z = new double[nPositions];
           sp = msp.get(a); //get an axis
           if (sp.is1DStagePosition()){
              for (int p=0; p<nPositions; p++){
                  z[p] = positionList.getPosition(p).get(a).x;                
              }              
              interpolators_.put(sp.getStageDeviceLabel(), 
                      new ShepardInterpolator(x, y, z, exp)); //store the interpolator for this axis
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
