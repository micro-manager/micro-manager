

package org.micromanager.internal.positionlist.utils;


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
     /**
     * Constructor
     * 
     * @param positionList initial position list
     * @param type 
     */  
    public ZGeneratorAverage (PositionList positionList) {
       MultiStagePosition msp0;
       StagePosition sp;
       double c;

       averageZPositions_ = new HashMap<String, Double>(5);
       //Loop over single axis stages and calculate their mean value     
       msp0 =  positionList.getPosition(0);        
       for (int a=0; a<msp0.size(); a++){
           sp = msp0.get(a);
           if (sp.is1DStagePosition()){
              c = sp.x;
              //Calculate sum of positions for current axis
              for (int p=1; p<positionList.getNumberOfPositions(); p++){
                  c = c + positionList.getPosition(p).get(a).x;                
              }

              Double z = c / positionList.getNumberOfPositions(); //average
              averageZPositions_.put(sp.getStageDeviceLabel(), z);
           }
       }        
    } 

    @Override
    public double getZ(double X, double Y, String axis) {
        return averageZPositions_.get(axis);
    }

    @Override
    public String getDescription(){
        return "Average";   
    }
}
