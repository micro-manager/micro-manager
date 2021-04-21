/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.profile.internal;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.micromanager.PropertyMap;
import org.micromanager.propertymap.MutablePropertyMapView;

public final class ProfilePropertyMapView implements MutablePropertyMapView {
  final DefaultUserProfile profile_;
  final Class<?> owner_;

  static ProfilePropertyMapView create(DefaultUserProfile profile, Class<?> owner) {
    return new ProfilePropertyMapView(profile, owner);
  }

  private ProfilePropertyMapView(DefaultUserProfile profile, Class<?> owner) {
    profile_ = profile;
    owner_ = owner;
  }

  private PropertyMap read() {
    return profile_.getProperties(owner_);
  }

  private void write(DefaultUserProfile.Editor editor) {
    profile_.editProperty(owner_, editor);
  }

  @Override
  public Set<String> keySet() {
    return new ProfileKeySetView(this);
  }

  @Override
  public boolean containsKey(String key) {
    return read().containsKey(key);
  }

  @Override
  public boolean containsAll(Collection<?> keys) {
    return read().containsAll(keys);
  }

  @Override
  public boolean isEmpty() {
    return read().isEmpty();
  }

  @Override
  public int size() {
    return read().size();
  }

  @Override
  public Class<?> getValueTypeForKey(String key) {
    return read().getValueTypeForKey(key);
  }

  @Override
  public PropertyMap toPropertyMap() {
    return read();
  }

  @Override
  public String getValueAsString(String key, String aDefault) {
    return read().getValueAsString(key, aDefault);
  }

  @Override
  public boolean containsBoolean(String key) {
    return read().containsBoolean(key);
  }

  @Override
  public boolean getBoolean(String key, boolean aDefault) {
    return read().getBoolean(key, aDefault);
  }

  @Override
  public boolean containsBooleanList(String key) {
    return read().containsBooleanList(key);
  }

  @Override
  public boolean[] getBooleanList(String key, boolean... defaults) {
    return read().getBooleanList(key, defaults);
  }

  @Override
  public List<Boolean> getBooleanList(String key, Iterable<Boolean> defaults) {
    return read().getBooleanList(key, defaults);
  }

  @Override
  public boolean containsByte(String key) {
    return read().containsByte(key);
  }

  @Override
  public byte getByte(String key, byte aDefault) {
    return read().getByte(key, aDefault);
  }

  @Override
  public boolean containsByteList(String key) {
    return read().containsByteList(key);
  }

  @Override
  public byte[] getByteList(String key, byte... defaults) {
    return read().getByteList(key, defaults);
  }

  @Override
  public List<Byte> getByteList(String key, Iterable<Byte> defaults) {
    return read().getByteList(key, defaults);
  }

  @Override
  public boolean containsShort(String key) {
    return read().containsShort(key);
  }

  @Override
  public short getShort(String key, short aDefault) {
    return read().getShort(key, aDefault);
  }

  @Override
  public boolean containsShortList(String key) {
    return read().containsShortList(key);
  }

  @Override
  public short[] getShortList(String key, short... defaults) {
    return read().getShortList(key, defaults);
  }

  @Override
  public List<Short> getShortList(String key, Iterable<Short> defaults) {
    return read().getShortList(key, defaults);
  }

  @Override
  public boolean containsInteger(String key) {
    return read().containsInteger(key);
  }

  @Override
  public int getInteger(String key, int aDefault) {
    return read().getInteger(key, aDefault);
  }

  @Override
  public boolean containsIntegerList(String key) {
    return read().containsIntegerList(key);
  }

  @Override
  public int[] getIntegerList(String key, int... defaults) {
    return read().getIntegerList(key, defaults);
  }

  @Override
  public List<Integer> getIntegerList(String key, Iterable<Integer> defaults) {
    return read().getIntegerList(key, defaults);
  }

  @Override
  public boolean containsLong(String key) {
    return read().containsLong(key);
  }

  @Override
  public long getLong(String key, long aDefault) {
    return read().getLong(key, aDefault);
  }

  @Override
  public boolean containsLongList(String key) {
    return read().containsLongList(key);
  }

  @Override
  public long[] getLongList(String key, long... defaults) {
    return read().getLongList(key, defaults);
  }

  @Override
  public List<Long> getLongList(String key, Iterable<Long> defaults) {
    return read().getLongList(key, defaults);
  }

