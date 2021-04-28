///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, 2017
//
// COPYRIGHT:    University of California, San Francisco, 2017
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

package org.micromanager.data.internal;

import org.micromanager.data.DataProvider;
import org.micromanager.data.DataProviderHasNewNameEvent;
import org.micromanager.data.Datastore;

 /**
  * This event posts when the DataStore gets a new name, i.e. when
  * {@link Datastore#setName(String)} is called
  *
  * This Event posts on the Datastore bus.
  * Subscribe using {@link DataProvider#registerForEvents(Object)}.
  */
public class DefaultHasNewNameEvent implements DataProviderHasNewNameEvent {
   private final String newName_;
   
   public DefaultHasNewNameEvent (String newName) {
      newName_ = newName;
   }

   /**
    * @return The new name of the DataProvider.
    */
   @Override
   public String getNewName() {
      return newName_;
   }
   
}
