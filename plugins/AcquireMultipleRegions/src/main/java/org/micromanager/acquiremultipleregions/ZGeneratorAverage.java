

package org.micromanager.acquiremultipleregions;

import java.util.HashMap;
import java.util.Map;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;

/**
 *
 * @author kthorn
 */
class ZGeneratorAverage implements ZGenerator {
     Map <String, Double> averageZPositions_;
     ZGeneratorType type_;
     /**
     * Constructor
     * 
     * @param PL initial position list
     * @param type 
     */  
    public ZGeneratorAverage (PositionList PL, ZGeneratorType type) {
       type_ = type; //remember type of ZGenerator

       MultiStagePosition MSP0;
       StagePosition SP;
       double C;

       averageZPositions_ = new HashMap<String, Double>(5);
       //Loop over single axis stages and calculate their mean value     
       MSP0 =  PL.getPosition(0);        
       for (int a=0; a<MSP0.size(); a++){
           SP = MSP0.get(a);
           if (SP.numAxes == 1){
              C = SP.x;
              //Calculate sum of positions for current axis
              for (int p=1; p<PL.getNumberOfPositions(); p++){
                  C = C + PL.getPosition(p).get(a).x;                
              }

              Double Z = C / PL.getNumberOfPositions(); //average
              averageZPositions_.put(SP.stageName, Z);
           }
       }        
    } 

    @Override
    public double getZ(double X, double Y, String axis) {
        return averageZPositions_.get(axis);
    }

}
