/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.imagedisplay.events;

import java.util.HashMap;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.acqj.api.AcqEngMetadata;

/**
 *
 * @author henrypinkard
 */
 public final class MagellanNewImageEvent implements MagellanScrollbarPosition {
  
   final public HashMap<String, Integer> axisToPosition_;
   public final String channelName_;

   public MagellanNewImageEvent(JSONObject tags) {
      axisToPosition_ = new HashMap<String, Integer>();
      axisToPosition_.put("t", AcqEngMetadata.getFrameIndex(tags));
      axisToPosition_.put("c", AcqEngMetadata.getChannelIndex(tags));
      axisToPosition_.put("z", AcqEngMetadata.getSliceIndex(tags));
      //TODO: region
      channelName_ = AcqEngMetadata.getChannelName(tags);
   }

   public int getPositionForAxis(String axis) {
      if (!axisToPosition_.containsKey(axis)) {
         return 0;
      }
      return axisToPosition_.get(axis);
   }


}
