///////////////////////////////////////////////////////////////////////////////
// FILE:          DeviceListBuilder.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, November 2006
//
// COPYRIGHT:     University of California, San Francisco, 2006
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// CVS:           $Id$

import org.micromanager.conf.MicroscopeModel;
import org.micromanager.utils.ReportingUtils;

/**
 * Utility to generate a list of available devices for use with
 * Micro-Manager Configuration Wizard.
 */
public class DeviceListBuilder {

   public static void main(String[] args) {
      StringBuffer resultingFile = new StringBuffer();
      
      if (MicroscopeModel.generateDeviceListFile(resultingFile, null))
         ReportingUtils.logMessage("Device list " + resultingFile + " generated.");
      else
         ReportingUtils.logMessage("Device list " + resultingFile + " not generated or invalid.");
   }

}