  @Override
  public boolean containsFloat(String key) {
    return read().containsFloat(key);
  }

  @Override
  public float getFloat(String key, float aDefault) {
    return read().getFloat(key, aDefault);
  }

  @Override
  public boolean containsFloatList(String key) {
    return read().containsFloatList(key);
  }

  @Override
  public float[] getFloatList(String key, float... defaults) {
    return read().getFloatList(key, defaults);
  }

  @Override
  public List<Float> getFloatList(String key, Iterable<Float> defaults) {
    return read().getFloatList(key, defaults);
  }

  @Override
  public boolean containsDouble(String key) {
    return read().containsDouble(key);
  }

  @Override
  public double getDouble(String key, double aDefault) {
    return read().getDouble(key, aDefault);
  }

  @Override
  public boolean containsDoubleList(String key) {
    return read().containsDoubleList(key);
  }

  @Override
  public double[] getDoubleList(String key, double... defaults) {
    return read().getDoubleList(key, defaults);
  }

  @Override
  public List<Double> getDoubleList(String key, Iterable<Double> defaults) {
    return read().getDoubleList(key, defaults);
  }

  @Override
  public boolean containsNumber(String key) {
    return read().containsNumber(key);
  }

  @Override
  public Number getAsNumber(String key, Number aDefault) {
    return read().getAsNumber(key, aDefault);
  }

  @Override
  public boolean containsNumberList(String key) {
    return read().containsNumberList(key);
  }

  @Override
  public List<Number> getAsNumberList(String key, Number... defaults) {
    return read().getAsNumberList(key, defaults);
  }

  @Override
  public List<Number> getAsNumberList(String key, Iterable<Number> defaults) {
    return read().getAsNumberList(key, defaults);
  }

  @Override
  public boolean containsString(String key) {
    return read().containsString(key);
  }

  @Override
  public String getString(String key, String aDefault) {
    return read().getString(key, aDefault);
  }

  @Override
  public boolean containsStringList(String key) {
    return read().containsStringList(key);
  }

  @Override
  public List<String> getStringList(String key, String... defaults) {
    return read().getStringList(key, defaults);
  }

  @Override
  public List<String> getStringList(String key, Iterable<String> defaults) {
    return read().getStringList(key, defaults);
  }

  @Override
  public boolean containsUUID(String key) {
    return read().containsUUID(key);
  }

  @Override
  public UUID getUUID(String key, UUID aDefault) {
    return read().getUUID(key, aDefault);
  }

  @Override
  public boolean containsUUIDList(String key) {
    return read().containsUUIDList(key);
  }

  @Override
  public List<UUID> getUUIDList(String key, UUID... defaults) {
    return read().getUUIDList(key, defaults);
  }

  @Override
  public List<UUID> getUUIDList(String key, Iterable<UUID> defaults) {
    return read().getUUIDList(key, defaults);
  }

  @Override
  public boolean containsColor(String key) {
    return read().containsColor(key);
  }

  @Override
  public Color getColor(String key, Color aDefault) {
    return read().getColor(key, aDefault);
  }

  @Override
  public boolean containsColorList(String key) {
    return read().containsColorList(key);
  }

  @Override
  public List<Color> getColorList(String key, Color... defaults) {
    return read().getColorList(key, defaults);
  }

  @Override
  public List<Color> getColorList(String key, Iterable<Color> defaults) {
    return read().getColorList(key, defaults);
  }

  @Override
  public boolean containsAffineTransform(String key) {
    return read().containsAffineTransform(key);
  }

  @Override
  public AffineTransform getAffineTransform(String key, AffineTransform aDefault) {
    return read().getAffineTransform(key, aDefault);
  }

  @Override
  public boolean containsAffineTransformList(String key) {
    return read().containsAffineTransformList(key);
  }

  @Override
  public List<AffineTransform> getAffineTransformList(String key, AffineTransform... defaults) {
    return read().getAffineTransformList(key, defaults);
  }

  @Override
  public List<AffineTransform> getAffineTransformList(
      String key, Iterable<AffineTransform> defaults) {
    return read().getAffineTransformList(key, defaults);
  }

  @Override
  public boolean containsPropertyMap(String key) {
    return read().containsPropertyMap(key);
  }

  @Override
  public PropertyMap getPropertyMap(String key, PropertyMap aDefault) {
    return read().getPropertyMap(key, aDefault);
  }

