package org.micromanager.internal.jacque;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

public final class SequenceGenerator {

   static final double MAX_Z_TRIGGER_DIST = 5.0;

   private static final List<String> CORE_SHUTTER_KEY =
         Collections.unmodifiableList(Arrays.asList("Core", "Shutter"));

   private static final Comparator<List<String>> KEY_ORDER = (a, b) -> {
      int n = Math.min(a.size(), b.size());
      for (int i = 0; i < n; i++) {
         int c = a.get(i).compareTo(b.get(i));
         if (c != 0) return c;
      }
      return Integer.compare(a.size(), b.size());
   };

   private SequenceGenerator() {
   }

   // --- Template for matching events against attached runnables ---

   public static final class AttachedRunnable {
      public final int frameIndex;
      public final int positionIndex;
      public final int channelIndex;
      public final int sliceIndex;
      public final Runnable runnable;

      public AttachedRunnable(int frameIndex, int positionIndex,
            int channelIndex, int sliceIndex, Runnable runnable) {
         this.frameIndex = frameIndex;
         this.positionIndex = positionIndex;
         this.channelIndex = channelIndex;
         this.sliceIndex = sliceIndex;
         this.runnable = runnable;
      }

      boolean matches(AcqEvent event) {
         return (frameIndex < 0 || event.frameIndex == frameIndex)
               && (positionIndex < 0 || event.positionIndex == positionIndex)
               && (channelIndex < 0 || event.channelIndex == channelIndex)
               && (sliceIndex < 0 || event.sliceIndex == sliceIndex);
      }
   }

   // --- Utility functions ---

   static <T> boolean allEqual(Collection<T> coll) {
      if (coll.isEmpty()) {
         return true;
      }
      T first = coll.iterator().next();
      for (T item : coll) {
         if (!Objects.equals(first, item)) {
            return false;
         }
      }
      return true;
   }

   static <T> boolean allEqual(T val, Collection<T> coll) {
      if (coll.isEmpty()) {
         return true;
      }
      for (T item : coll) {
         if (!Objects.equals(val, item)) {
            return false;
         }
      }
      return true;
   }

   // --- Property sequence functions ---

   static Map<List<String>, List<String>> makePropertySequences(
         List<Map<List<String>, String>> channelProperties) {
      TreeSet<List<String>> allKeys = new TreeSet<>(KEY_ORDER);
      for (Map<List<String>, String> props : channelProperties) {
         if (props != null) {
            allKeys.addAll(props.keySet());
         }
      }
      Map<List<String>, List<String>> result = new TreeMap<>(KEY_ORDER);
      for (List<String> key : allKeys) {
         List<String> values = new ArrayList<>(channelProperties.size());
         for (Map<List<String>, String> props : channelProperties) {
            values.add(props != null ? props.get(key) : null);
         }
         result.put(key, values);
      }
      return result;
   }

   static boolean channelsSequenceable(
         Map<List<String>, List<String>> propertySequences,
         List<AcqChannel> channels, CoreOps core) {
      try {
         for (Map.Entry<List<String>, List<String>> entry
               : propertySequences.entrySet()) {
            List<String> key = entry.getKey();
            List<String> seq = entry.getValue();
            String d = key.get(0);
            String p = key.get(1);
            if (!allEqual(seq)) {
               if (!core.isPropertySequenceable(d, p)
                     || seq.size() > core.getPropertySequenceMaxLength(d, p)) {
                  return false;
               }
            }
         }
      } catch (Exception e) {
         return false;
      }
      List<Double> exposures = new ArrayList<>(channels.size());
      for (AcqChannel ch : channels) {
         exposures.add(ch.exposure);
      }
      return allEqual(exposures);
   }

   static Map<List<String>, List<String>> selectTriggerableSequences(
         Map<List<String>, List<String>> propertySequences,
         CoreOps core) {
      Map<List<String>, List<String>> result = new TreeMap<>(KEY_ORDER);
      try {
         for (Map.Entry<List<String>, List<String>> entry
               : propertySequences.entrySet()) {
            List<String> key = entry.getKey();
            List<String> seq = entry.getValue();
            String d = key.get(0);
            String p = key.get(1);
            if (core.isPropertySequenceable(d, p) && !allEqual(seq)) {
               result.put(key, seq);
            }
         }
      } catch (Exception e) {
         // Return whatever we found so far
      }
      return result;
   }

