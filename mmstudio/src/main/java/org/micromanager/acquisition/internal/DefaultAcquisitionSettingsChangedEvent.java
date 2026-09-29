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
   private final boolean isPrimaryEngine_;

   /**
    * Constructs the event for the primary acquisition engine.
    *
    * @param newSettings The settings that were just applied.
    */
   public DefaultAcquisitionSettingsChangedEvent(SequenceSettings newSettings) {
      this(newSettings, true);
   }

   /**
    * Constructs the event, specifying which kind of engine it came from.
    *
    * @param newSettings     The settings that were just applied.
    * @param isPrimaryEngine False when posted by a secondary engine, such as a
    *                        Test Acquisition, whose settings are derived from
    *                        the MDA window rather than being the MDA window's
    *                        own settings.
    */
   public DefaultAcquisitionSettingsChangedEvent(SequenceSettings newSettings,
                                                 boolean isPrimaryEngine) {
      newSettings_ = newSettings;
      isPrimaryEngine_ = isPrimaryEngine;
   }

   @Override
   public SequenceSettings getNewSettings() {
      return newSettings_;
   }

   @Override
   public boolean isPrimaryEngine() {
      return isPrimaryEngine_;
   }
}