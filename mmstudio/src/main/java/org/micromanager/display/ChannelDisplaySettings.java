/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display;

import java.awt.Color;
import java.util.List;

/**
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
   boolean isVisible();

   int getNumberOfComponents();
   ComponentDisplaySettings getComponentSettings(int component);
   List<ComponentDisplaySettings> getAllComponentSettings();

   Builder copyBuilder();
   Builder copyBuilderWithComponentSettings(int component, ComponentDisplaySettings settings);

   // TODO Add static builder() in Java 8
}