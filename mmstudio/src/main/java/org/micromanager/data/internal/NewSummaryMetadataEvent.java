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

import org.micromanager.data.SummaryMetadata;

/**
 * This class signifies that new summary metadata has been set for a 
 * Datastore.
 * TODO: should be renamed to DefaultNewSummaryMetadataEvent.
 */
public class NewSummaryMetadataEvent implements org.micromanager.data.NewSummaryMetadataEvent {
   private SummaryMetadata metadata_;
   public NewSummaryMetadataEvent(SummaryMetadata metadata) {
      metadata_ = metadata;
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      return metadata_;
   }
}
