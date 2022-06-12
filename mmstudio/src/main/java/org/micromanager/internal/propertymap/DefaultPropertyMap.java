package org.micromanager.internal.propertymap;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.ArrayUtils;
import org.micromanager.PropertyMap;
import org.micromanager.internal.utils.ChainedMapView;

/**
 * @author mark
 */
public final class DefaultPropertyMap implements PropertyMap {
   private final Map<String, Object> map_;

   private DefaultPropertyMap(PropertyMap.Builder builder) {
      if (!(builder instanceof Builder)) {
         throw new UnsupportedOperationException();
      }
      map_ = Collections.unmodifiableMap(
            new LinkedHashMap<>(((Builder) builder).map_));
   }

   private DefaultPropertyMap(Map<String, Object> map) {
      map_ = map;
   }

   // Not quite API quality yet, due to ChainedMapView being rough.
   public PropertyMap createChainedView(PropertyMap fallback) {
      Preconditions.checkNotNull(fallback);
      if (!(fallback instanceof DefaultPropertyMap)) {
         throw new UnsupportedOperationException();
      }
      Map<String, Object> chainedMap = ChainedMapView.create(map_,
            ((DefaultPropertyMap) fallback).map_);
      return new DefaultPropertyMap(chainedMap);
   }

   // Not in API!
   public static PropertyMap.Builder builder() {
      return new Builder();
   }

   @Override
   public boolean equals(Object other) {
      if (!(other instanceof PropertyMap)) {
         return false;
      }
      if (!(other instanceof DefaultPropertyMap)) {
         throw new UnsupportedOperationException();
      }

      // Since T[].equals() is the same as '==', Map.equals() is not what we
      // want.
      Map<String, Object> map2 = ((DefaultPropertyMap) other).map_;
      if (!map_.keySet().equals(map2.keySet())) {
         return false;
      }
      for (String key : map_.keySet()) {
         Object v1 = map_.get(key);
         Object v2 = map2.get(key);
         if (v1.equals(v2)) {
            continue;
         }
         Class<?> cls = v1.getClass();
         if (cls != v2.getClass() || !cls.isArray()) {
            return false;
         }
         Class<?> ctype = cls.getComponentType();
         if (!ctype.isPrimitive()) {
            if (Arrays.equals((Object[]) v1, (Object[]) v2)) {
               continue;
            }
            return false;
         }
         if (Primitive.valueOf(ctype).primitiveArrayEquals(v1, v2)) {
            continue;
         }
         return false;
      }
      return true;
   }

   @Override
   public int hashCode() {
      int hash = 5;
      hash = 67 * hash + (map_ != null ? map_.hashCode() : 0);
      return hash;
   }

   @Override
   public String toString() {
      return "pmap(" + map_.toString() + ")";
   }

   @Override
   public PropertyMap.Builder copyBuilder() {
      Builder b = new Builder();
      b.map_.putAll(map_);
      return b;
   }

   @Override
   public Set<String> keySet() {
      return Collections.unmodifiableSet(map_.keySet());
   }

   @Override
   public boolean containsKey(String key) {
      return map_.containsKey(key);
   }

   @Override
   public boolean containsAll(Collection<?> keys) {
      return keySet().containsAll(keys);
   }

   @Override
   public boolean isEmpty() {
      return map_.isEmpty();
   }

   @Override
   public int size() {
      return map_.size();
   }

   @Override
   public Class<?> getValueTypeForKey(String key) {
      Class<?> cls = map_.get(key).getClass();
      if (Primitive.VALUES.containsKey(cls)) {
         Primitive p = Primitive.valueOf(cls);
         if (cls == p.getBoxedClass()) {
            return p.getPrimitiveClass();
         }
      }
      return cls;
   }

   private static class OpaqueValue implements PropertyMap.OpaqueValue {
      private final Class<?> type_;
      private final Object value_;

      private OpaqueValue(Class<?> type, Object value) {
         type_ = type;
         value_ = value;
      }

      @Override
      public Class<?> getValueType() {
         return type_;
      }

      private Object getValue() {
         return value_;
      }
   }

   @Override
   public PropertyMap.OpaqueValue getAsOpaqueValue(String key) {
      Preconditions.checkArgument(map_.containsKey(key));
      return new OpaqueValue(getValueTypeForKey(key), map_.get(key));
   }

   @Override
   public String getValueAsString(String key, String aDefault) {
      if (map_.containsKey(key)) {

         if (containsDoubleList(key) || containsStringList(key)) {
            StringBuilder strb = new StringBuilder();

            if (containsDoubleList(key)) {
               double[] doubles = getDoubleList(key);
               for (double d : doubles) {
                  strb.append(d).append(", ");
               }
            }
            else if (containsStringList(key)) {
               List<String> strList = getStringList(key);
               for (String str : strList) {
                  strb.append(str).append(", ");
               }
            }
            String result = strb.toString();
            if (result.length() > 2) {
               result = result.substring(0, result.length() - 2);
            }
            return result;
         }

         // not a Double or String List
         return map_.get(key).toString();
      }
      return aDefault;
   }


   // One of those rare cases where long lines are more manageable...

   // Primitive type contains/get

   @Override
   public boolean containsBoolean(String key) {
      return containsPrimitiveScalar(key, Primitive.BOOLEAN);
   }

   @Override
   public boolean getBoolean(String key, boolean aDefault) {
      return getPrimitiveScalar(key, Primitive.BOOLEAN, aDefault);
   }

   @Override
   public boolean containsBooleanList(String key) {
      return containsPrimitiveArray(key, Primitive.BOOLEAN);
   }

   @Override
   public boolean[] getBooleanList(String key, boolean... defaults) {
      return getPrimitiveArray(key, Primitive.BOOLEAN, defaults);
   }

   @Override
   public List<Boolean> getBooleanList(String key, Iterable<Boolean> defaults) {
      return getBoxedList(key, Primitive.BOOLEAN, defaults);
   }

   @Override
   public boolean containsByte(String key) {
      return containsPrimitiveScalar(key, Primitive.BYTE);
   }

   @Override
   public byte getByte(String key, byte aDefault) {
      return getPrimitiveScalar(key, Primitive.BYTE, aDefault);
   }

   @Override
   public boolean containsByteList(String key) {
      return containsPrimitiveArray(key, Primitive.BYTE);
   }

   @Override
   public byte[] getByteList(String key, byte... defaults) {
      return getPrimitiveArray(key, Primitive.BYTE, defaults);
   }

