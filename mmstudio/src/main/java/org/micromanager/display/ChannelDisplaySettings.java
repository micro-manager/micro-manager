
package org.micromanager.display;

import java.awt.Color;
import java.util.List;

/**
 * Stores the display settings for individual channels
 * Maintains the state of things like channel color, visibility,
 * min, max, gamma, and histogram range
 * 
 * @author mark
 */
public interface ChannelDisplaySettings {
   public interface Builder {
      Builder color(Color color);
      Builder colorWhite();
      Builder colorColorBlindFriendly(int number);
      Builder colorRed();
      Builder colorGreen();
      Builder colorBlue();
      Builder colorCyan();
      Builder colorMagenta();
      Builder colorYellow();

      Builder uniformComponentScaling(boolean enable);
      Builder histoRangeBits(int bits);
      Builder useCameraHistoRange(boolean use);
      
      Builder name(String name);
      Builder visible(boolean visible);
      Builder show();
      Builder hide();

      Builder component(int component);
      Builder component(int component, ComponentDisplaySettings settings);
      int getNumberOfComponents();
      ComponentDisplaySettings getComponentSettings(int component);

      ChannelDisplaySettings build();
   }

   Color getColor();
   boolean isUniformComponentScalingEnabled();
   int getHistoRangeBits();
   boolean useCameraRange();
   boolean isVisible();
   String getName();

   int getNumberOfComponents();
   ComponentDisplaySettings getComponentSettings(int component);
   List<ComponentDisplaySettings> getAllComponentSettings();

   Builder copyBuilder();
   Builder copyBuilderWithComponentSettings(int component, ComponentDisplaySettings settings);

   // TODO Add static builder() in Java 8
}