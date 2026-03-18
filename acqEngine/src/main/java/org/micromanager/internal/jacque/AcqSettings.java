package org.micromanager.internal.jacque;

import java.util.ArrayList;
import java.util.List;
import mmcorej.CMMCore;
import org.micromanager.PositionList;
import org.micromanager.acquisition.ChannelSpec;
import org.micromanager.acquisition.SequenceSettings;

final class AcqSettings {
   public List<Integer> frames;
   public List<AcqChannel> channels;
   public List<Integer> positions;
   public List<Double> slices;
   public boolean slicesFirst;
   public boolean timeFirst;
   public boolean keepShutterOpenSlices;
   public boolean keepShutterOpenChannels;
   public boolean useAutofocus;
   public int autofocusSkip;
   public boolean relativeSlices;
   public double intervalMs;
   public double defaultExposure;
   public int cameraTimeout;
   public List<Double> customIntervalsMs;
   public boolean usePositionList;
   public String channelGroup;
   public int numFrames;
   public boolean save;
   public String root;
   public String prefix;
   public String comment;

   public static AcqSettings fromSequenceSettings(SequenceSettings ss,
         PositionList pl, CMMCore mmc) throws Exception {
      AcqSettings s = new AcqSettings();
      s.numFrames = ss.numFrames();
      s.frames = new ArrayList<>(ss.numFrames());
      for (int i = 0; i < ss.numFrames(); i++) {
         s.frames.add(i);
      }
      s.channels = new ArrayList<>();
      for (ChannelSpec cs : ss.channels()) {
         AcqChannel ch = AcqChannel.fromChannelSpec(
               ss.channelGroup(), cs, mmc);
         if (ch.useChannel) {
            s.channels.add(ch);
         }
      }
      if (pl != null && ss.usePositionList()) {
         int n = pl.getNumberOfPositions();
         s.positions = new ArrayList<>(n);
         for (int i = 0; i < n; i++) {
            s.positions.add(i);
         }
      } else {
         s.positions = null;
      }
      s.slices = new ArrayList<>(ss.slices());
      s.slicesFirst = ss.slicesFirst;
      s.timeFirst = ss.timeFirst;
      s.keepShutterOpenSlices = ss.keepShutterOpenSlices();
      s.keepShutterOpenChannels = ss.keepShutterOpenChannels();
      s.useAutofocus = ss.useAutofocus();
      s.autofocusSkip = ss.skipAutofocusCount();
      s.relativeSlices = ss.relativeZSlice();
      s.intervalMs = ss.intervalMs();
      s.defaultExposure = mmc.getExposure();
      s.cameraTimeout = ss.cameraTimeout();
      s.customIntervalsMs = ss.customIntervalsMs() != null
            ? new ArrayList<>(ss.customIntervalsMs())
            : new ArrayList<>();
      s.usePositionList = ss.usePositionList();
      s.channelGroup = ss.channelGroup();
      s.save = ss.save();
      s.root = ss.root();
      s.prefix = ss.prefix();
      s.comment = ss.comment();
      return s;
   }
}
