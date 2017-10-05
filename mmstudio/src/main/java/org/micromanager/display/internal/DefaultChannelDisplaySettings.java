
package org.micromanager.display.internal;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.internal.utils.ColorPalettes;

/**
 *
 * @author mark
 */
public final class DefaultChannelDisplaySettings
      implements ChannelDisplaySettings
{
   private final Color color_;
   private final boolean useUniformComponentScaling_;
   private final boolean visible_;
   private final List<ComponentDisplaySettings> componentSettings_;

   private static final class Builder
         implements ChannelDisplaySettings.Builder
   {
      private Color color_ = Color.WHITE;
      private boolean useUniformComponentScaling_ = false;
      private boolean visible_ = true;
      private final List<ComponentDisplaySettings> componentSettings_ =
            new ArrayList<ComponentDisplaySettings>();

      private Builder() {
         componentSettings_.add(DefaultComponentDisplaySettings.builder().build());
      }

      @Override
      public Builder color(Color color) {
         Preconditions.checkNotNull(color);
         color_ = color;
         return this;
      }

      @Override
      public Builder colorWhite() {
         return color(Color.WHITE);
      }

      @Override
      public Builder colorColorBlindFriendly(int number) {
         return color(ColorPalettes.getFromColorblindFriendlyPalette(number));
      }
    
      @Override
      public Builder colorRed() {
         return color(Color.RED);
      }

      @Override
      public Builder colorGreen() {
         return color(Color.GREEN);
      }

      @Override
      public Builder colorBlue() {
         return color(Color.BLUE);
      }

      @Override
      public Builder colorCyan() {
         return color(Color.CYAN);
      }

      @Override
      public Builder colorMagenta() {
         return color(Color.MAGENTA);
      }

      @Override
      public Builder colorYellow() {
         return color(Color.YELLOW);
      }

      @Override
      public Builder uniformComponentScaling(boolean enable) {
         useUniformComponentScaling_ = enable;
         return this;
      }

      @Override
      public Builder visible(boolean visible) {
         visible_ = visible;
         return this;
      }

      @Override
      public Builder show() {
         return visible(true);
      }

      @Override
      public Builder hide() {
         return visible(false);
      }

      @Override
      public Builder component(int component) {
         Preconditions.checkArgument(component >= 0);
         while (componentSettings_.size() <= component) {
            componentSettings_.add(DefaultComponentDisplaySettings.builder().build());
         }
         return this;
      }

      @Override
      public Builder component(int component, ComponentDisplaySettings settings) {
         Preconditions.checkNotNull(settings);
         component(component);
         componentSettings_.set(component, settings);
         return this;
      }

      @Override
      public int getNumberOfComponents() {
         return componentSettings_.size();
      }

      @Override
      public ComponentDisplaySettings getComponentSettings(int component) {
         Preconditions.checkArgument(component >= 0);
         component(component);
         return componentSettings_.get(component);
      }

      @Override
      public ChannelDisplaySettings build() {
         return new DefaultChannelDisplaySettings(this);
      }
   }

   public static ChannelDisplaySettings.Builder builder() {
      return new Builder();
   }

   private DefaultChannelDisplaySettings(Builder builder) {
      color_ = builder.color_;
      useUniformComponentScaling_ = builder.useUniformComponentScaling_;
      visible_ = builder.visible_;
      componentSettings_ = new ArrayList<ComponentDisplaySettings>(
            builder.componentSettings_);
   }

   @Override
   public Color getColor() {
      return color_;
   }

   @Override
   public boolean isUniformComponentScalingEnabled() {
      return useUniformComponentScaling_;
   }

   @Override
   public boolean isVisible() {
      return visible_;
   }

   @Override
   public int getNumberOfComponents() {
      return componentSettings_.size();
   }

   @Override
   public ComponentDisplaySettings getComponentSettings(int component) {
      if (component >= componentSettings_.size()) {
         return DefaultComponentDisplaySettings.builder().build();
      }
      return componentSettings_.get(component);
   }

   @Override
   public List<ComponentDisplaySettings> getAllComponentSettings() {
      return new ArrayList<ComponentDisplaySettings>(componentSettings_);
   }

   @Override
   public ChannelDisplaySettings.Builder copyBuilder() {
      Builder builder = new Builder();
      builder.color_ = color_;
      builder.useUniformComponentScaling_ = useUniformComponentScaling_;
      builder.visible_ = visible_;
      builder.componentSettings_.clear();
      builder.componentSettings_.addAll(componentSettings_);
      return builder;
   }

   @Override
   public ChannelDisplaySettings.Builder copyBuilderWithComponentSettings(
         int component, ComponentDisplaySettings settings)
   {
      return copyBuilder().component(component, settings);
   }

   public PropertyMap toPropertyMap() {
      List<PropertyMap> componentSettings = new ArrayList<PropertyMap>();
      for (ComponentDisplaySettings cs : componentSettings_) {
         componentSettings.add(((DefaultComponentDisplaySettings) cs).toPropertyMap());
      }
      return PropertyMaps.builder().
            putColor("Color", color_).
            putBoolean("UniformComponentScaling", useUniformComponentScaling_).
            putBoolean("Visible", visible_).
            putPropertyMapList("ComponentSettings", componentSettings).
            build();
   }
}