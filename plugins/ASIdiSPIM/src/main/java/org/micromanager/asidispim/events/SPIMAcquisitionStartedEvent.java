//////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2015
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

package org.micromanager.asidispim.events;

import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Datastore;
import org.micromanager.acquisition.AcquisitionSequenceStartedEvent;

/**
 *
 * @author nico
 */
public class SPIMAcquisitionStartedEvent implements AcquisitionSequenceStartedEvent {
   final private Datastore store_;
   final private SequenceSettings sequence_;
   final private Object object_;
   
   public SPIMAcquisitionStartedEvent(Datastore store, SequenceSettings sequence,
           Object object) {
      store_ = store;
      sequence_ = sequence;
      object_ = object;
   }
   
   @Override
   public Datastore getDatastore() {
      return store_;
   }

   @Override
   public Object getSource() {
      return object_;
   }

   @Override
   public SequenceSettings getSettings() {
      return sequence_;
   }
   
}
