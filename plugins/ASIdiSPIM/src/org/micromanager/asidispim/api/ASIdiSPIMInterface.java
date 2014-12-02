///////////////////////////////////////////////////////////////////////////////
//FILE:          ASIdiSPIMInterface.java
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

/**
 * This interface defines methods that we intend to support in future versions
 * of the ASIdiSPIM plugin.  Use an implementation of this interface in your
 * own code.
 */

/**
 *
 * @author nico
 * @author Jon
 */
public interface ASIdiSPIMInterface {
   
   /**
    * Runs an acquisition using the current settings, i.e., the settings
    * as visible in the acquisition panel.  The definition of current
    * settings may change in the future.
    * 
    */
   public void runAcquisition() throws ASIdiSPIMException;
   
   /**
    * Changes the name (really the prefix) of the acquisition.
    * @param acqName
    * @throws ASIdiSPIMException
    */
   public void setAcquisitionNamePrefix(String acqName) throws ASIdiSPIMException;
   
}
