///////////////////////////////////////////////////////////////////////////////
//FILE:          DevicesPanel.java
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

package org.micromanager.asidispim;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author nico
 */
public class Knobs {
   public static enum Controller {
      JOYSTICK, RIGHT_KNOB, LEFT_KNOB
   };
   public static final Map<Controller, String> CONTROLSMAP =
           new EnumMap<Controller, String>(Controller.class);

   static {
      CONTROLSMAP.put(Controller.JOYSTICK, "Joystick");
      CONTROLSMAP.put(Controller.RIGHT_KNOB, "Right Knob");
      CONTROLSMAP.put(Controller.LEFT_KNOB, "Left Know");
   }
   public static final Map<String, Controller> REVCONTROLSMAP =
           new HashMap<String, Controller>();

   static {
      Iterator<Controller> it = CONTROLSMAP.keySet().iterator();
      while (it.hasNext()) {
         Controller term = it.next();
         REVCONTROLSMAP.put(CONTROLSMAP.get(term), term);
      }
   }
   
   private Map<Controller, String> controlMap_;
   
   
   public Knobs() {
      controlMap_ = new EnumMap<Controller, String>(Controller.class);
   }
   
   public void setController(Controller control, String device) {
      // TODO: write code talking to controller  
      controlMap_.put(control, device);
   }
   
   
   public String getControlledDevice(Controller control) {   
      return controlMap_.get(control);
   }
   
   
   
}
