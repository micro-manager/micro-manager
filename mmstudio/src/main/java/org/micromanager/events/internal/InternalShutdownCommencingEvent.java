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

package org.micromanager.events.internal;

/**
 * This event is posted when the user requests the program to shut down. It's
 * a complete copy of the ShutdownCommencingEvent, but not available in the
 * API; it's only intended for internal use.
 */
public final class InternalShutdownCommencingEvent {
   private boolean isCanceled_ = false;

   /**
    * Cancel shutdown.
    */
   public void cancelShutdown() {
      isCanceled_ = true;
   }

   /**
    * Return whether or not shutdown has been canceled.
    */
   public boolean isCanceled() {
      return isCanceled_;
   }
}
