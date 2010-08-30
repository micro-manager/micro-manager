/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition.engine;

import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.utils.ChannelSpec;

/**
 *
 * @author arthur
 */
public class ImageRequest {
   double exposure;
   int FrameIndex = 0;
   double WaitTime;
   int SliceIndex = 0;
   double SlicePosition;
   int PositionIndex = 0;
   MultiStagePosition Position;
   int ChannelIndex = 0;
   ChannelSpec Channel;
   boolean CloseShutter;
   boolean AutoFocus;
   boolean UseChannel;
   boolean UseSlice;
   boolean UsePosition;
   boolean UseFrame;
   boolean relativeZSlices;
   double zReference;
   double zPosition;
}