   // --- Dimension/loop building ---

   private enum DimKind { SLICE, CHANNEL, FRAME, POSITION }

   private static void setDimFields(AcqEvent event, DimKind kind,
         int index, List<?> values) {
      switch (kind) {
         case SLICE:
            event.sliceIndex = index;
            if (values != null && index < values.size()) {
               event.slice = (Double) values.get(index);
            }
            break;
         case CHANNEL:
            event.channelIndex = index;
            if (values != null && index < values.size()) {
               event.channel = (AcqChannel) values.get(index);
            }
            break;
         case FRAME:
            event.frameIndex = index;
            break;
         case POSITION:
            event.positionIndex = index;
            if (values != null && index < values.size()) {
               event.position = (Integer) values.get(index);
            }
            break;
      }
   }

   static List<AcqEvent> makeMainLoops(AcqSettings settings) {
      // Build dimension order matching Clojure make-dimensions
      DimKind[] innerPair;
      if (settings.slicesFirst) {
         innerPair = new DimKind[] { DimKind.SLICE, DimKind.CHANNEL };
      } else {
         innerPair = new DimKind[] { DimKind.CHANNEL, DimKind.SLICE };
      }
      DimKind[] outerPair;
      if (settings.timeFirst) {
         outerPair = new DimKind[] { DimKind.FRAME, DimKind.POSITION };
      } else {
         outerPair = new DimKind[] { DimKind.POSITION, DimKind.FRAME };
      }
      DimKind[] order = {
         innerPair[0], innerPair[1], outerPair[0], outerPair[1]
      };

      List<AcqEvent> events = new ArrayList<>();
      events.add(new AcqEvent());

      for (DimKind kind : order) {
         List<?> values = dimValues(settings, kind);
         if (values != null && !values.isEmpty()) {
            List<AcqEvent> next = new ArrayList<>(
                  events.size() * values.size());
            for (int i = 0; i < values.size(); i++) {
               for (AcqEvent event : events) {
                  AcqEvent e = event.copy();
                  setDimFields(e, kind, i, values);
                  next.add(e);
               }
            }
            events = next;
         } else {
            for (AcqEvent event : events) {
               setDimFields(event, kind, 0, null);
            }
         }
      }
      return events;
   }

   private static List<?> dimValues(AcqSettings settings, DimKind kind) {
      switch (kind) {
         case SLICE: return settings.slices;
         case CHANNEL: return settings.channels;
         case FRAME: return settings.frames;
         case POSITION: return settings.positions;
         default: return null;
      }
   }

   // --- Pipeline functions ---

   static void buildEvent(AcqSettings settings, AcqEvent event) {
      event.exposure = (event.channel != null)
            ? event.channel.exposure
            : settings.defaultExposure;
      event.relativeZ = settings.relativeSlices;
   }

   static List<AcqEvent> processSkipZStack(List<AcqEvent> events,
         List<Double> slices) {
      if (slices == null || slices.isEmpty()) {
         return events;
      }
      Double middleSlice = slices.get(slices.size() / 2);
      List<AcqEvent> result = new ArrayList<>();
      for (AcqEvent e : events) {
         if (Objects.equals(middleSlice, e.slice)
               || e.channel == null
               || e.channel.useZStack) {
            result.add(e);
         }
      }
      return result;
   }

   static List<AcqEvent> processChannelSkipFrames(List<AcqEvent> events) {
      List<AcqEvent> result = new ArrayList<>();
      for (AcqEvent e : events) {
         if (e.channel == null
               || e.channel.skipFrames == 0
               || (e.frameIndex % (e.channel.skipFrames + 1)) == 0) {
            result.add(e);
         }
      }
      return result;
   }

   static List<AcqEvent> processUseAutofocus(List<AcqEvent> events,
         boolean useAutofocus, int autofocusSkip) {
      if (events.isEmpty()) {
         return events;
      }
      AcqEvent prev = null;
      for (AcqEvent curr : events) {
         if (!useAutofocus) {
            curr.autofocus = false;
         } else if (prev == null) {
            curr.autofocus = true;
         } else {
            curr.autofocus =
                  (curr.frameIndex % (autofocusSkip + 1) == 0)
                  && (prev.positionIndex != curr.positionIndex
                        || prev.frameIndex != curr.frameIndex);
         }
         prev = curr;
      }
      return events;
   }