   @Override
   public List<Byte> getByteList(String key, Iterable<Byte> defaults) {
      return getBoxedList(key, Primitive.BYTE, defaults);
   }

   @Override
   public boolean containsShort(String key) {
      return containsPrimitiveScalar(key, Primitive.SHORT);
   }

   @Override
   public short getShort(String key, short aDefault) {
      return getPrimitiveScalar(key, Primitive.SHORT, aDefault);
   }

   @Override
   public boolean containsShortList(String key) {
      return containsPrimitiveArray(key, Primitive.SHORT);
   }

   @Override
   public short[] getShortList(String key, short... defaults) {
      return getPrimitiveArray(key, Primitive.SHORT, defaults);
   }

   @Override
   public List<Short> getShortList(String key, Iterable<Short> defaults) {
      return getBoxedList(key, Primitive.SHORT, defaults);
   }

   @Override
   public boolean containsInteger(String key) {
      return containsPrimitiveScalar(key, Primitive.INT);
   }

   @Override
   public int getInteger(String key, int aDefault) {
      return getPrimitiveScalar(key, Primitive.INT, aDefault);
   }

   @Override
   public boolean containsIntegerList(String key) {
      return containsPrimitiveArray(key, Primitive.INT);
   }

   @Override
   public int[] getIntegerList(String key, int... defaults) {
      return getPrimitiveArray(key, Primitive.INT, defaults);
   }

   @Override
   public List<Integer> getIntegerList(String key, Iterable<Integer> defaults) {
      return getBoxedList(key, Primitive.INT, defaults);
   }

   @Override
   public boolean containsLong(String key) {
      return containsPrimitiveScalar(key, Primitive.LONG);
   }

   @Override
   public long getLong(String key, long aDefault) {
      return getPrimitiveScalar(key, Primitive.LONG, aDefault);
   }

   @Override
   public boolean containsLongList(String key) {
      return containsPrimitiveArray(key, Primitive.LONG);
   }

   @Override
   public long[] getLongList(String key, long... defaults) {
      return getPrimitiveArray(key, Primitive.LONG, defaults);
   }

   @Override
   public List<Long> getLongList(String key, Iterable<Long> defaults) {
      return getBoxedList(key, Primitive.LONG, defaults);
   }

   @Override
   public boolean containsFloat(String key) {
      return containsPrimitiveScalar(key, Primitive.FLOAT);
   }

   @Override
   public float getFloat(String key, float aDefault) {
      return getPrimitiveScalar(key, Primitive.FLOAT, aDefault);
   }

   @Override
   public boolean containsFloatList(String key) {
      return containsPrimitiveArray(key, Primitive.FLOAT);
   }

   @Override
   public float[] getFloatList(String key, float... defaults) {
      return getPrimitiveArray(key, Primitive.FLOAT, defaults);
   }

   @Override
   public List<Float> getFloatList(String key, Iterable<Float> defaults) {
      return getBoxedList(key, Primitive.FLOAT, defaults);
   }

   @Override
   public boolean containsDouble(String key) {
      return containsPrimitiveScalar(key, Primitive.DOUBLE);
   }

   @Override
   public double getDouble(String key, double aDefault) {
      return getPrimitiveScalar(key, Primitive.DOUBLE, aDefault);
   }

   @Override
   public boolean containsDoubleList(String key) {
      return containsPrimitiveArray(key, Primitive.DOUBLE);
   }

   @Override
   public double[] getDoubleList(String key, double... defaults) {
      return getPrimitiveArray(key, Primitive.DOUBLE, defaults);
   }

   @Override
   public List<Double> getDoubleList(String key, Iterable<Double> defaults) {
      return getBoxedList(key, Primitive.DOUBLE, defaults);
   }

   //
   // Non-type-specific numerical
   //

   private static final Set<? extends Class<?>> NUMERICAL_PRIMITIVES =
         ImmutableSet.of(byte.class, short.class, int.class, long.class,
               float.class, double.class);

   @Override
   public boolean containsNumber(String key) {
      return map_.containsKey(key) && NUMERICAL_PRIMITIVES.contains(
            map_.get(key).getClass());
   }

   @Override
   public Number getAsNumber(String key, Number aDefault) {
      return containsNumber(key) ? (Number) map_.get(key) : aDefault;
   }

   @Override
   public boolean containsNumberList(String key) {
      if (!map_.containsKey(key)) {
         return false;
      }
      Class<?> cls = map_.get(key).getClass();
      return cls.isArray() && NUMERICAL_PRIMITIVES.contains(cls.getComponentType());
   }

   @Override
   public List<Number> getAsNumberList(String key, Number... defaults) {
      return getAsNumberList(key, Arrays.asList(defaults));
   }

   @Override
   public List<Number> getAsNumberList(String key, Iterable<Number> defaults) {
      if (!containsNumberList(key)) {
         return Lists.newArrayList(defaults);
      }
      return (List) Lists.newArrayList(Primitive
            .valueOf(map_.get(key).getClass().getComponentType())
            .primitiveToBoxedArray(map_.get(key)));
   }

   //
   // Non-primitive contains/get: Immutable types
   //

   @Override
   public boolean containsString(String key) {
      return containsNonPrimitiveScalar(key, String.class);
   }

   @Override
   public String getString(String key, String aDefault) {
      return getNonPrimitiveScalar(key, String.class, aDefault);
   }

   @Override
   public boolean containsStringList(String key) {
      return containsNonPrimitiveArray(key, String.class);
   }

   @Override
   public List<String> getStringList(String key, String... defaults) {
      return getNonPrimitiveArray(key, String.class, defaults);
   }

   @Override
   public List<String> getStringList(String key, Iterable<String> defaults) {
      return getNonPrimitiveArray(key, String.class, defaults);
   }

   @Override
   public boolean containsUUID(String key) {
      return containsNonPrimitiveScalar(key, UUID.class);
   }

   @Override
   public UUID getUUID(String key, UUID aDefault) {
      return getNonPrimitiveScalar(key, UUID.class, aDefault);
   }

   @Override
   public boolean containsUUIDList(String key) {
      return containsNonPrimitiveArray(key, UUID.class);
   }

   @Override
   public List<UUID> getUUIDList(String key, UUID... defaults) {
      return getNonPrimitiveArray(key, UUID.class, defaults);
   }

   @Override
   public List<UUID> getUUIDList(String key, Iterable<UUID> defaults) {
      return getNonPrimitiveArray(key, UUID.class, defaults);
   }

   @Override
   public boolean containsColor(String key) {
      return containsNonPrimitiveScalar(key, Color.class);
   }

