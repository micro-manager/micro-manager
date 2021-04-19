///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API implementation
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

package org.micromanager.data.internal;

import org.micromanager.data.DataProvider;
import org.micromanager.data.DataProviderHasNewSummaryMetadataEvent;
import org.micromanager.data.SummaryMetadata;

/**
 * This class signifies that new summary metadata has been set for a 
 * DataProvider.
 *
 * This Event posts on the DataProvider bus.
 * Subscribe using {@link DataProvider#registerForEvents(Object)}.
 */
public final class DefaultNewSummaryMetadataEvent implements 
        DataProviderHasNewSummaryMetadataEvent {
   
   private final SummaryMetadata metadata_;
   
   public DefaultNewSummaryMetadataEvent(SummaryMetadata metadata) {
      metadata_ = metadata;
   }

   /**
    * @return New summary metadata of the DataProvider.
    */
   @Override
   public SummaryMetadata getSummaryMetadata() {
      return metadata_;
   }
}
