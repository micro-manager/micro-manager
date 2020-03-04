package org.micromanager.acqj.api.mda;

import org.micromanager.acqj.internal.acqengj.MinimalAcquisitionSettings;
import org.micromanager.acqj.api.ChannelGroupSettings;
import java.util.List;
import org.micromanager.acqj.api.ChannelGroupSettings;
import org.micromanager.acqj.api.XYStagePosition;
import org.micromanager.acqj.api.XYStagePosition;

/**
 *
 * @author henrypinkard
 */
public class MultiDAcqSettings extends MinimalAcquisitionSettings {

   public final List<XYStagePosition> xyPositions_;
   public final int numFrames_;
   public final double frameInterval_;
   public final double zStep_;
   public final double zOrigin_;
   public final int minSliceIndex_, maxSliceIndex_;

   public MultiDAcqSettings(String dir, String name, String cGroup, ChannelGroupSettings channels,
           List<XYStagePosition> xyPos, int numFrames, double frameInterval_ms,
           double zStep, double zOrigin, int minSlice, int maxSlice) {
      super(dir, name, cGroup, channels);
      xyPositions_ = xyPos;
      numFrames_ = numFrames;
      frameInterval_ = frameInterval_ms;
      zStep_ = zStep;
      zOrigin_ = zOrigin;
      minSliceIndex_ = minSlice;
      maxSliceIndex_ = maxSlice;   
   }

}