   @Override
   public Color getColor(String key, Color aDefault) {
      return getNonPrimitiveScalar(key, Color.class, aDefault);
   }

   @Override
   public boolean containsColorList(String key) {
      return containsNonPrimitiveArray(key, Color.class);
   }

   @Override
   public List<Color> getColorList(String key, Color... defaults) {
      return getNonPrimitiveArray(key, Color.class, defaults);
   }

   @Override
   public List<Color> getColorList(String key, Iterable<Color> defaults) {
      return getNonPrimitiveArray(key, Color.class, defaults);
   }

   @Override
   public boolean containsAffineTransform(String key) {
      return containsNonPrimitiveScalar(key, AffineTransform.class);
   }

   @Override
   public AffineTransform getAffineTransform(String key, AffineTransform aDefault) {
      return getNonPrimitiveScalar(key, AffineTransform.class, aDefault);
   }

   @Override
   public boolean containsAffineTransformList(String key) {
      return containsNonPrimitiveArray(key, AffineTransform.class);
   }

   @Override
   public List<AffineTransform> getAffineTransformList(String key,
                                                       AffineTransform... defaults) {
      return getNonPrimitiveArray(key, AffineTransform.class, defaults);
   }

   @Override
   public List<AffineTransform> getAffineTransformList(String key,
                                                       Iterable<AffineTransform> defaults) {
      return getNonPrimitiveArray(key, AffineTransform.class, defaults);
   }

   @Override
   public boolean containsPropertyMap(String key) {
      return containsNonPrimitiveScalar(key, PropertyMap.class);
   }

   @Override
   public PropertyMap getPropertyMap(String key, PropertyMap aDefault) {
      return getNonPrimitiveScalar(key, PropertyMap.class, aDefault);
   }

   @Override
   public boolean containsPropertyMapList(String key) {
      return containsNonPrimitiveArray(key, PropertyMap.class);
   }

   @Override
   public List<PropertyMap> getPropertyMapList(String key, PropertyMap... defaults) {
      return getNonPrimitiveArray(key, PropertyMap.class, defaults);
   }

   @Override
   public List<PropertyMap> getPropertyMapList(String key,
                                               Iterable<PropertyMap> defaults) {
      return getNonPrimitiveArray(key, PropertyMap.class, defaults);
   }

   // To add more, copy the 5 UUID methods and replace 'UUID' with type name.

   //
   // Non-primitive contains/get: Mutable types (copy before returning!)
   //

   private static final Cloner<Rectangle> CLONE_RECTANGLE = new Cloner<Rectangle>() {
      @Override
      public Rectangle clone(Rectangle value) {
         return value == null ? null : (Rectangle) value.clone();
      }
   };

   @Override
   public boolean containsRectangle(String key) {
      return containsNonPrimitiveScalar(key, Rectangle.class);
   }

   @Override
   public Rectangle getRectangle(String key, Rectangle aDefault) {
      return getClonedNonPrimitiveScalar(key, Rectangle.class, CLONE_RECTANGLE, aDefault);
   }

   @Override
   public boolean containsRectangleList(String key) {
      return containsNonPrimitiveArray(key, Rectangle.class);
   }

   @Override
   public List<Rectangle> getRectangleList(String key, Rectangle... defaults) {
      return getClonedNonPrimitiveArray(key, Rectangle.class, CLONE_RECTANGLE, defaults);
   }

   @Override
   public List<Rectangle> getRectangleList(String key, Iterable<Rectangle> defaults) {
      return getClonedNonPrimitiveArray(key, Rectangle.class, CLONE_RECTANGLE, defaults);
   }

   private static final Cloner<Dimension> CLONE_DIMENSION = new Cloner<Dimension>() {
      @Override
      public Dimension clone(Dimension value) {
         return value == null ? null : (Dimension) value.clone();
      }
   };

   @Override
   public boolean containsDimension(String key) {
      return containsNonPrimitiveScalar(key, Dimension.class);
   }

   @Override
   public Dimension getDimension(String key, Dimension aDefault) {
      return getClonedNonPrimitiveScalar(key, Dimension.class, CLONE_DIMENSION, aDefault);
   }

   @Override
   public boolean containsDimensionList(String key) {
      return containsNonPrimitiveArray(key, Dimension.class);
   }

   @Override
   public List<Dimension> getDimensionList(String key, Dimension... defaults) {
      return getClonedNonPrimitiveArray(key, Dimension.class, CLONE_DIMENSION, defaults);
   }

   @Override
   public List<Dimension> getDimensionList(String key, Iterable<Dimension> defaults) {
      return getClonedNonPrimitiveArray(key, Dimension.class, CLONE_DIMENSION, defaults);
   }

   private static final Cloner<Point> CLONE_POINT = new Cloner<Point>() {
      @Override
      public Point clone(Point value) {
         return value == null ? null : (Point) value.clone();
      }
   };

   @Override
   public boolean containsPoint(String key) {
      return containsNonPrimitiveScalar(key, Point.class);
   }

   @Override
   public Point getPoint(String key, Point aDefault) {
      return getClonedNonPrimitiveScalar(key, Point.class, CLONE_POINT, aDefault);
   }

   @Override
   public boolean containsPointList(String key) {
      return containsNonPrimitiveArray(key, Point.class);
   }

   @Override
   public List<Point> getPointList(String key, Point... defaults) {
      return getClonedNonPrimitiveArray(key, Point.class, CLONE_POINT, defaults);
   }

   @Override
   public List<Point> getPointList(String key, Iterable<Point> defaults) {
      return getClonedNonPrimitiveArray(key, Point.class, CLONE_POINT, defaults);
   }

   // To add more, copy the Rectnagle methods and replace 'Rectangle' with the
   // type name; then edit the clone method if the value type requires a
   // different method to duplicate.


   //
   // Enum-as-string contains/get
   //

   @Override
   public <E extends Enum<E>> boolean containsStringForEnum(String key, Class<E> enumType) {
      try {
         return containsString(key) && Enum.valueOf(enumType, getString(key, null)) != null;
      } catch (IllegalArgumentException e) {
         return false;
      }
   }

   @Override
   public <E extends Enum<E>> boolean containsStringListForEnumList(String key, Class<E> enumType) {
      if (!containsStringList(key)) {
         return false;
      }
      try {
         for (String s : getStringList(key)) {
            Enum.valueOf(enumType, s);
         }
         return true;
      } catch (IllegalArgumentException e) {
         return false;
      }
   }

