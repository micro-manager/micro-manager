///////////////////////////////////////////////////////////////////////////////
//FILE:          PreLoacDataThread.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, November 2010
//
// COPYRIGHT:    University of California, San Francisco, 2010
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

/*
 * A thread that will attempt to (pre-)load images from disk until less than
 * a set amount of memory is available.  Should only be called in conjuntcion
 * with a disk-based storage system and only when loading an existing data set
 * from disk.  
 */

package org.micromanager.acquisition;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author nico
 */
public class PreLoadDataThread implements Runnable {

   private VirtualAcquisitionDisplay virtAcq_;
   private static final long MEMORYLIMIT = 5000000;

   public PreLoadDataThread(VirtualAcquisitionDisplay virtAcq) {
      virtAcq_ = virtAcq;
   }

   public void run() {

      //int nrChannels = virtAcq_.getNumChannels();
      JSONObject summaryMetadata = virtAcq_.imageCache_.getSummaryMetadata();
      try {
         int numSlices = Math.max(summaryMetadata.getInt("Slices"), 1);
         int numFrames = Math.max(summaryMetadata.getInt("Frames"), 1);
         int numChannels = Math.max(summaryMetadata.getInt("Channels"), 1);
         int numPositions = Math.max(summaryMetadata.getInt("Positions"), 1);
         //int numComponents = MDUtils.getNumberOfComponents(summaryMetadata);
         for (int channel = 0; channel < numChannels; channel++) {
            for (int slice = 0; slice < numSlices; slice++ ) {
               for (int frame = 0; frame < numFrames; frame ++) {
                  for (int position = 0; position < numPositions; position++) {
                     virtAcq_.imageCache_.getImage(channel, slice, frame, position);
                     if (JavaUtils.getAvailableUnusedMemory() < MEMORYLIMIT)
                        return;
                  }
               }
            }
         }
      } catch (JSONException ex ) {
         ReportingUtils.logError(ex);
      } catch (NullPointerException ex) {
         ReportingUtils.logError(ex);
      }
   }
}