   static List<AcqEvent> processNewPosition(List<AcqEvent> events) {
      if (events.isEmpty()) {
         return events;
      }
      AcqEvent prev = null;
      for (AcqEvent curr : events) {
         curr.newPosition = (prev == null
               || prev.positionIndex != curr.positionIndex);
         prev = curr;
      }
      return events;
   }

   static List<AcqEvent> processWaitTime(List<AcqEvent> events,
         List<Double> customIntervalsMs, double intervalMs) {
      if (events.isEmpty()) {
         return events;
      }
      boolean useCustom = customIntervalsMs != null
            && !customIntervalsMs.isEmpty()
            && customIntervalsMs.get(0) != null;
      events.get(0).waitTimeMs = useCustom
            ? customIntervalsMs.get(0) : 0.0;
      for (int i = 1; i < events.size(); i++) {
         AcqEvent prev = events.get(i - 1);
         AcqEvent curr = events.get(i);
         if (prev.frameIndex != curr.frameIndex) {
            curr.waitTimeMs = useCustom
                  ? customIntervalsMs.get(curr.frameIndex)
                  : intervalMs;
         }
      }
      return events;
   }

   static List<AcqEvent> attachRunnables(List<AcqEvent> events,
         List<AttachedRunnable> runnables) {
      if (runnables == null || runnables.isEmpty()) {
         return events;
      }
      for (AttachedRunnable ar : runnables) {
         for (AcqEvent event : events) {
            if (ar.matches(event)) {
               if (event.runnables == null) {
                  event.runnables = new ArrayList<>();
               }
               event.runnables.add(ar.runnable);
            }
         }
      }
      return events;
   }

   static List<AcqEvent> manageShutter(List<AcqEvent> events,
         boolean keepShutterOpenChannels, boolean keepShutterOpenSlices) {
      for (int i = 0; i < events.size(); i++) {
         AcqEvent e1 = events.get(i);
         AcqEvent e2 = (i + 1 < events.size()) ? events.get(i + 1) : null;
         if (e2 == null) {
            e1.closeShutter = true;
         } else {
            boolean diffChannel = !Objects.equals(e1.channel, e2.channel);
            boolean diffSlice = !Objects.equals(e1.slice, e2.slice);
            boolean diffFrame = e1.frameIndex != e2.frameIndex;
            boolean diffPosition = e1.positionIndex != e2.positionIndex;
            boolean diffCoreShutter = !Objects.equals(
                  getCoreShutterProp(e1), getCoreShutterProp(e2));

            boolean rapidCycleCase =
                  !diffSlice
                  && !diffPosition
                  && keepShutterOpenChannels
                  && (e2.waitTimeMs == null || e2.waitTimeMs == 0.0);

            e1.closeShutter =
                  (!keepShutterOpenChannels && diffChannel)
                  || (!keepShutterOpenSlices && diffSlice)
                  || (diffFrame && !rapidCycleCase)
                  || diffPosition
                  || e2.autofocus
                  || diffCoreShutter;
         }
      }
      return events;
   }

   private static String getCoreShutterProp(AcqEvent e) {
      if (e == null || e.channel == null || e.channel.properties == null) {
         return null;
      }
      return e.channel.properties.get(CORE_SHUTTER_KEY);
   }

   // --- Burst functions ---

   static boolean stageSequenceable(CoreOps core) {
      try {
         String zDrive = core.getFocusDevice();
         if (zDrive == null || zDrive.isEmpty()) {
            return false;
         }
         return core.isStageSequenceable(zDrive);
      } catch (Exception e) {
         return false;
      }
   }

   static boolean sequenceFitsStage(CoreOps core, String zDrive,
         int nSlices) {
      try {
         return nSlices <= core.getStageSequenceMaxLength(zDrive);
      } catch (Exception e) {
         return false;
      }
   }