   @Override
   public <E extends Enum<E>> E getStringAsEnum(String key, Class<E> enumType, E defaultValue) {
      try {
         return Enum.valueOf(enumType, getString(key, null));
      } catch (NullPointerException keyMissing) {
         return defaultValue;
      } catch (IllegalArgumentException notAValidEnumValue) {
         return defaultValue;
      }
   }

   @Override
   public <E extends Enum<E>> List<E> getStringListAsEnumList(String key,
                                                              Class<E> enumType,
                                                              E... defaultValues) {
      return getStringListAsEnumList(key, enumType, Lists.newArrayList(defaultValues));
   }

   @Override
   public <E extends Enum<E>> List<E> getStringListAsEnumList(String key,
                                                              Class<E> enumType,
                                                              Iterable<E> defaultValues) {
      List<E> ret = new ArrayList<E>();
      try {
         for (String s : getStringList(key, (Iterable<String>) null)) {
            ret.add(Enum.valueOf(enumType, s));
         }
         return ret;
      } catch (NullPointerException keyMissing) {
         return Lists.newArrayList(defaultValues);
      } catch (IllegalArgumentException notAValidEnumValue) {
         return Lists.newArrayList(defaultValues);
      }
   }


   //
   // Generic Implementations for contains/get
   //

   // Somebody has to define the mapping between primitve and boxed types...
   private static enum Primitive {
      BOOLEAN(boolean.class, Boolean.class, boolean[].class) {
         @Override
         Boolean[] primitiveToBoxedArray(Object a) {
            return ArrayUtils.toObject((boolean[]) a);
         }

         @Override
         Object clonePrimitiveArray(Object a) {
            return ((boolean[]) a).clone();
         }

         @Override
         boolean primitiveArrayEquals(Object a, Object b) {
            return Arrays.equals((boolean[]) a, (boolean[]) b);
         }

         // Work around a commons.lang3 bug (toPrimitive(Object) returns Boolean[],
         // not boolean[], for Boolean[])
         @Override
         Object boxedToPrimitiveArray(Object a) {
            return ArrayUtils.toPrimitive((Boolean[]) a);
         }
      },
      BYTE(byte.class, Byte.class, byte[].class) {
         @Override
         Byte[] primitiveToBoxedArray(Object a) {
            return ArrayUtils.toObject((byte[]) a);
         }

         @Override
         Object clonePrimitiveArray(Object a) {
            return ((byte[]) a).clone();
         }

         @Override
         boolean primitiveArrayEquals(Object a, Object b) {
            return Arrays.equals((byte[]) a, (byte[]) b);
         }

         // Work around a commons.lang3 bug (toPrimitive(Object) returns Byte[],
         // not byte[], for Byte[])
         @Override
         Object boxedToPrimitiveArray(Object a) {
            return ArrayUtils.toPrimitive((Byte[]) a);
         }
      },
      SHORT(short.class, Short.class, short[].class) {
         @Override
         Short[] primitiveToBoxedArray(Object a) {
            return ArrayUtils.toObject((short[]) a);
         }

         @Override
         Object clonePrimitiveArray(Object a) {
            return ((short[]) a).clone();
         }

         @Override
         boolean primitiveArrayEquals(Object a, Object b) {
            return Arrays.equals((short[]) a, (short[]) b);
         }
      },
      INT(int.class, Integer.class, int[].class) {
         @Override
         Integer[] primitiveToBoxedArray(Object a) {
            return ArrayUtils.toObject((int[]) a);
         }

         @Override
         Object clonePrimitiveArray(Object a) {
            return ((int[]) a).clone();
         }

         @Override
         boolean primitiveArrayEquals(Object a, Object b) {
            return Arrays.equals((int[]) a, (int[]) b);
         }
      },
      LONG(long.class, Long.class, long[].class) {
         @Override
         Long[] primitiveToBoxedArray(Object a) {
            return ArrayUtils.toObject((long[]) a);
         }

         @Override
         Object clonePrimitiveArray(Object a) {
            return ((long[]) a).clone();
         }

         @Override
         boolean primitiveArrayEquals(Object a, Object b) {
            return Arrays.equals((long[]) a, (long[]) b);
         }
      },
      FLOAT(float.class, Float.class, float[].class) {
         @Override
         Float[] primitiveToBoxedArray(Object a) {
            return ArrayUtils.toObject((float[]) a);
         }

         @Override
         Object clonePrimitiveArray(Object a) {
            return ((float[]) a).clone();
         }

         @Override
         boolean primitiveArrayEquals(Object a, Object b) {
            return Arrays.equals((float[]) a, (float[]) b);
         }
      },
      DOUBLE(double.class, Double.class, double[].class) {
         @Override
         Double[] primitiveToBoxedArray(Object a) {
            return ArrayUtils.toObject((double[]) a);
         }

         @Override
         Object clonePrimitiveArray(Object a) {
            return ((double[]) a).clone();
         }

         @Override
         boolean primitiveArrayEquals(Object a, Object b) {
            return Arrays.equals((double[]) a, (double[]) b);
         }
      },
      ;

      private final Class<?> primitive_;
      private final Class<?> boxed_;
      private final Class<?> array_;

      private Primitive(Class<?> primitive, Class<?> boxed, Class<?> array) {
         primitive_ = primitive;
         boxed_ = boxed;
         array_ = array;
      }

      Class<?> getPrimitiveClass() {
         return primitive_;
      }

      Class<?> getBoxedClass() {
         return boxed_;
      }

      Class<?> getPrimitiveArrayClass() {
         return array_;
      }

      abstract Object clonePrimitiveArray(Object a);

      abstract boolean primitiveArrayEquals(Object a, Object b);

      abstract Object primitiveToBoxedArray(Object a);

      Object boxedToPrimitiveArray(Object a) {
         return ArrayUtils.toPrimitive(a);
      }

      static Primitive valueOf(Class<?> type) {
         return VALUES.get(type);
      }

      private static final Map<Class<?>, Primitive> VALUES = new HashMap<>();

      static {
         for (Primitive v : values()) {
            VALUES.put(v.getBoxedClass(), v);
            VALUES.put(v.getPrimitiveClass(), v);
            VALUES.put(v.getPrimitiveArrayClass(), v);
         }
      }
   }

   private boolean containsPrimitiveScalar(String key, Primitive p) {
      Preconditions.checkNotNull(key);
      return map_.containsKey(key) && p.getBoxedClass().isInstance(map_.get(key));
   }

