//////////////////////////////////////////////////////////////////////////////
//
//PROJECT:       diSPIM
//-----------------------------------------------------------------------------
//
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

import org.micromanager.data.Datastore;
import org.micromanager.acquisition.AcquisitionEndedEvent;

/**
 *
 * @author nico
 */
public class SPIMAcquisitionEndedEvent implements AcquisitionEndedEvent {
   private final Datastore store_;
   private final Object object_;
   
   public SPIMAcquisitionEndedEvent (Datastore store, Object object) {
      store_ = store;
      object_ = object;
   }
   
   @Override
   public Datastore getStore() {
      return store_;
   }

   @Override
   public Object getSource() {
      return object_;
   }
   
}
