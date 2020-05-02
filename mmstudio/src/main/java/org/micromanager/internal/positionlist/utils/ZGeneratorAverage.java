///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Kurt Thorn
//              Nick Anthony, 2018  Moved from AcquireMultipleRegions plugin.
//
// COPYRIGHT:    University of California, San Francisco, 2014
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.internal.positionlist.utils;


import java.util.HashMap;
import java.util.Map;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;

class ZGeneratorAverage implements ZGenerator {
     Map <String, Double> averageZPositions_;
     /**
     * Constructor
     * @param positionList initial position list
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
              c = sp.get1DPosition();
              //Calculate sum of positions for current axis
              for (int p=1; p<positionList.getNumberOfPositions(); p++){
                  c = c + positionList.getPosition(p).get(a).get1DPosition();
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
        return ZGenerator.Type.AVERAGE.toString();   
    }
}