   private boolean containsPrimitiveArray(String key, Primitive p) {
      Preconditions.checkNotNull(key);
      return map_.containsKey(key) && p.getPrimitiveArrayClass().isInstance(map_.get(key));
   }

   private <B> B getPrimitiveScalar(String key, Primitive p, B aDefault) {
      Preconditions.checkNotNull(key);
      Class<B> boxedClass = (Class<B>) p.getBoxedClass();
      return getRaw(key, boxedClass, aDefault);
   }

   private <PA> PA getPrimitiveArray(String key, Primitive p, PA defaults) {
      Preconditions.checkNotNull(key);
      Class<PA> arrayClass = (Class<PA>) p.getPrimitiveArrayClass();
      return (PA) p.clonePrimitiveArray(getRaw(key, arrayClass, defaults));
   }

   private <B> List<B> getBoxedList(String key, Primitive p, Iterable<B> defaults) {
      Preconditions.checkNotNull(key);
      if (missingOrWrongType(key, p.getPrimitiveArrayClass())) {
         return defaults == null ? null : Lists.newArrayList(defaults);
      }
      Object primitiveArray = getRaw(key, p.getPrimitiveArrayClass(), null);
      B[] boxedArray = (B[]) p.primitiveToBoxedArray(primitiveArray);
      return (List<B>) Lists.newArrayList(boxedArray);
   }

   private boolean containsNonPrimitiveScalar(String key, Class<?> scalarClass) {
      Preconditions.checkNotNull(key);
      return map_.containsKey(key) && scalarClass.isInstance(map_.get(key));
   }

   private boolean containsNonPrimitiveArray(String key, Class<?> elementClass) {
      Preconditions.checkNotNull(key);
      Class<?> arrayClass = arrayClassForElementClass(elementClass);
      return map_.containsKey(key) && arrayClass.isInstance(map_.get(key));
   }

   private <T> T getNonPrimitiveScalar(String key, Class<T> scalarClass, T aDefault) {
      Preconditions.checkNotNull(key);
      return getRaw(key, scalarClass, aDefault);
   }

   private <T> T getClonedNonPrimitiveScalar(String key, Class<T> scalarClass,
                                             Cloner<T> cloner, T aDefault) {
      Preconditions.checkNotNull(key);
      return cloner.clone(getNonPrimitiveScalar(key, scalarClass, aDefault));
   }

   private <T> List<T> getNonPrimitiveArray(String key, Class<T> elementClass, T... defaults) {
      Preconditions.checkNotNull(key);
      return getNonPrimitiveArray(key, elementClass,
            defaults == null ? null : Lists.newArrayList(defaults));
   }

   private <T> List<T> getNonPrimitiveArray(String key, Class<T> elementClass,
                                            Iterable<T> defaults) {
      Preconditions.checkNotNull(key);
      Class<T[]> arrayClass = arrayClassForElementClass(elementClass);
      if (missingOrWrongType(key, arrayClass)) {
         return defaults == null ? null : Lists.newArrayList(defaults);
      }
      return Lists.newArrayList((T[]) map_.get(key));
   }

   private <T> List<T> getClonedNonPrimitiveArray(String key, Class<T> elementClass,
                                                  Cloner<T> cloner, T... defaults) {
      Preconditions.checkNotNull(key);
      List<T> values = getNonPrimitiveArray(key, elementClass, defaults);
      for (int i = 0; i < values.size(); ++i) {
         values.set(i, cloner.clone(values.get(i)));
      }
      return values;
   }

   private <T> List<T> getClonedNonPrimitiveArray(String key, Class<T> elementClass,
                                                  Cloner<T> cloner, Iterable<T> defaults) {
      Preconditions.checkNotNull(key);
      List<T> values = getNonPrimitiveArray(key, elementClass, defaults);
      for (int i = 0; i < values.size(); ++i) {
         values.set(i, cloner.clone(values.get(i)));
      }
      return values;
   }


   //
   // Helpers used by the generic contains/get methods
   //

   private static interface Cloner<T> {
      T clone(T value);
   }

   private <T> Class<T[]> arrayClassForElementClass(Class<T> elementClass) {
      return (Class<T[]>) Array.newInstance(elementClass, 0).getClass();
   }

   private <T> T getRaw(String key, Class<T> cls, T aDefault) {
      if (missingOrWrongType(key, cls)) {
         return aDefault;
      }
      return (T) map_.get(key);
   }

   private boolean missingOrWrongType(String key, Class<?> cls) {
      if (!map_.containsKey(key)) {
         return true;
      }
      if (!cls.isInstance(map_.get(key))) {
         // We throw ClassCastException (but a subclass thereof, for backward compat)
         throw new TypeMismatchException(String.format(
               "Type mismatch on property map access for key: " + key
                     + " (requested: %s; found: %s)",
               cls.getName(), map_.get(key).getClass().getName()));
      }
      return false;
   }


   //
   // Serialization API
   //

   @Override
   public String toJSON() {
      return PropertyMapJSONSerializer.toJSON(this);
   }

   @Override
   public boolean saveJSON(File file, boolean overwrite, boolean createBackup) throws IOException {
      // Write (almost) atomically by writing to a temporary file and them
      // moving to requested location.
      File tempDir = Files.createTempDir();
      File tempFile = new File(tempDir, "property_map.json");
      try {
         Files.write(toJSON(), tempFile, Charsets.UTF_8);
         if (file.exists()) {
            if (!overwrite) {
               return false;
            }
            if (createBackup) {
               File backup = new File(file.getParentFile(), file.getName() + "~");
               backup.delete();
               if (!file.renameTo(backup)) {
                  throw new IOException("Failed to create backup file: "
                        + backup.getPath());
               }
            }
         }
         try {
            Files.move(tempFile, file);
         } catch (FileNotFoundException fne) {
            return false;
         }
         return true;
      } finally {
         tempFile.delete();
         tempDir.delete();
      }
   }


   public static class Builder extends LegacyBuilder implements PropertyMap.Builder {
      // Map keys and values are never null. Collections are stored as arrays,
      // so that the type is preserved even if empty. Arrays of primitive types
      // are used when aplicable rather than arrays of boxed types.
      Map<String, Object> map_ = new LinkedHashMap<>();

      //
      // Primitives
      //

      @Override
      public Builder putBoolean(String key, Boolean value) {
         return putScalar(key, value);
      }

      @Override
      public Builder putBooleanList(String key, boolean... values) {
         return putPrimitiveArray(key, Primitive.BOOLEAN, values);
      }

