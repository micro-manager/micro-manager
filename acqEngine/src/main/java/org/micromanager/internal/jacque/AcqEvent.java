package org.micromanager.internal.jacque;

import java.util.List;
import java.util.Map;

final class AcqEvent {
   public int frameIndex;
   public int sliceIndex;
   public int channelIndex;
   public int positionIndex;
   public int cameraChannelIndex;
   public Integer nextFrameIndex;
   public double exposure;
   public Double slice;
   public Double waitTimeMs;
   public boolean autofocus;
   public boolean newPosition;
   public boolean closeShutter;
   public boolean relativeZ;
   public AcqChannel channel;
   public int position;
   public String camera;
   public String task;
   public List<AcqEvent> burstData;
   public int burstLength;
   public TriggerSequence triggerSequence;
   public List<Runnable> runnables;
   public Map<String, String> metadata;

   public AcqEvent() {
   }

   public AcqEvent copy() {
      AcqEvent e = new AcqEvent();
      e.frameIndex = this.frameIndex;
      e.sliceIndex = this.sliceIndex;
      e.channelIndex = this.channelIndex;
      e.positionIndex = this.positionIndex;
      e.cameraChannelIndex = this.cameraChannelIndex;
      e.nextFrameIndex = this.nextFrameIndex;
      e.exposure = this.exposure;
      e.slice = this.slice;
      e.waitTimeMs = this.waitTimeMs;
      e.autofocus = this.autofocus;
      e.newPosition = this.newPosition;
      e.closeShutter = this.closeShutter;
      e.relativeZ = this.relativeZ;
      e.channel = this.channel;
      e.position = this.position;
      e.camera = this.camera;
      e.task = this.task;
      e.burstData = this.burstData;
      e.burstLength = this.burstLength;
      e.triggerSequence = this.triggerSequence;
      e.runnables = this.runnables;
      e.metadata = this.metadata;
      return e;
   }
}
