package org.micromanager.acqj.api.mda;

import java.util.List;

/**
 *
 * @author henrypinkard
 */
public class MultiDAcqSettings {

   public final List<XYStagePosition> xyPositions_;
   public final List<ChannelSetting> channels_;
   public final int numFrames_;
   public final double frameInterval_;
   public final double zStep_;
   public final double zOrigin_;
   public final int minSliceIndex_, maxSliceIndex_;
   public final String name_, dir_;
   
   public MultiDAcqSettings(String dir, String name, List<ChannelSetting> channels,
           List<XYStagePosition> xyPos, int numFrames, double frameInterval_ms,
           double zStep, double zOrigin, int minSlice, int maxSlice) {
      name_ = name;
      dir_ = dir;
      channels_ = channels;
      xyPositions_ = xyPos;
      numFrames_ = numFrames;
      frameInterval_ = frameInterval_ms;
      zStep_ = zStep;
      zOrigin_ = zOrigin;
      minSliceIndex_ = minSlice;
      maxSliceIndex_ = maxSlice;   
   }

}
