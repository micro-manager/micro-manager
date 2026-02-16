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

   static Seq<AcqEvent> makeMainLoops(AcqSettings settings) {
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

      Seq<AcqEvent> events = Seq.cons(new AcqEvent(), Seq.empty());

      for (DimKind kind : order) {
         List<?> values = dimValues(settings, kind);
         if (values != null && !values.isEmpty()) {
            final Seq<AcqEvent> prev = events;
            events = Seq.range(values.size()).flatMap(i ->
                  prev.map(event -> {
                     AcqEvent e = event.copy();
                     setDimFields(e, kind, i, values);
                     return e;
                  })
            );
         } else {
            events = events.map(event -> {
               setDimFields(event, kind, 0, null);
               return event;
            });
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

   static Seq<AcqEvent> processSkipZStack(Seq<AcqEvent> events,
         List<Double> slices) {
      if (slices == null || slices.isEmpty()) {
         return events;
      }
      Double middleSlice = slices.get(slices.size() / 2);
      return events.filter(e ->
            Objects.equals(middleSlice, e.slice)
                  || e.channel == null
                  || e.channel.useZStack);
   }

   static Seq<AcqEvent> processChannelSkipFrames(Seq<AcqEvent> events) {
      return events.filter(e ->
            e.channel == null
                  || e.channel.skipFrames == 0
                  || (e.frameIndex % (e.channel.skipFrames + 1)) == 0);
   }

   static Seq<AcqEvent> processUseAutofocus(Seq<AcqEvent> events,
         boolean useAutofocus, int autofocusSkip) {
      return events.mapWithPrev((prev, curr) -> {
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
         return curr;
      });
   }

   static Seq<AcqEvent> processNewPosition(Seq<AcqEvent> events) {
      return events.mapWithPrev((prev, curr) -> {
         curr.newPosition = (prev == null
               || prev.positionIndex != curr.positionIndex);
         return curr;
      });
   }

   static Seq<AcqEvent> processWaitTime(Seq<AcqEvent> events,
         List<Double> customIntervalsMs, double intervalMs) {
      boolean useCustom = customIntervalsMs != null
            && !customIntervalsMs.isEmpty()
            && customIntervalsMs.get(0) != null;
      return events.mapWithPrev((prev, curr) -> {
         if (prev == null) {
            curr.waitTimeMs = useCustom
                  ? customIntervalsMs.get(0) : 0.0;
         } else if (prev.frameIndex != curr.frameIndex) {
            curr.waitTimeMs = useCustom
                  ? customIntervalsMs.get(curr.frameIndex)
                  : intervalMs;
         }
         return curr;
      });
   }

   static Seq<AcqEvent> attachRunnables(Seq<AcqEvent> events,
         List<AttachedRunnable> runnables) {
      if (runnables == null || runnables.isEmpty()) {
         return events;
      }
      for (AttachedRunnable ar : runnables) {
         events = events.map(event -> {
            if (ar.matches(event)) {
               if (event.runnables == null) {
                  event.runnables = new ArrayList<>();
               }
               event.runnables.add(ar.runnable);
            }
            return event;
         });
      }
      return events;
   }

   static Seq<AcqEvent> manageShutter(Seq<AcqEvent> events,
         boolean keepShutterOpenChannels, boolean keepShutterOpenSlices) {
      return events.mapWithNext((e1, e2) -> {
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
         return e1;
      });
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

   static Seq<AcqEvent> makeBursts(Seq<AcqEvent> events, CoreOps core) {
      return Seq.lazy(() -> {
         if (events.isEmpty()) {
            return Seq.empty();
         }
         List<AcqEvent> burst = new ArrayList<>();
         burst.add(events.first());
         Seq<AcqEvent> remaining = events.rest();

         while (!remaining.isEmpty()) {
            AcqEvent eLast = burst.get(burst.size() - 1);
            AcqEvent eNext = remaining.first();
            if (!burstValid(eLast, eNext)) {
               break;
            }
            if (!eventTriggerable(burst, eNext, core)) {
               break;
            }
            burst.add(eNext);
            remaining = remaining.rest();
         }

         AcqEvent burstEvent;
         if (burst.size() > 1) {
            burstEvent = burst.get(0).copy();
            burstEvent.task = "burst";
            burstEvent.burstData = burst;
            burstEvent.burstLength = burst.size();
            burstEvent.triggerSequence = makeTriggers(burst, core);
         } else {
            burstEvent = burst.get(0);
            burstEvent.task = "snap";
         }
         Seq<AcqEvent> tail = remaining;
         return Seq.cons(burstEvent, () -> makeBursts(tail, core));
      });
   }

   static Seq<AcqEvent> addNextTaskTags(Seq<AcqEvent> events) {
      return events.mapWithNext((curr, next) -> {
         curr.nextFrameIndex = (next != null)
               ? next.frameIndex : null;
         return curr;
      });
   }

   // --- Metadata ---

   static Map<String, String> makeChannelMetadata(AcqChannel channel) {
      if (channel == null || channel.properties == null) {
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

   static Seq<AcqEvent> generateDefaultAcqSequence(AcqSettings settings,
         List<AttachedRunnable> runnables, CoreOps core) {
      Seq<AcqEvent> events = makeMainLoops(settings);
      events = events.map(e -> {
         buildEvent(settings, e);
         return e;
      });
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

   static Seq<AcqEvent> generateSimpleBurstSequence(
         int numFrames, boolean useAutofocus,
         List<AcqChannel> channels, List<Double> slices,
         double defaultExposure,
         TriggerSequence propertyTriggers, int positionIndex,
         boolean relativeSlices, CoreOps core) {
      int nChannels = Math.max(1, channels.size());
      int nSlices = Math.max(1, slices.size());
      int nF = Math.max(1, numFrames);
      double exposure = !channels.isEmpty()
            ? channels.get(0).exposure : defaultExposure;

      Seq<AcqEvent> rawEvents = Seq.range(nF).flatMap(f ->
            Seq.range(nSlices).flatMap(s ->
                  Seq.range(nChannels).map(c -> {
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
                     return e;
                  })
            )
      );

      Seq<List<AcqEvent>> partitions;
      if (nSlices > 1) {
         partitions = rawEvents.partitionBy(e -> e.frameIndex);
      } else {
         partitions = Seq.lazy(() ->
               Seq.cons(rawEvents.toList(), Seq.empty()));
      }

      return partitions.map(partition -> {
         AcqEvent burstEvent = partition.get(0).copy();
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
         return burstEvent;
      });
   }

   static Seq<AcqEvent> generateMultipositionBursts(
         List<Integer> positions, int numFrames, boolean useAutofocus,
         List<AcqChannel> channels, List<Double> slices,
         double defaultExposure, TriggerSequence triggers,
         boolean relativeSlices, CoreOps core) {
      Seq<AcqEvent> allEvents =
            Seq.range(positions.size()).flatMap(posIndex -> {
               int posValue = positions.get(posIndex);
               return generateSimpleBurstSequence(
                     numFrames, useAutofocus, channels, slices,
                     defaultExposure, triggers, posIndex, relativeSlices,
                     core).map(e -> {
                        e.positionIndex = posIndex;
                        e.position = posValue;
                        return e;
                     });
            });
      return processNewPosition(allEvents);
   }

   public static Seq<AcqEvent> generateAcqSequence(AcqSettings settings,
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
