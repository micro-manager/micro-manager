package org.micromanager.display.internal.ndviewer2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.ndviewer.main.NDViewer;

/**
 * Utility class translating between NDViewer's HashMap axes and MM's Coords.
 *
 * <p>NDViewer channel axis values can be either string names (e.g., "DAPI")
 * or integer indices. MM Coords channels are always integer indices. This
 * bridge maintains an ordered list mapping between the two representations,
 * and preserves the original value type for round-trip fidelity.</p>
 */
public final class AxesBridge {

   // Ordered list of channel values as they appear in NDViewer.
   // Index in this list == MM channel index.
   // Values can be String names or Integer indices (stored as Object).
   private final List<Object> channelValues_ =
         Collections.synchronizedList(new ArrayList<>());

   /**
    * Register a channel value if it is not already known.
    *
    * @param channelValue the NDViewer channel value (String or Integer)
    * @return the MM channel index assigned to this channel
    */
   public int registerChannel(Object channelValue) {
      synchronized (channelValues_) {
         int idx = channelValues_.indexOf(channelValue);
         if (idx < 0) {
            channelValues_.add(channelValue);
            idx = channelValues_.size() - 1;
         }
         return idx;
      }
   }

   /**
    * Return the NDViewer channel value for a given MM channel index.
    *
    * @param index the MM channel index
    * @return the channel value (String or Integer), or null if out of range
    */
   public Object getChannelValue(int index) {
      synchronized (channelValues_) {
         if (index >= 0 && index < channelValues_.size()) {
            return channelValues_.get(index);
         }
         return null;
      }
   }

   /**
    * Return a snapshot of the current channel value list.
    *
    * @return ordered list of channel values
    */
   public List<Object> getChannelValues() {
      synchronized (channelValues_) {
         return new ArrayList<>(channelValues_);
      }
   }

   /**
    * Return the channel names as strings (for DisplaySettingsBridge).
    * Integer channel values are converted to their string representation.
    *
    * @return ordered list of channel names
    */
   public List<String> getChannelNames() {
      synchronized (channelValues_) {
         List<String> names = new ArrayList<>(channelValues_.size());
         for (Object v : channelValues_) {
            names.add(v.toString());
         }
         return names;
      }
   }

   /**
    * Return the number of known channels.
    *
    * @return number of channels
    */
   public int getChannelCount() {
      synchronized (channelValues_) {
         return channelValues_.size();
      }
   }

   /**
    * Convert NDViewer axes to MM Coords.
    *
    * @param axes NDViewer axes map
    * @return equivalent MM Coords
    */
   public Coords ndViewerToCoords(HashMap<String, Object> axes) {
      Coords.Builder b = Coordinates.builder();
      for (Map.Entry<String, Object> entry : axes.entrySet()) {
         String key = entry.getKey();
         Object value = entry.getValue();
         if (NDViewer.CHANNEL_AXIS.equals(key)) {
            // Register the raw value (Integer or String) and map to index
            b.channel(registerChannel(value));
         } else if (Coords.Z_SLICE.equals(key)) {
            b.zSlice(toInt(value));
         } else if (Coords.TIME_POINT.equals(key)) {
            b.timePoint(toInt(value));
         } else if (Coords.STAGE_POSITION.equals(key)) {
            b.stagePosition(toInt(value));
         } else {
            // Custom axes pass through as-is with integer values
            b.index(key, toInt(value));
         }
      }
      return b.build();
   }

   /**
    * Convert MM Coords to NDViewer axes.
    *
    * @param coords MM Coords
    * @return equivalent NDViewer axes map
    */
   public HashMap<String, Object> coordsToNDViewer(Coords coords) {
      HashMap<String, Object> axes = new HashMap<>();
      for (String axis : coords.getAxes()) {
         int value = coords.getIndex(axis);
         if (Coords.CHANNEL.equals(axis)) {
            // Handled below to cover index-0 case
            continue;
         }
         if (value == 0) {
            continue; // Coords treats 0 as "absent" for non-channel axes
         }
         axes.put(axis, value);
      }
      // Handle channel: Coords.getChannel() returns 0 for both "absent" and
      // "index 0", so we check hasChannelAxis() explicitly.
      if (coords.hasChannelAxis()) {
         Object chValue = getChannelValue(coords.getChannel());
         if (chValue != null) {
            axes.put(NDViewer.CHANNEL_AXIS, chValue);
         }
      }
      return axes;
   }

   /**
    * Seed the channel list from a set of NDViewer axes keys.
    *
    * @param axesSet the set of all axes maps in the storage
    */
   public void discoverChannels(Set<HashMap<String, Object>> axesSet) {
      for (HashMap<String, Object> axes : axesSet) {
         Object ch = axes.get(NDViewer.CHANNEL_AXIS);
         if (ch != null) {
            registerChannel(ch);
         }
      }
   }

   private static int toInt(Object value) {
      if (value instanceof Number) {
         return ((Number) value).intValue();
      }
      try {
         return Integer.parseInt(value.toString());
      } catch (NumberFormatException e) {
         System.err.println("AxesBridge: cannot parse axis value as integer: " + value);
         return 0;
      }
   }
}
