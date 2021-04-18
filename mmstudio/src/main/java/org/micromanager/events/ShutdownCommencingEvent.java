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

import org.micromanager.MMEvent;

/**
 * This event posts when the user requests the program to shut down.
 *
 * It gives subscribers the opportunity to cancel shutdown (ideally only to
 * ensure that data can be saved or other similarly-critical decisions).
 *
 * All subscribers must first check if the shutdown has been canceled by
 * calling {@link #isCanceled()}. If the shutdown has been canceled, the
 * event must be ignored.
 *
 * The default implementation of this event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public interface ShutdownCommencingEvent extends MMEvent {
   /**
    * Cancel shutdown.
    */
   void cancelShutdown();

   /**
    * Return whether or not shutdown has been canceled.
    * @return true when shutdown was canceled
    */
   boolean isCanceled();


   /**
    * @return true when shutdown was canceled
    * @deprecated use {@link #isCanceled()} instead
    */
   @Deprecated
   boolean getIsCancelled();
}