   static boolean eventTriggerable(List<AcqEvent> burst, AcqEvent event,
         CoreOps core) {
      if (event == null) {
         return false;
      }
      int n = burst.size();
      AcqEvent e1 = burst.get(n - 1);

      List<Map<List<String>, String>> allProps = new ArrayList<>(n + 1);
      List<AcqChannel> allChannels = new ArrayList<>(n + 1);
      for (AcqEvent be : burst) {
         allChannels.add(be.channel);
         allProps.add(be.channel != null ? be.channel.properties : null);
      }
      allChannels.add(event.channel);
      allProps.add(event.channel != null ? event.channel.properties : null);

      if (!channelsSequenceable(makePropertySequences(allProps),
            allChannels, core)) {
         return false;
      }

      if (Objects.equals(e1.slice, event.slice)) {
         return true;
      }

      String zDrive = core.getFocusDevice();
      if (zDrive == null || zDrive.isEmpty()) {
         return false;
      }
      if (!stageSequenceable(core)) {
         return false;
      }
      if (!sequenceFitsStage(core, zDrive, n + 1)) {
         return false;
      }
      if (e1.slice == null || event.slice == null) {
         return false;
      }
      if (Math.abs(e1.slice - event.slice) > MAX_Z_TRIGGER_DIST) {
         return false;
      }
      return e1.sliceIndex <= event.sliceIndex;
   }

   static boolean burstValid(AcqEvent e1, AcqEvent e2) {
      if (e2 == null) {
         return false;
      }
      if (e2.waitTimeMs != null && e2.exposure < e2.waitTimeMs) {
         return false;
      }
      if (e1.exposure != e2.exposure || e1.position != e2.position) {
         return false;
      }
      if (e2.autofocus) {
         return false;
      }
      if (e2.runnables != null && !e2.runnables.isEmpty()) {
         return false;
      }
      return true;
   }

   static TriggerSequence makeTriggers(List<AcqEvent> events,
         CoreOps core) {
      List<Map<List<String>, String>> props = new ArrayList<>(events.size());
      for (AcqEvent e : events) {
         props.add(e.channel != null ? e.channel.properties : null);
      }
      TriggerSequence ts = new TriggerSequence();
      ts.properties = selectTriggerableSequences(
            makePropertySequences(props), core);

      List<Double> slices = new ArrayList<>(events.size());
      boolean allSame = true;
      Double first = events.get(0).slice;
      for (AcqEvent e : events) {
         slices.add(e.slice);
         if (!Objects.equals(first, e.slice)) {
            allSame = false;
         }
      }
      if (!slices.isEmpty() && !allSame && first != null) {
         ts.slices = slices;
      }
      return ts;
   }

   static List<AcqEvent> makeBursts(List<AcqEvent> events, CoreOps core) {
      List<AcqEvent> result = new ArrayList<>();
      int i = 0;
      while (i < events.size()) {
         int burstEnd = i + 1;
         while (burstEnd < events.size()) {
            AcqEvent eLast = events.get(burstEnd - 1);
            AcqEvent eNext = events.get(burstEnd);
            if (!burstValid(eLast, eNext)) {
               break;
            }
            List<AcqEvent> burst = events.subList(i, burstEnd);
            if (!eventTriggerable(burst, eNext, core)) {
               break;
            }
            burstEnd++;
         }

         int burstLen = burstEnd - i;
         AcqEvent burstEvent = events.get(i);
         if (burstLen > 1) {
            burstEvent.task = "burst";
            burstEvent.burstData =
                  new ArrayList<>(events.subList(i, burstEnd));
            burstEvent.burstLength = burstLen;
            burstEvent.triggerSequence =
                  makeTriggers(events.subList(i, burstEnd), core);
         } else {
            burstEvent.task = "snap";
         }
         result.add(burstEvent);
         i = burstEnd;
      }
      return result;
   }

   static List<AcqEvent> addNextTaskTags(List<AcqEvent> events) {
      for (int i = 0; i < events.size(); i++) {
         AcqEvent next = (i + 1 < events.size())
               ? events.get(i + 1) : null;
         events.get(i).nextFrameIndex = (next != null)
               ? next.frameIndex : null;
      }
      return events;
   }

   // --- Metadata ---

   static Map<String, String> makeChannelMetadata(AcqChannel channel) {
      if (channel == null || channel.properties == null
            || channel.properties.isEmpty()) {
         return null;
      }
      Map<String, String> result = new HashMap<>();
      for (Map.Entry<List<String>, String> entry
            : channel.properties.entrySet()) {
         List<String> key = entry.getKey();
         result.put(key.get(0) + "-" + key.get(1), entry.getValue());
      }
      return result;
   }

   // --- Top-level generators ---

