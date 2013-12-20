///////////////////////////////////////////////////////////////////////////////
//FILE:          Operation.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim.Data;

import org.micromanager.asidispim.ASIdiSPIMFrame;
import org.micromanager.asidispim.Data.Properties.PropTypes;


/**
 * Class containing data pertaining to acquisition settings
 * @author Jon
 */
public class Operation {

   // list of strings used as keys in the Property class
   // initialized with corresponding property name in the constructor
   public static final String MM_SPIM_STATE = "MMSPIMState";
   public static final String PZ_SPIM_STATE_A = "PZSPIMStateA";
   public static final String PZ_SPIM_STATE_B = "PZSPIMStateB";
   
   public Operation() {
      
      // initialize any property values
      ASIdiSPIMFrame.props_.addPropertyData(MM_SPIM_STATE, "SPIMState", Devices.GALVOA, PropTypes.STRING);
      ASIdiSPIMFrame.props_.addPropertyData(PZ_SPIM_STATE_A, "SPIMState", Devices.PIEZOA, PropTypes.STRING);
      ASIdiSPIMFrame.props_.addPropertyData(PZ_SPIM_STATE_A, "SPIMState", Devices.PIEZOB, PropTypes.STRING);

   }

}