  @Override
  public boolean containsPropertyMapList(String key) {
    return read().containsPropertyMapList(key);
  }

  @Override
  public List<PropertyMap> getPropertyMapList(String key, PropertyMap... defaults) {
    return read().getPropertyMapList(key, defaults);
  }

  @Override
  public List<PropertyMap> getPropertyMapList(String key, Iterable<PropertyMap> defaults) {
    return read().getPropertyMapList(key, defaults);
  }

  @Override
  public boolean containsRectangle(String key) {
    return read().containsRectangle(key);
  }

  @Override
  public Rectangle getRectangle(String key, Rectangle aDefault) {
    return read().getRectangle(key, aDefault);
  }

  @Override
  public boolean containsRectangleList(String key) {
    return read().containsRectangleList(key);
  }

  @Override
  public List<Rectangle> getRectangleList(String key, Rectangle... defaults) {
    return read().getRectangleList(key, defaults);
  }

  @Override
  public List<Rectangle> getRectangleList(String key, Iterable<Rectangle> defaults) {
    return read().getRectangleList(key, defaults);
  }

  @Override
  public boolean containsDimension(String key) {
    return read().containsDimension(key);
  }

  @Override
  public Dimension getDimension(String key, Dimension aDefault) {
    return read().getDimension(key, aDefault);
  }

  @Override
  public boolean containsDimensionList(String key) {
    return read().containsDimensionList(key);
  }

  @Override
  public List<Dimension> getDimensionList(String key, Dimension... defaults) {
    return read().getDimensionList(key, defaults);
  }

  @Override
  public List<Dimension> getDimensionList(String key, Iterable<Dimension> defaults) {
    return read().getDimensionList(key, defaults);
  }

  @Override
  public boolean containsPoint(String key) {
    return read().containsPoint(key);
  }

  @Override
  public Point getPoint(String key, Point aDefault) {
    return read().getPoint(key, aDefault);
  }

  @Override
  public boolean containsPointList(String key) {
    return read().containsPointList(key);
  }

  @Override
  public List<Point> getPointList(String key, Point... defaults) {
    return read().getPointList(key, defaults);
  }

  @Override
  public List<Point> getPointList(String key, Iterable<Point> defaults) {
    return read().getPointList(key, defaults);
  }

  @Override
  public <E extends Enum<E>> boolean containsStringForEnum(String key, Class<E> enumType) {
    return read().containsStringForEnum(key, enumType);
  }

  @Override
  public <E extends Enum<E>> E getStringAsEnum(String key, Class<E> enumType, E aDefault) {
    return read().getStringAsEnum(key, enumType, aDefault);
  }

  @Override
  public <E extends Enum<E>> boolean containsStringListForEnumList(String key, Class<E> enumType) {
    return read().containsStringListForEnumList(key, enumType);
  }

  @Override
  public <E extends Enum<E>> List<E> getStringListAsEnumList(
      String key, Class<E> enumType, E... defaults) {
    return read().getStringListAsEnumList(key, enumType, defaults);
  }

  @Override
  public <E extends Enum<E>> List<E> getStringListAsEnumList(
      String key, Class<E> enumType, Iterable<E> defaults) {
    return read().getStringListAsEnumList(key, enumType, defaults);
  }

