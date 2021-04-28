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
 * This class signals when the exposure time for one of the channels of the
 * current channel group has been changed.
 *
 * The default implementation of this event is posted on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public interface ChannelExposureEvent extends MMEvent {

    /**
    * Return the new exposure time for the channel.
    */
    double getNewExposureTime();

   /**
    * Return the name of the channel group in which the modified channel
    * is located.
    */
   String getChannelGroup();

   /**
    * Return the channel whose exposure time has changed.
    */
    String getChannel();

   /**
    * Returns true if this channel is the currently-active channel (i.e. the
    * one used for snaps and live mode, the one whose exposure time is
    * displayed in the main window).
    */
    boolean isMainExposureTime();

   /**
    *
    * @deprecated use {@link #isMainExposureTime()} instead
    */
   @Deprecated
    boolean getIsMainExposureTime();
}
