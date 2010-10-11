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
   public double exposure;
   public int FrameIndex = 0;
   public double WaitTime;
   public int SliceIndex = 0;
   public double SlicePosition;
   public int PositionIndex = 0;
   public MultiStagePosition Position;
   public int ChannelIndex = 0;
   public ChannelSpec Channel;
   public boolean CloseShutter;
   public boolean AutoFocus;
   public boolean UseChannel;
   public boolean UseSlice;
   public boolean UsePosition;
   public boolean UseFrame;
   public boolean relativeZSlices;
   public double zReference;
   public double zPosition;
   public double NextWaitTime = 0;
   public boolean stop = false;
   public boolean startBurst = false;
   public boolean collectBurst = false;
}
