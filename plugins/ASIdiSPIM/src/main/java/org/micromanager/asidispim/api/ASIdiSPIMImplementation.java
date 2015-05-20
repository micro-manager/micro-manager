///////////////////////////////////////////////////////////////////////////////
//FILE:          ASIdiSPIMImplementation.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2014
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

package org.micromanager.asidispim.api;

import org.micromanager.asidispim.ASIdiSPIM;
import org.micromanager.asidispim.ASIdiSPIMFrame;
import org.micromanager.asidispim.AcquisitionPanel;

/**
 * Implementation of the ASidiSPIMInterface
 * To avoid depending on the internals of this class and restrict yourself
 * to the ASIdiSPIMInterface, always cast the instance of this class to ASIdiSPIMInterface
 * e.g.: 
 * 
 * ASIdiSPIMInterface diSPIM = new ASIdiSPIMImplementation();
 * 
 * @author nico
 * @author Jon
 */
public class ASIdiSPIMImplementation implements ASIdiSPIMInterface {

   @Override
   public void runAcquisition() throws ASIdiSPIMException {
      getAcquisitionPanel().runAcquisition();
   }
   
   @Override
   public void setAcquisitionNamePrefix(String acqName) throws ASIdiSPIMException {
      getAcquisitionPanel().setAcquisitionNamePrefix(acqName);
   }
   
   //** Private methods.  Only for internal use **//
   
   private ASIdiSPIMFrame getFrame() throws ASIdiSPIMException {
      ASIdiSPIMFrame frame = ASIdiSPIM.getFrame();
      if (frame == null) {
         throw new ASIdiSPIMException ("Plugin is not open");
      }
      return frame;
   }
   
   private AcquisitionPanel getAcquisitionPanel() throws ASIdiSPIMException {
      AcquisitionPanel acquisitionPanel = getFrame().getAcquisitionPanel();
      if (acquisitionPanel == null) {
         throw new ASIdiSPIMException ("AcquisitionPanel is not open");
      }
      return acquisitionPanel;
   }

}