      @Override
      public Builder putBooleanList(String key, Iterable<Boolean> values) {
         return putBoxedList(key, Primitive.BOOLEAN, values);
      }

      @Override
      public Builder putByte(String key, Byte value) {
         return putScalar(key, value);
      }

      @Override
      public Builder putByteList(String key, byte... values) {
         return putPrimitiveArray(key, Primitive.BYTE, values);
      }

      @Override
      public Builder putByteList(String key, Iterable<Byte> values) {
         return putBoxedList(key, Primitive.BYTE, values);
      }

      @Override
      public Builder putShort(String key, Short value) {
         return putScalar(key, value);
      }

      @Override
      public Builder putShortList(String key, short... values) {
         return putPrimitiveArray(key, Primitive.SHORT, values);
      }

      @Override
      public Builder putShortList(String key, Iterable<Short> values) {
         return putBoxedList(key, Primitive.SHORT, values);
      }

      @Override
      public Builder putInteger(String key, Integer value) {
         return putScalar(key, value);
      }

      @Override
      public Builder putIntegerList(String key, int... values) {
         return putPrimitiveArray(key, Primitive.INT, values);
      }

      @Override
      public Builder putIntegerList(String key, Iterable<Integer> values) {
         return putBoxedList(key, Primitive.INT, values);
      }

      @Override
      public Builder putLong(String key, Long value) {
         return putScalar(key, value);
      }

      @Override
      public Builder putLongList(String key, long... values) {
         return putPrimitiveArray(key, Primitive.LONG, values);
      }

      @Override
      public Builder putLongList(String key, Iterable<Long> values) {
         return putBoxedList(key, Primitive.LONG, values);
      }

      @Override
      public Builder putFloat(String key, Float value) {
         return putScalar(key, value);
      }

      @Override
      public Builder putFloatList(String key, float... values) {
         return putPrimitiveArray(key, Primitive.FLOAT, values);
      }

      @Override
      public Builder putFloatList(String key, Iterable<Float> values) {
         return putBoxedList(key, Primitive.FLOAT, values);
      }

      @Override
      public Builder putDouble(String key, Double value) {
         return putScalar(key, value);
      }

      @Override
      public Builder putDoubleList(String key, double... values) {
         return putPrimitiveArray(key, Primitive.DOUBLE, values);
      }

      @Override
      public Builder putDoubleList(String key, Iterable<Double> values) {
         return putBoxedList(key, Primitive.DOUBLE, values);
      }

      //
      // Immutable non-primitives
      //

      @Override
      public Builder putString(String key, String value) {
         return putScalar(key, value);
      }

      @Override
      public Builder putStringList(String key, String... values) {
         return putNonPrimitiveArray(key, String.class, values);
      }

      @Override
      public Builder putStringList(String key, Iterable<String> values) {
         return putNonPrimitiveArray(key, String.class, values);
      }

      @Override
      public Builder putUUID(String key, UUID value) {
         return putScalar(key, value);
      }

      @Override
      public Builder putUUIDList(String key, UUID... values) {
         return putNonPrimitiveArray(key, UUID.class, values);
      }

      @Override
      public Builder putUUIDList(String key, Iterable<UUID> values) {
         return putNonPrimitiveArray(key, UUID.class, values);
      }

      @Override
      public Builder putColor(String key, Color value) {
         return putScalar(key, value);
      }

      @Override
      public Builder putColorList(String key, Color... values) {
         return putNonPrimitiveArray(key, Color.class, values);
      }

      @Override
      public Builder putColorList(String key, Iterable<Color> values) {
         return putNonPrimitiveArray(key, Color.class, values);
      }

      @Override
      public Builder putAffineTransform(String key, AffineTransform value) {
         return putScalar(key, value);
      }

      @Override
      public Builder putAffineTransformList(String key, AffineTransform... values) {
         return putNonPrimitiveArray(key, AffineTransform.class, values);
      }

      @Override
      public Builder putAffineTransformList(String key,
                                            Iterable<AffineTransform> values) {
         return putNonPrimitiveArray(key, AffineTransform.class, values);
      }

      @Override
      public Builder putPropertyMap(String key, PropertyMap value) {
         return putScalar(key, value);
      }

      @Override
      public Builder putPropertyMapList(String key, PropertyMap... values) {
         return putNonPrimitiveArray(key, PropertyMap.class, values);
      }

      @Override
      public Builder putPropertyMapList(String key, Iterable<PropertyMap> values) {
         return putNonPrimitiveArray(key, PropertyMap.class, values);
      }

      //
      // Mutable non-primitives (clone before adding!)
      //

      @Override
      public Builder putRectangle(String key, Rectangle value) {
         return putClonedScalar(key, CLONE_RECTANGLE, value);
      }

      @Override
      public Builder putRectangleList(String key, Rectangle... values) {
         return putClonedNonPrimitiveArray(key, Rectangle.class, CLONE_RECTANGLE, values);
      }

      @Override
      public Builder putRectangleList(String key, Iterable<Rectangle> values) {
         return putClonedNonPrimitiveArray(key, Rectangle.class, CLONE_RECTANGLE, values);
      }

      @Override
      public Builder putDimension(String key, Dimension value) {
         return putClonedScalar(key, CLONE_DIMENSION, value);
      }

      @Override
      public Builder putDimensionList(String key, Dimension... values) {
         return putClonedNonPrimitiveArray(key, Dimension.class, CLONE_DIMENSION, values);
      }

      @Override
      public Builder putDimensionList(String key, Iterable<Dimension> values) {
         return putClonedNonPrimitiveArray(key, Dimension.class, CLONE_DIMENSION, values);
      }

      @Override
      public Builder putPoint(String key, Point value) {
         return putClonedScalar(key, CLONE_POINT, value);
      }

      @Override
      public Builder putPointList(String key, Point... values) {
         return putClonedNonPrimitiveArray(key, Point.class, CLONE_POINT, values);
      }

      @Override
      public Builder putPointList(String key, Iterable<Point> values) {
         return putClonedNonPrimitiveArray(key, Point.class, CLONE_POINT, values);
      }

      //
      // Enum-as-string
      //

      @Override
      public <E extends Enum<E>> Builder putEnumAsString(String key, E value) {
         return putString(key, value.name());
      }

      @Override
      public <E extends Enum<E>> Builder putEnumListAsStringList(String key, E... values) {
         if (checkArgsAndRemoveKeyIfValueIsNull(key, values)) {
            return this;
         }
         List<String> ss = new ArrayList<>();
         for (E value : values) {
            ss.add(value.name());
         }
         return putStringList(key, ss);
      }

