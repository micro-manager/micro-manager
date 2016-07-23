///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Events API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.events;

/**
 * This event is posted when the user requests the program to shut down. It
 * gives subscribers the opportunity to cancel shutdown (ideally only to ensure
 * that data can be saved or other similarly-critical decisions).
 */
public class ShutdownCommencingEvent {
   private boolean isCancelled_ = false;

   /**
    * Cancel shutdown.
    */
   public void cancelShutdown() {
      isCancelled_ = true;
   }

   /**
    * Return whether or not shutdown has been cancelled.
    */
   public boolean getIsCancelled() {
      return isCancelled_;
   }
}