  @Override
  public MutablePropertyMapView putAll(final PropertyMap pmap) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putAll(pmap).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView clear() {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().clear().build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView remove(final String key) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().remove(key).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView removeAll(final Collection<?> keys) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().removeAll(keys).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView retainAll(final Collection<?> keys) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().retainAll(keys).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putBoolean(final String key, final Boolean value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putBoolean(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putBooleanList(final String key, final boolean... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putBooleanList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putBooleanList(final String key, final Iterable<Boolean> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putBooleanList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putByte(final String key, final Byte value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putByte(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putByteList(final String key, final byte... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putByteList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putByteList(final String key, final Iterable<Byte> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putByteList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putShort(final String key, final Short value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putShort(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putShortList(final String key, final short... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putShortList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putShortList(final String key, final Iterable<Short> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putShortList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putInteger(final String key, final Integer value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putInteger(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putIntegerList(final String key, final int... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putIntegerList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putIntegerList(final String key, final Iterable<Integer> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putIntegerList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putLong(final String key, final Long value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putLong(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putLongList(final String key, final long... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putLongList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putLongList(final String key, final Iterable<Long> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putLongList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putFloat(final String key, final Float value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putFloat(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putFloatList(final String key, final float... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putFloatList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putFloatList(final String key, final Iterable<Float> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putFloatList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putDouble(final String key, final Double value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putDouble(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putDoubleList(final String key, final double... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putDoubleList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putDoubleList(final String key, final Iterable<Double> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putDoubleList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putString(final String key, final String value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putString(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putStringList(final String key, final String... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putStringList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putStringList(final String key, final Iterable<String> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putStringList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putColor(final String key, final Color value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putColor(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putColorList(final String key, final Color... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putColorList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putColorList(final String key, final Iterable<Color> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putColorList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putAffineTransform(final String key, final AffineTransform value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putAffineTransform(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putAffineTransformList(
      final String key, final AffineTransform... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putAffineTransformList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putAffineTransformList(
      final String key, final Iterable<AffineTransform> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putAffineTransformList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putPropertyMap(final String key, final PropertyMap value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putPropertyMap(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putPropertyMapList(final String key, final PropertyMap... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putPropertyMapList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putPropertyMapList(
      final String key, final Iterable<PropertyMap> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putPropertyMapList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putRectangle(final String key, final Rectangle value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putRectangle(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putRectangleList(final String key, final Rectangle... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putRectangleList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putRectangleList(
      final String key, final Iterable<Rectangle> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putRectangleList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putDimension(final String key, final Dimension value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putDimension(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putDimensionList(final String key, final Dimension... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putDimensionList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putDimensionList(
      final String key, final Iterable<Dimension> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putDimensionList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putPoint(final String key, final Point value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putPoint(key, value).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putPointList(final String key, final Point... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putPointList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public MutablePropertyMapView putPointList(final String key, final Iterable<Point> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putPointList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public <E extends Enum<E>> MutablePropertyMapView putEnumAsString(
      final String key, final E value) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putEnumAsString(key, value).build();
          }
        });
    return this;
  }

  @Override
  public <E extends Enum<E>> MutablePropertyMapView putEnumListAsStringList(
      final String key, final E... values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putEnumListAsStringList(key, values).build();
          }
        });
    return this;
  }

  @Override
  public <E extends Enum<E>> MutablePropertyMapView putEnumListAsStringList(
      final String key, final Iterable<E> values) {
    write(
        new DefaultUserProfile.Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putEnumListAsStringList(key, values).build();
          }
        });
    return this;
  }

  private static class ProfileKeySetView implements Set<String> {
    private final ProfilePropertyMapView map_;

    private ProfileKeySetView(ProfilePropertyMapView mapView) {
      map_ = mapView;
    }

    @Override
    public int size() {
      return map_.size();
    }

    @Override
    public boolean isEmpty() {
      return map_.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
      if (o instanceof String) {
        return map_.containsKey((String) o);
      }
      return false;
    }

    @Override
    public Iterator<String> iterator() {
      return map_.read().keySet().iterator();
    }

    @Override
    public Object[] toArray() {
      return map_.read().keySet().toArray();
    }

    @Override
    public <T> T[] toArray(T[] arg0) {
      return map_.read().keySet().toArray(arg0);
    }

    @Override
    public boolean add(String e) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
      if (o instanceof String) {
        boolean ret = contains(o);
        map_.remove((String) o);
        return ret;
      }
      return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      return map_.read().keySet().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends String> c) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(final Collection<?> c) {
      final boolean[] removed = new boolean[] {false};
      map_.write(
          new DefaultUserProfile.Editor() {
            @Override
            public PropertyMap edit(PropertyMap input) {
              int size = input.size();
              PropertyMap ret = input.copyBuilder().retainAll(c).build();
              removed[0] = ret.size() < size;
              return ret;
            }
          });
      return removed[0];
    }

    @Override
    public boolean removeAll(final Collection<?> c) {
      final boolean[] removed = new boolean[] {false};
      map_.write(
          new DefaultUserProfile.Editor() {
            @Override
            public PropertyMap edit(PropertyMap input) {
              int size = input.size();
              PropertyMap ret = input.copyBuilder().removeAll(c).build();
              removed[0] = ret.size() < size;
              return ret;
            }
          });
      return removed[0];
    }

    @Override
    public void clear() {
      map_.clear();
    }
  }
}
