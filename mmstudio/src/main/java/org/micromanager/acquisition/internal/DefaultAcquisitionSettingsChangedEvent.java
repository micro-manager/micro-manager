///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Events API
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, 2023
//
// COPYRIGHT:    Altos Labs, 2023
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

package org.micromanager.acquisition.internal;

import org.micromanager.acquisition.AcquisitionSettingsChangedEvent;
import org.micromanager.acquisition.SequenceSettings;

/**
 * This implementation of this event is posted on the Studio event bus,
 * so subscribe to this event using {@link org.micromanager.events.EventManager}.
 */
public class DefaultAcquisitionSettingsChangedEvent implements AcquisitionSettingsChangedEvent {
   private final SequenceSettings newSettings_;

   public DefaultAcquisitionSettingsChangedEvent(SequenceSettings newSettings) {
      newSettings_ = newSettings;
   }

   @Override
   public SequenceSettings getNewSettings() {
      return newSettings_;
   }
}