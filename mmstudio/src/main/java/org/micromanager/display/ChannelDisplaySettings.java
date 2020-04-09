
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
      Builder groupName(String groupName);
      Builder visible(boolean visible);
      Builder show();
      Builder hide();

      Builder component(int component);
      Builder component(int component, ComponentDisplaySettings settings);
      int getNumberOfComponents();
      ComponentDisplaySettings getComponentSettings(int component);

      ChannelDisplaySettings build();
   }

   /**
    * Color used to represent this channel, i.e., brightest color of the LUT
    * used to display this channel
    *
    * @return Color for this channel
    */
   Color getColor();

   boolean isUniformComponentScalingEnabled();

   /**
    * Range of this histogram displayed for this channel
    * For now, histogram always starts at zero, so this represents
    * the maximum value on the x-axis of the histogram.
    *
    * @return Maximum value on the x-axis of the histogram expressed as a factor of 2
    */
   int getHistoRangeBits();

   /**
    * @return True when historangebits is equal to the maximum intensity coming from the camera
    */
   boolean useCameraRange();

   /**
    * @return True when this channel is visible to the user, false otherwise
    */
   boolean isVisible();

   /**
    * @return Name of the channel being displayed.
    */
   String getName();

   /**
    * @return Name of the channelGroup this channel belongs to
    */
   String getGroupName();

   int getNumberOfComponents();
   ComponentDisplaySettings getComponentSettings(int component);
   List<ComponentDisplaySettings> getAllComponentSettings();

   Builder copyBuilder();
   Builder copyBuilderWithComponentSettings(int component, ComponentDisplaySettings settings);

}