      @Override
      public <E extends Enum<E>> Builder putEnumListAsStringList(String key, Iterable<E> values) {
         if (checkArgsAndRemoveKeyIfValueIsNull(key, values)) {
            return this;
         }
         List<String> ss = new ArrayList<>();
         for (E value : values) {
            ss.add(value.name());
         }
         return putStringList(key, ss);
      }


      //
      // Generic put implementation
      //

      private <T> Builder putScalar(String key, T value) {
         if (checkArgsAndRemoveKeyIfValueIsNull(key, value)) {
            return this;
         }
         map_.put(key, value);
         return this;
      }

      private <T> Builder putClonedScalar(String key, Cloner<T> cloner, T value) {
         return putScalar(key, value == null ? null : cloner.clone(value));
      }

      private <T> Builder putNonPrimitiveArray(String key, Class<T> elementClass, T... values) {
         putNonPrimitiveArrayImpl(key, elementClass,
               values == null ? null : Lists.newArrayList(values), null);
         return this;
      }

      private <T> Builder putNonPrimitiveArray(String key, Class<T> elementClass,
                                               Iterable<T> values) {
         putNonPrimitiveArrayImpl(key, elementClass, values, null);
         return this;
      }

      private <T> Builder putClonedNonPrimitiveArray(String key, Class<T> elementClass,
                                                     Cloner<T> cloner, T... values) {
         putNonPrimitiveArrayImpl(key, elementClass,
               values == null ? null : Lists.newArrayList(values), cloner);
         return this;
      }

      private <T> Builder putClonedNonPrimitiveArray(String key, Class<T> elementClass,
                                                     Cloner<T> cloner, Iterable<T> values) {
         putNonPrimitiveArrayImpl(key, elementClass, values, cloner);
         return this;
      }

      private <T> void putNonPrimitiveArrayImpl(String key, Class<T> elementClass,
                                                Iterable<T> values, Cloner<T> cloner) {
         if (checkArgsAndRemoveKeyIfValueIsNull(key, values)) {
            return;
         }
         List<T> valueList = new ArrayList<T>();
         if (cloner != null) {
            for (T value : values) {
               Preconditions.checkNotNull(value, "Null not allowed in property map values");
               valueList.add(cloner.clone(value));
            }
         }
         else {
            for (T value : values) {
               Preconditions.checkNotNull(value, "Null not allowed in property map values");
               valueList.add(value);
            }
         }

         T[] array = valueList.toArray(
               (T[]) Array.newInstance(elementClass, valueList.size()));
         map_.put(key, array);
      }

      private Builder putPrimitiveArray(String key, Primitive p, Object values) {
         if (checkArgsAndRemoveKeyIfValueIsNull(key, values)) {
            return this;
         }
         map_.put(key, p.clonePrimitiveArray(values));
         return this;
      }

      private <B> Builder putBoxedList(String key, Primitive p, Iterable<B> values) {
         if (checkArgsAndRemoveKeyIfValueIsNull(key, values)) {
            return this;
         }
         List<B> valueList = Lists.newArrayList(values);
         for (B value : valueList) {
            Preconditions.checkNotNull(value, "Null not allowed in property map values");
         }
         B[] boxedArray = valueList.toArray(
               (B[]) Array.newInstance(p.getBoxedClass(), valueList.size()));
         map_.put(key, p.boxedToPrimitiveArray(boxedArray));
         return this;

      }

      private <T> boolean checkArgsAndRemoveKeyIfValueIsNull(String key, T value) {
         Preconditions.checkNotNull(key);
         if (value == null) {
            map_.remove(key);
            return true;
         }
         return false;
      }


      //
      //
      //

      @Override
      public Builder putOpaqueValue(String key, PropertyMap.OpaqueValue value) {
         OpaqueValue v;
         try {
            v = (OpaqueValue) value;
         } catch (ClassCastException e) {
            throw new UnsupportedOperationException("Unsupported opaque value implementation", e);
         }

         map_.put(key, v.getValue());
         return this;
      }

      @Override
      public Builder putAll(PropertyMap map) {
         // In theory we could come up with a way to copy any PropertyMap. In
         // practice that's not expected to be necessary.
         if (!(map instanceof DefaultPropertyMap)) {
            throw new UnsupportedOperationException();
         }
         map_.putAll(((DefaultPropertyMap) map).map_);
         return this;
      }

      @Override
      public Builder clear() {
         map_.clear();
         return this;
      }

      @Override
      public Builder remove(String key) {
         map_.remove(key);
         return this;
      }

      @Override
      public Builder removeAll(Collection<?> keys) {
         map_.keySet().removeAll(keys);
         return this;
      }

      @Override
      public Builder retainAll(Collection<?> keys) {
         map_.keySet().retainAll(keys);
         return this;
      }

      @Override
      public PropertyMap build() {
         return new DefaultPropertyMap(this);
      }


      //
      // Legacy support
      //

      @Deprecated
      public Builder putLegacySerializedObject(String key, byte[] serialized) {
         map_.put(key, new SerializedObject(serialized));
         return this;
      }
   }


   //
   // Deprecated methods
   //

   @Override
   @Deprecated
   public PropertyMapBuilder copy() {
      return copyBuilder();
   }

   @Override
   @Deprecated
   public String getString(String key) {
      return getString(key, null);
   }

   @Override
   @Deprecated
   public String[] getStringArray(String key) {
      return getStringArray(key, null);
   }

   @Override
   @Deprecated
   public String[] getStringArray(String key, String[] defaultValue) {
      List<String> strList = getStringList(key, defaultValue);
      if (strList != null) {
         return strList.toArray(new String[] {});
      }
      return null;
   }

   @Override
   @Deprecated
   public Integer getInt(String key) {
      return getInt(key, null);
   }

   @Override
   @Deprecated
   public Integer getInt(String key, Integer defaultValue) {
      if (!containsInteger(key)) {
         return defaultValue;
      }
      return getInteger(key, 0);
   }

   @Override
   @Deprecated
   public Integer[] getIntArray(String key) {
      return getIntArray(key, null);
   }

   @Override
   @Deprecated
   public Integer[] getIntArray(String key, Integer[] defaultValue) {
      if (!containsIntegerList(key)) {
         return defaultValue;
      }
      return ArrayUtils.toObject(getIntegerList(key, new int[] {}));
   }

   @Override
   @Deprecated
   public Long getLong(String key) {
      return getLong(key, null);
   }

