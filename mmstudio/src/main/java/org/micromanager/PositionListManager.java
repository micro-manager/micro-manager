///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, December 3, 2006
//               Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2006-2015
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

package org.micromanager;



/**
 * This entity provides access to methods for interacting with the Stage
 * PositionList. You can access this class via the Studio.positions() and
 * Studio.getPositionListManager() methods.
 */
public interface PositionListManager {
   /**
    * Makes this the 'current' PositionList, i.e., the one used by the
    * Acquisition Protocol, and shown in the PositionListDlg.
    *
    * @param pl PositionList to be made the current one
    */
   void setPositionList(PositionList pl);

   /**
    * Returns a copy of the current PositionList, the one used by the
    * Acquisition Protocol.
    *
    * @return copy of the current PositionList
    */
   PositionList getPositionList();

   /**
    * Adds the current position to the list (same as pressing the "Mark" button
    * in the XYPositionList with no position selected).
    *
    * @deprecated since this function completely depends on the PositionListDlg.
    */
   @Deprecated
   void markCurrentPosition();
}
