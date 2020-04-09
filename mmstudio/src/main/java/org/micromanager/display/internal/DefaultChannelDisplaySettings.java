
package org.micromanager.display.internal;

import com.google.common.base.Preconditions;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.data.internal.PropertyKey;
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
   private final String name_;
   private final String groupName_;
   private final boolean useUniformComponentScaling_;
   private final boolean visible_;
   private final int histoRangeBits_;
   private final boolean useCameraRange_;
   private final List<ComponentDisplaySettings> componentSettings_;

  
   private static final class Builder
         implements ChannelDisplaySettings.Builder
   {
      private Color color_ = Color.WHITE;
      private String name_ = "";
      private String groupName_ = "";
      private boolean useUniformComponentScaling_ = false;
      private boolean visible_ = true;
      private int histoRangeBits_ = 8; 
      private boolean useCameraRange_ = true;
      private final List<ComponentDisplaySettings> componentSettings_ =
            new ArrayList<>();

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
      public Builder name(String name) {
         name_ = name;
         return this;
      }

      @Override
      public Builder groupName(String groupName) {
         groupName_ = groupName;
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
      public Builder histoRangeBits(int bits) {
         histoRangeBits_ = bits;
         return this;
      }
      
      @Override
      public Builder useCameraHistoRange(boolean use) {
         useCameraRange_ = use;
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
      name_ = builder.name_;
      groupName_ = builder.groupName_;
      useUniformComponentScaling_ = builder.useUniformComponentScaling_;
      visible_ = builder.visible_;
      histoRangeBits_ = builder.histoRangeBits_;
      useCameraRange_ = builder.useCameraRange_;
      componentSettings_ = new ArrayList<>(builder.componentSettings_);
   }

   @Override
   public Color getColor() {
      return color_;
   }
   
   @Override
   public String getName() {
      return name_;
   }

   @Override
   public String getGroupName() {
      return groupName_;
   }

   @Override
   public boolean isUniformComponentScalingEnabled() {
      return useUniformComponentScaling_;
   }
   
   @Override
   public int getHistoRangeBits() {
      return histoRangeBits_;
   }
   
   @Override
   public boolean useCameraRange() {
      return useCameraRange_;
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
      return new ArrayList<>(componentSettings_);
   }

   @Override
   public ChannelDisplaySettings.Builder copyBuilder() {
      Builder builder = new Builder();
      builder.color_ = color_;
      builder.useUniformComponentScaling_ = useUniformComponentScaling_;
      builder.visible_ = visible_;
      builder.name_ = name_;
      builder.groupName_ = groupName_;
      builder.histoRangeBits_= histoRangeBits_;
      builder.useCameraRange_ = useCameraRange_;
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

   /**
    * Encodes these ChannelDisplaySettings into a PropertyMap
    * @return PropertyMap encoding the current ChannelDisplaySettings
    */
   public PropertyMap toPropertyMap() {
      List<PropertyMap> componentSettings = new ArrayList<>();
      for (ComponentDisplaySettings cs : componentSettings_) {
         componentSettings.add(((DefaultComponentDisplaySettings) cs).toPropertyMap());
      }

      return PropertyMaps.builder().
            putColor(PropertyKey.COLOR.key(), color_).
            putString(PropertyKey.CHANNEL_NAME.key(), name_).
            putString(PropertyKey.CHANNEL_GROUP.key(), groupName_).
            putBoolean(PropertyKey.UNIFORM_COMPONENT_SCALING.key(), useUniformComponentScaling_).
            putBoolean(PropertyKey.VISIBLE.key(), visible_).
            putInteger(PropertyKey.HISTOGRAM_BIT_DEPTH.key(), histoRangeBits_).
            putBoolean(PropertyKey.USE_CAMERA_BIT_DEPTH.key(), useCameraRange_).
            putPropertyMapList(PropertyKey.COMPONENT_SETTINGS.key(), componentSettings).
            build();
   }

   /**
    * Helper function for overloaded versions of fromPropertyMap
    * Restores everything form the propertymap except for Channelgroup and
    * ChannelName
    * @param pMap
    * @return Builder with everything useful in the propertymap except for channelGroup
    * and channelName
    */
   private static ChannelDisplaySettings.Builder partialBuilderFromPropertyMap(PropertyMap pMap) {
      Builder b = new Builder();

      if (pMap.containsColor(PropertyKey.COLOR.key())) {
         b.color(pMap.getColor(PropertyKey.COLOR.key(), b.color_));
      }

      if (pMap.containsBoolean(PropertyKey.UNIFORM_COMPONENT_SCALING.key())) {
         b.uniformComponentScaling(pMap.getBoolean(
                 PropertyKey.UNIFORM_COMPONENT_SCALING.key(), b.useUniformComponentScaling_));
      }
      if (pMap.containsBoolean(PropertyKey.VISIBLE.key())) {
         b.visible(pMap.getBoolean(PropertyKey.VISIBLE.key(), b.visible_));
      }
      if (pMap.containsPropertyMapList(PropertyKey.COMPONENT_SETTINGS.key())) {
         List<PropertyMap> componentMapList = pMap.getPropertyMapList(
                 PropertyKey.COMPONENT_SETTINGS.key(), new ArrayList<>());
         for (int i = 0; i < componentMapList.size(); i++) {
            ComponentDisplaySettings cds = DefaultComponentDisplaySettings.fromPropertyMap(
                    componentMapList.get(i));
            b.component(i, cds);
         }
      }
      if (pMap.containsInteger(PropertyKey.HISTOGRAM_BIT_DEPTH.key())) {
         b.histoRangeBits(pMap.getInteger(PropertyKey.HISTOGRAM_BIT_DEPTH.key(),
                 b.histoRangeBits_));
      }
      if (pMap.containsBoolean(PropertyKey.USE_CAMERA_BIT_DEPTH.key())) {
         b.useCameraHistoRange(pMap.getBoolean(PropertyKey.USE_CAMERA_BIT_DEPTH.key(),
                 b.useCameraRange_));
      }

      return b;
   }

   /**
    * Restores ChannelDisplaySettings from a PropertyMap, but uses the input
    * channelGroup and channelName rather than the ones in the PropertyMap
    * Needed to gracefully update propertymaps that did not yet store the
    * channelGroup
    *
    * @param pMap PropertyMap from which to restore the ChannelDisplaySettings
    * @return ChannelDisplaySettings.  Missing values are replaced by defaults.
    */
   public static ChannelDisplaySettings fromPropertyMap (PropertyMap pMap,
                                                         String channelGroup, String channelName) {
      ChannelDisplaySettings.Builder b = partialBuilderFromPropertyMap(pMap);

      return b.name(channelName).groupName(channelGroup).build();
   }
   
   /**
    * Restores ChannelDisplaySettings from a PropertyMap
    * 
    * @param pMap PropertyMap from which to restore the ChannelDisplaySettings
    * @return ChannelDisplaySettings.  Missing values are replaced by defaults.
    */
   public static ChannelDisplaySettings fromPropertyMap (PropertyMap pMap) {
      ChannelDisplaySettings.Builder b = partialBuilderFromPropertyMap(pMap);

      if (pMap.containsString(PropertyKey.CHANNEL_NAME.key())) {
         b.name(pMap.getString(PropertyKey.CHANNEL_NAME.key(), ""));
      }
      if (pMap.containsString(PropertyKey.CHANNEL_GROUP.key())) {
         b.groupName(pMap.getString(PropertyKey.CHANNEL_GROUP.key(), ""));
      }
      return b.build();
   }
}