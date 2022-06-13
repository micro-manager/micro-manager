/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.propertymap;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.micromanager.PropertyMap;

/**
 * An interface to a property-map-like object that can be modified.
 *
 * <p>TODO: This should have two superinterfaces, one shared with PropertyMap and
 * another shared with PropertyMap.Builder.</p>
 *
 * @author Mark A. Tsuchida
 */
public interface MutablePropertyMapView {
   /**
    * Return a set view of all of the keys in this view.
    *
    * <p>The returned view is updated when this MutablePropertyMapView changes.
    * Removing keys from the returned set view will remove those keys from the
    * MutablePropertyMapView. Adding keys is not supported.
    *
    * @return set view of property keys
    */
   Set<String> keySet();

   /**
    * Return whether a given key is present in this view.
    *
    * @param key a property key
    * @return true if {@code key} is present
    */
   boolean containsKey(String key);

   /**
    * Return whether all of the given keys are present in this view.
    *
    * @param keys a collection of property keys
    * @return true if all of {@code keys} are present
    */
   boolean containsAll(Collection<?> keys);

   /**
    * Return whether this view is empty.
    *
    * @return true if {@code size() == 0}; false otherwise
    */
   boolean isEmpty();

   /**
    * Return the number of keys in this view.
    *
    * @return The number of keys in this view.
    */
   int size();

   /**
    * Return a class indicating the type of the value for the given key.
    *
    * <p>Intended for use by special library/framework code.
    * See the analogous
    * {@link PropertyMap#getValueTypeForKey} for details.
    *
    * @param key a property key
    * @return the class representing the type of the value stored for
    *     {@code key}
    * @throws IllegalArgumentException if {@code key} is not found
    */
   Class<?> getValueTypeForKey(String key);

   /**
    * Return a property map copy of this view.
    *
    * @return the property map copy
    */
   PropertyMap toPropertyMap();

   String getValueAsString(String key, String aDefault);

   boolean containsBoolean(String key);

   boolean getBoolean(String key, boolean aDefault);

   boolean containsBooleanList(String key);

   boolean[] getBooleanList(String key, boolean... defaults);

   List<Boolean> getBooleanList(String key, Iterable<Boolean> defaults);

   boolean containsByte(String key);

   byte getByte(String key, byte aDefault);

   boolean containsByteList(String key);

   byte[] getByteList(String key, byte... defaults);

   List<Byte> getByteList(String key, Iterable<Byte> defaults);

   boolean containsShort(String key);

   short getShort(String key, short aDefault);

   boolean containsShortList(String key);

   short[] getShortList(String key, short... defaults);

   List<Short> getShortList(String key, Iterable<Short> defaults);

   boolean containsInteger(String key);

   int getInteger(String key, int aDefault);

   boolean containsIntegerList(String key);

   int[] getIntegerList(String key, int... defaults);

   List<Integer> getIntegerList(String key, Iterable<Integer> defaults);

   boolean containsLong(String key);

   long getLong(String key, long aDefault);

   boolean containsLongList(String key);

   long[] getLongList(String key, long... defaults);

   List<Long> getLongList(String key, Iterable<Long> defaults);

   boolean containsFloat(String key);

   float getFloat(String key, float aDefault);

   boolean containsFloatList(String key);

   float[] getFloatList(String key, float... defaults);

   List<Float> getFloatList(String key, Iterable<Float> defaults);

   boolean containsDouble(String key);

   double getDouble(String key, double aDefault);

   boolean containsDoubleList(String key);

   double[] getDoubleList(String key, double... defaults);

   List<Double> getDoubleList(String key, Iterable<Double> defaults);

   boolean containsNumber(String key);

   Number getAsNumber(String key, Number aDefault);

   boolean containsNumberList(String key);

   List<Number> getAsNumberList(String key, Number... defaults);

   List<Number> getAsNumberList(String key, Iterable<Number> defaults);

   boolean containsString(String key);

   String getString(String key, String aDefault);

   boolean containsStringList(String key);

   List<String> getStringList(String key, String... defaults);

   List<String> getStringList(String key, Iterable<String> defaults);

   boolean containsUUID(String key);

   UUID getUUID(String key, UUID aDefault);

   boolean containsUUIDList(String key);

   List<UUID> getUUIDList(String key, UUID... defaults);

   List<UUID> getUUIDList(String key, Iterable<UUID> defaults);

   boolean containsColor(String key);

   Color getColor(String key, Color aDefault);

   boolean containsColorList(String key);

   List<Color> getColorList(String key, Color... defaults);

   List<Color> getColorList(String key, Iterable<Color> defaults);

   boolean containsAffineTransform(String key);

   AffineTransform getAffineTransform(String key, AffineTransform aDefault);

   boolean containsAffineTransformList(String key);

   List<AffineTransform> getAffineTransformList(String key, AffineTransform... defaults);

   List<AffineTransform> getAffineTransformList(String key, Iterable<AffineTransform> defaults);

   boolean containsPropertyMap(String key);

   PropertyMap getPropertyMap(String key, PropertyMap aDefault);

   boolean containsPropertyMapList(String key);