   static List<AcqEvent> generateDefaultAcqSequence(AcqSettings settings,
         List<AttachedRunnable> runnables, CoreOps core) {
      List<AcqEvent> events = makeMainLoops(settings);
      for (AcqEvent e : events) {
         buildEvent(settings, e);
      }
      events = processSkipZStack(events, settings.slices);
      events = processChannelSkipFrames(events);
      events = processUseAutofocus(events,
            settings.useAutofocus, settings.autofocusSkip);
      events = processNewPosition(events);

      boolean useCustom = settings.customIntervalsMs != null
            && !settings.customIntervalsMs.isEmpty()
            && settings.customIntervalsMs.get(0) != null;
      events = processWaitTime(events,
            useCustom ? settings.customIntervalsMs : null,
            settings.intervalMs);

      events = attachRunnables(events, runnables);
      events = manageShutter(events,
            settings.keepShutterOpenChannels,
            settings.keepShutterOpenSlices);
      events = makeBursts(events, core);
      events = addNextTaskTags(events);
      return events;
   }

   static List<AcqEvent> generateSimpleBurstSequence(
         int numFrames, boolean useAutofocus,
         List<AcqChannel> channels, List<Double> slices,
         double defaultExposure,
         TriggerSequence propertyTriggers, int positionIndex,
         boolean relativeSlices, CoreOps core) {
      int nChannels = Math.max(1, channels.size());
      int nSlices = Math.max(1, slices.size());
      numFrames = Math.max(1, numFrames);
      double exposure = !channels.isEmpty()
            ? channels.get(0).exposure : defaultExposure;

      List<AcqEvent> rawEvents = new ArrayList<>(
            numFrames * nSlices * nChannels);
      for (int f = 0; f < numFrames; f++) {
         for (int s = 0; s < nSlices; s++) {
            for (int c = 0; c < nChannels; c++) {
               AcqEvent e = new AcqEvent();
               e.nextFrameIndex = f + 1;
               e.waitTimeMs = 0.0;
               e.exposure = exposure;
               e.positionIndex = positionIndex;
               e.position = positionIndex;
               e.autofocus = (f == 0 && c == 0) && useAutofocus;
               e.channelIndex = c;
               e.channel = c < channels.size()
                     ? channels.get(c) : null;
               e.sliceIndex = s;
               e.slice = s < slices.size() ? slices.get(s) : null;
               e.frameIndex = f;
               e.metadata = makeChannelMetadata(
                     c < channels.size() ? channels.get(c) : null);
               rawEvents.add(e);
            }
         }
      }

      // Partition by frame-index if multiple slices, else single group
      List<List<AcqEvent>> partitions;
      if (nSlices > 1) {
         partitions = partitionByFrameIndex(rawEvents);
      } else {
         partitions = Collections.singletonList(rawEvents);
      }

      List<AcqEvent> result = new ArrayList<>(partitions.size());
      for (List<AcqEvent> partition : partitions) {
         AcqEvent burstEvent = partition.get(0);
         burstEvent.task = "burst";
         burstEvent.burstData = partition;
         burstEvent.burstLength = partition.size();
         burstEvent.relativeZ = relativeSlices;

         TriggerSequence ts = makeTriggers(partition, core);
         if (propertyTriggers != null
               && propertyTriggers.properties != null) {
            ts.properties = propertyTriggers.properties;
         }
         burstEvent.triggerSequence = ts;
         result.add(burstEvent);
      }
      return result;
   }

   private static List<List<AcqEvent>> partitionByFrameIndex(
         List<AcqEvent> events) {
      List<List<AcqEvent>> partitions = new ArrayList<>();
      List<AcqEvent> current = new ArrayList<>();
      int currentFrame = -1;
      for (AcqEvent e : events) {
         if (current.isEmpty() || e.frameIndex == currentFrame) {
            current.add(e);
         } else {
            partitions.add(current);
            current = new ArrayList<>();
            current.add(e);
         }
         currentFrame = e.frameIndex;
      }
      if (!current.isEmpty()) {
         partitions.add(current);
      }
      return partitions;
   }