   @Override
   @Deprecated
   public Long getLong(String key, Long defaultValue) {
      if (!containsLong(key)) {
         return defaultValue;
      }
      return getLong(key, 0);
   }

   @Override
   @Deprecated
   public Long[] getLongArray(String key) {
      return getLongArray(key, null);
   }

   @Override
   @Deprecated
   public Long[] getLongArray(String key, Long[] defaultValue) {
      if (!containsLongList(key)) {
         return defaultValue;
      }
      return ArrayUtils.toObject(getLongList(key, new long[] {}));
   }

   @Override
   @Deprecated
   public Double getDouble(String key) {
      return getDouble(key, null);
   }

   @Override
   @Deprecated
   public Double getDouble(String key, Double defaultValue) {
      if (!containsDouble(key)) {
         return defaultValue;
      }
      return getDouble(key, 0);
   }

   @Override
   @Deprecated
   public Double[] getDoubleArray(String key) {
      return getDoubleArray(key, null);
   }

   @Override
   @Deprecated
   public Double[] getDoubleArray(String key, Double[] defaultValue) {
      if (!containsDoubleList(key)) {
         return defaultValue;
      }
      return ArrayUtils.toObject(getDoubleList(key, new double[] {}));
   }

   @Override
   @Deprecated
   public Boolean getBoolean(String key) {
      return getBoolean(key, null);
   }

   @Override
   @Deprecated
   public Boolean getBoolean(String key, Boolean defaultValue) {
      if (!containsBoolean(key)) {
         return defaultValue;
      }
      return getBoolean(key, false);
   }

   @Override
   @Deprecated
   public Boolean[] getBooleanArray(String key) {
      return getBooleanArray(key, null);
   }

   @Override
   @Deprecated
   public Boolean[] getBooleanArray(String key, Boolean[] defaultValue) {
      if (!containsBooleanList(key)) {
         return defaultValue;
      }
      return ArrayUtils.toObject(getBooleanList(key, new boolean[] {}));
   }

   @Override
   @Deprecated
   public PropertyMap getPropertyMap(String key) {
      return getPropertyMap(key, null);
   }

   @Override
   @Deprecated
   public PropertyMap merge(PropertyMap alt) {
      return copyBuilder().putAll(alt).build();
   }

   @Override
   @Deprecated
   public Set<String> getKeys() {
      return new HashSet<String>(keySet());
   }

   @Override
   @Deprecated
   public Class getPropertyType(String key) {
      return getValueTypeForKey(key);
   }

   @Override
   @Deprecated
   public void save(String path) throws IOException {
      saveJSON(new File(path), true, false);
   }

   @Deprecated
   private static class SerializedObject {
      private final byte[] serialized;

      public SerializedObject(byte[] serialized) {
         this.serialized = serialized;
      }

      Object getObject() {
         ByteArrayInputStream bis = new ByteArrayInputStream(serialized);
         ObjectInputStream ois = null;
         try {
            ois = new ObjectInputStream(bis);
            return ois.readObject();
         } catch (IOException e) {
            return null;
         } catch (ClassNotFoundException e) {
            return null;
         } finally {
            if (ois != null) {
               try {
                  ois.close();
               } catch (IOException cantActuallyHappenWithByteArray) {
               }
            }
         }
      }
   }

   @Deprecated
   public <T> T getLegacySerializedObject(String key, T aDefault) {
      T obj = (T) getNonPrimitiveScalar(
            key, SerializedObject.class, new SerializedObject(null)).getObject();
      return obj == null ? aDefault : obj;
   }


   @Deprecated
   private static class LegacyBuilder implements PropertyMap.PropertyMapBuilder {
      // Implement legacy support by forwarding to modern builder.
      private PropertyMap.Builder modernBuilder_;

      private PropertyMap.Builder modern() {
         if (this instanceof PropertyMap.Builder) {
            return (PropertyMap.Builder) this;
         }
         if (modernBuilder_ == null) {
            modernBuilder_ = new Builder();
         }
         return modernBuilder_;
      }

      @Override
      public PropertyMap build() {
         return new DefaultPropertyMap(modern());
      }

      @Override
      @Deprecated
      public PropertyMapBuilder putString(String key, String value) {
         if (value == null) {
            return modern().remove(key);
         }
         return modern().putString(key, value);
      }

      @Override
      @Deprecated
      public PropertyMapBuilder putStringArray(String key, String[] values) {
         if (values == null) {
            return modern().remove(key);
         }
         return modern().putStringList(key, values);
      }

      @Override
      @Deprecated
      public PropertyMapBuilder putInt(String key, Integer value) {
         if (value == null) {
            return modern().remove(key);
         }
         return modern().putInteger(key, value);
      }

      @Override
      @Deprecated
      public PropertyMapBuilder putIntArray(String key, Integer[] values) {
         if (values == null) {
            return modern().remove(key);
         }
         return modern().putIntegerList(key, ArrayUtils.toPrimitive(values));
      }

      @Override
      @Deprecated
      public PropertyMapBuilder putLong(String key, Long value) {
         if (value == null) {
            return modern().remove(key);
         }
         return modern().putLong(key, value);
      }

      @Override
      @Deprecated
      public PropertyMapBuilder putLongArray(String key, Long[] values) {
         if (values == null) {
            return modern().remove(key);
         }
         return modern().putLongList(key, ArrayUtils.toPrimitive(values));
      }

      @Override
      @Deprecated
      public PropertyMapBuilder putDouble(String key, Double value) {
         if (value == null) {
            return modern().remove(key);
         }
         return modern().putDouble(key, value);
      }

      @Override
      @Deprecated
      public PropertyMapBuilder putDoubleArray(String key, Double[] values) {
         if (values == null) {
            return modern().remove(key);
         }
         return modern().putDoubleList(key, ArrayUtils.toPrimitive(values));
      }

      @Override
      @Deprecated
      public PropertyMapBuilder putBoolean(String key, Boolean value) {
         if (value == null) {
            return modern().remove(key);
         }
         return modern().putBoolean(key, value);
      }

      @Override
      @Deprecated
      public PropertyMapBuilder putBooleanArray(String key, Boolean[] values) {
         if (values == null) {
            return modern().remove(key);
         }
         return modern().putBooleanList(key, ArrayUtils.toPrimitive(values));
      }

      @Override
      @Deprecated
      public PropertyMapBuilder putPropertyMap(String key, PropertyMap values) {
         if (values == null) {
            return modern().remove(key);
         }
         return modern().putPropertyMap(key, values);
      }
   }
}