   List<PropertyMap> getPropertyMapList(String key, PropertyMap... defaults);

   List<PropertyMap> getPropertyMapList(String key, Iterable<PropertyMap> defaults);

   boolean containsRectangle(String key);

   Rectangle getRectangle(String key, Rectangle aDefault);

   boolean containsRectangleList(String key);

   List<Rectangle> getRectangleList(String key, Rectangle... defaults);

   List<Rectangle> getRectangleList(String key, Iterable<Rectangle> defaults);

   boolean containsDimension(String key);

   Dimension getDimension(String key, Dimension aDefault);

   boolean containsDimensionList(String key);

   List<Dimension> getDimensionList(String key, Dimension... defaults);

   List<Dimension> getDimensionList(String key, Iterable<Dimension> defaults);

   boolean containsPoint(String key);

   Point getPoint(String key, Point aDefault);

   boolean containsPointList(String key);

   List<Point> getPointList(String key, Point... defaults);

   List<Point> getPointList(String key, Iterable<Point> defaults);

   <E extends Enum<E>> boolean containsStringForEnum(String key, Class<E> enumType);

   <E extends Enum<E>> E getStringAsEnum(String key, Class<E> enumType, E aDefault);

   <E extends Enum<E>> boolean containsStringListForEnumList(String key, Class<E> enumType);

   <E extends Enum<E>> List<E> getStringListAsEnumList(
         String key, Class<E> enumType, E... defaults);

   <E extends Enum<E>> List<E> getStringListAsEnumList(
         String key, Class<E> enumType, Iterable<E> defaults);

   MutablePropertyMapView putAll(PropertyMap pmap);

   MutablePropertyMapView clear();

   MutablePropertyMapView remove(String key);

   MutablePropertyMapView removeAll(Collection<?> keys);

   MutablePropertyMapView retainAll(Collection<?> keys);

   MutablePropertyMapView putBoolean(String key, Boolean value);

   MutablePropertyMapView putBooleanList(String key, boolean... values);

   MutablePropertyMapView putBooleanList(String key, Iterable<Boolean> values);

   MutablePropertyMapView putByte(String key, Byte value);

   MutablePropertyMapView putByteList(String key, byte... values);

   MutablePropertyMapView putByteList(String key, Iterable<Byte> values);

   MutablePropertyMapView putShort(String key, Short value);

   MutablePropertyMapView putShortList(String key, short... values);

   MutablePropertyMapView putShortList(String key, Iterable<Short> values);

   MutablePropertyMapView putInteger(String key, Integer value);

   MutablePropertyMapView putIntegerList(String key, int... values);

   MutablePropertyMapView putIntegerList(String key, Iterable<Integer> values);

   MutablePropertyMapView putLong(String key, Long value);

   MutablePropertyMapView putLongList(String key, long... values);

   MutablePropertyMapView putLongList(String key, Iterable<Long> values);

   MutablePropertyMapView putFloat(String key, Float value);

   MutablePropertyMapView putFloatList(String key, float... values);

   MutablePropertyMapView putFloatList(String key, Iterable<Float> values);

   MutablePropertyMapView putDouble(String key, Double value);

   MutablePropertyMapView putDoubleList(String key, double... values);

   MutablePropertyMapView putDoubleList(String key, Iterable<Double> values);

   MutablePropertyMapView putString(String key, String value);

   MutablePropertyMapView putStringList(String key, String... values);

   MutablePropertyMapView putStringList(String key, Iterable<String> values);

   MutablePropertyMapView putColor(String key, Color value);

   MutablePropertyMapView putColorList(String key, Color... values);

   MutablePropertyMapView putColorList(String key, Iterable<Color> values);

   MutablePropertyMapView putAffineTransform(String key, AffineTransform value);

   MutablePropertyMapView putAffineTransformList(String key, AffineTransform... values);

   MutablePropertyMapView putAffineTransformList(String key, Iterable<AffineTransform> values);

   MutablePropertyMapView putPropertyMap(String key, PropertyMap value);

   MutablePropertyMapView putPropertyMapList(String key, PropertyMap... values);

   MutablePropertyMapView putPropertyMapList(String key, Iterable<PropertyMap> values);

   MutablePropertyMapView putRectangle(String key, Rectangle value);

   MutablePropertyMapView putRectangleList(String key, Rectangle... values);

   MutablePropertyMapView putRectangleList(String key, Iterable<Rectangle> values);

   MutablePropertyMapView putDimension(String key, Dimension value);

   MutablePropertyMapView putDimensionList(String key, Dimension... values);

   MutablePropertyMapView putDimensionList(String key, Iterable<Dimension> values);

   MutablePropertyMapView putPoint(String key, Point value);

   MutablePropertyMapView putPointList(String key, Point... values);

   MutablePropertyMapView putPointList(String key, Iterable<Point> values);

   <E extends Enum<E>> MutablePropertyMapView putEnumAsString(String key, E value);

   <E extends Enum<E>> MutablePropertyMapView putEnumListAsStringList(String key, E... values);

   <E extends Enum<E>> MutablePropertyMapView putEnumListAsStringList(
         String key, Iterable<E> values);
}