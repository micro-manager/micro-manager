package org.micromanager.internal.jacque;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import mmcorej.CMMCore;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import org.micromanager.acquisition.ChannelSpec;

final class AcqChannel {
   public String name;
   public double exposure;
   public double zOffset;
   public boolean useZStack;
   public boolean useChannel;
   public int skipFrames;
   public Color color;
   public Map<List<String>, String> properties;

   public AcqChannel() {
      this.properties = new TreeMap<>();
   }

   public static AcqChannel fromChannelSpec(String channelGroup,
         ChannelSpec cs, CMMCore mmc) throws Exception {
      AcqChannel ch = new AcqChannel();
      ch.name = cs.config();
      ch.exposure = cs.exposure();
      ch.zOffset = cs.zOffset();
      ch.useZStack = cs.doZStack();
      ch.skipFrames = cs.skipFactorFrame();
      ch.useChannel = cs.useChannel();
      ch.color = cs.color();
      ch.properties = configToProperties(
            mmc.getConfigData(channelGroup, cs.config()));
      return ch;
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof AcqChannel)) return false;
      AcqChannel that = (AcqChannel) o;
      return Double.compare(that.exposure, exposure) == 0
            && Double.compare(that.zOffset, zOffset) == 0
            && useZStack == that.useZStack
            && skipFrames == that.skipFrames
            && useChannel == that.useChannel
            && Objects.equals(name, that.name)
            && Objects.equals(color, that.color)
            && Objects.equals(properties, that.properties);
   }

   @Override
   public int hashCode() {
      return Objects.hash(name, exposure, zOffset, useZStack, skipFrames,
            useChannel, color, properties);
   }

   private static Map<List<String>, String> configToProperties(
         Configuration config) {
      Map<List<String>, String> props = new TreeMap<>();
      for (long i = 0; i < config.size(); i++) {
         PropertySetting ps;
         try {
            ps = config.getSetting(i);
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
         List<String> key = new ArrayList<>(2);
         key.add(ps.getDeviceLabel());
         key.add(ps.getPropertyName());
         props.put(Collections.unmodifiableList(key),
               ps.getPropertyValue());
      }
      return props;
   }
}