   static List<AcqEvent> generateMultipositionBursts(
         List<Integer> positions, int numFrames, boolean useAutofocus,
         List<AcqChannel> channels, List<Double> slices,
         double defaultExposure, TriggerSequence triggers,
         boolean relativeSlices, CoreOps core) {
      List<AcqEvent> allEvents = new ArrayList<>();
      for (int posIndex = 0; posIndex < positions.size(); posIndex++) {
         int posValue = positions.get(posIndex);
         List<AcqEvent> posEvents = generateSimpleBurstSequence(
               numFrames, useAutofocus, channels, slices,
               defaultExposure, triggers, posIndex, relativeSlices,
               core);
         for (AcqEvent e : posEvents) {
            e.positionIndex = posIndex;
            e.position = posValue;
         }
         allEvents.addAll(posEvents);
      }
      return processNewPosition(allEvents);
   }

   public static List<AcqEvent> generateAcqSequence(AcqSettings settings,
         List<AttachedRunnable> runnables, CoreOps core) {
      List<AcqChannel> channels = settings.channels;
      List<Double> slices = settings.slices;
      List<Integer> positions = settings.positions;
      int numFrames = settings.numFrames;
      boolean timeFirst = settings.timeFirst;
      boolean slicesFirst = settings.slicesFirst;
      boolean useAutofocus = settings.useAutofocus;
      int autofocusSkip = settings.autofocusSkip;
      double intervalMs = settings.intervalMs;
      double defaultExposure = settings.defaultExposure;
      boolean relativeSlices = settings.relativeSlices;

      List<Map<List<String>, String>> channelProps =
            new ArrayList<>(channels.size());
      for (AcqChannel ch : channels) {
         channelProps.add(ch.properties);
      }
      Map<List<String>, List<String>> propertySequences =
            makePropertySequences(channelProps);

      int nSlices = slices.size();
      boolean haveMultipleFrames = numFrames > 1;
      boolean haveMultiplePositions = positions != null
            && positions.size() > 1;
      boolean haveMultipleSlices = nSlices > 1;
      boolean haveMultipleChannels = channels.size() > 1;

      boolean channelPropsSequenceable =
            channelsSequenceable(propertySequences, channels, core);
      boolean slicesSeq = stageSequenceable(core)
            && sequenceFitsStage(core, core.getFocusDevice(), nSlices);

      List<Integer> skipFramesList = new ArrayList<>(channels.size());
      for (AcqChannel ch : channels) {
         skipFramesList.add(ch.skipFrames);
      }
      boolean noChannelSkipsFrames = allEqual(0, skipFramesList);

      List<Boolean> doZList = new ArrayList<>(channels.size());
      for (AcqChannel ch : channels) {
         doZList.add(ch.useZStack);
      }
      boolean allChannelsDoZStack = allEqual(true, doZList);

      double channelTotalExposure;
      if (!channels.isEmpty()) {
         double sum = 0;
         for (AcqChannel ch : channels) {
            sum += ch.exposure;
         }
         channelTotalExposure = sum;
      } else {
         channelTotalExposure = defaultExposure;
      }

      List<Double> customIntervalsMs = settings.customIntervalsMs;
      boolean hasCustomIntervals = customIntervalsMs != null
            && !customIntervalsMs.isEmpty()
            && customIntervalsMs.get(0) != null;

      boolean useFastBurst =
            (haveMultipleFrames || haveMultipleSlices
                  || haveMultipleChannels)
            && (timeFirst || !haveMultiplePositions)
            && !(haveMultipleSlices && !slicesSeq)
            && !(haveMultipleChannels && !channelPropsSequenceable)
            && !(haveMultipleChannels && !noChannelSkipsFrames)
            && !(haveMultipleSlices && haveMultipleChannels
                  && !allChannelsDoZStack)
            && !(haveMultipleSlices && haveMultipleChannels
                  && slicesFirst)
            && (!useAutofocus || autofocusSkip >= numFrames - 1)
            && (runnables == null || runnables.isEmpty())
            && !hasCustomIntervals
            && intervalMs < channelTotalExposure;

      if (useFastBurst) {
         TriggerSequence triggers = new TriggerSequence();
         triggers.properties = selectTriggerableSequences(
               propertySequences, core);
         if (haveMultiplePositions) {
            return generateMultipositionBursts(
                  positions, numFrames, useAutofocus, channels,
                  slices, defaultExposure, triggers, relativeSlices,
                  core);
         } else {
            return generateSimpleBurstSequence(
                  numFrames, useAutofocus, channels, slices,
                  defaultExposure, triggers, 0, relativeSlices, core);
         }
      } else {
         return generateDefaultAcqSequence(settings, runnables, core);
      }
   }
}
