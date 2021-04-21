/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.internal.propertymap;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;

/** @author mark */
public class PropertyMapJSONSerializerTest {
  private static Gson gson;

  @Before
  public void setUp() {
    gson = new Gson();
  }

  @Test
  public void testRoundTrip() throws Exception {
    PropertyMap inner =
        PropertyMaps.builder()
            .putString("Bar", "baz")
            .putShort("hi", (short) 100)
            .putByte("ho", (byte) 50)
            .putBoolean("flag", true)
            .putLong("looong", 2L * Integer.MAX_VALUE)
            .putDoubleList("numerical", 1.2, 3.4, 5.6, 7.8)
            .putBooleanList("alternative truth", false, false, false)
            .putByteList("crlf", (byte) 0x0d, (byte) 0x0a)
            .putShortList("14-bit", (short) 0, (short) 16384)
            .putIntegerList("voltage", +15, -15)
            .putLongList("64-bit signed", Long.MIN_VALUE, Long.MAX_VALUE)
            .putFloatList("gray", 0.5f, 0.5f, 0.5f)
            .putPropertyMapList(
                "empty pair",
                Arrays.asList(
                    new PropertyMap[] {
                      PropertyMaps.emptyPropertyMap(), PropertyMaps.emptyPropertyMap()
                    }))
            .build();
    PropertyMap pm =
        PropertyMaps.builder()
            .putString("Foo", "foo")
            .putStringList("FooBar", "foo", "bar")
            .putInteger("answer", 42)
            .putFloat("n", 1.2f)
            .putString("not HTML", "<html></html>")
            .putColor("Red", Color.RED)
            .putColorList("Tree", Arrays.asList(new Color[] {Color.RED, Color.BLACK}))
            .putAffineTransform("Rotate", AffineTransform.getRotateInstance(1.0))
            .putAffineTransformList(
                "ShearAndTranslate",
                Arrays.asList(
                    new AffineTransform[] {
                      AffineTransform.getShearInstance(0.5, 0.5),
                      AffineTransform.getTranslateInstance(20.0, 15.3)
                    }))
            .putDouble("posinf", Double.POSITIVE_INFINITY)
            .putDouble("neginf", Double.NEGATIVE_INFINITY)
            .putFloat("nan", Float.NaN)
            .putPropertyMap("nested", inner)
            .putRectangle("bounds", new Rectangle(10, 20, 480, 320))
            .putDimension("size", new Dimension(480, 320))
            .putPointList("line", new Point(10, 20), new Point(470, 300))
            .putUUID("uuid", UUID.randomUUID())
            .build();
    String json = PropertyMapJSONSerializer.toJSON(pm);
    System.out.println(json);
    PropertyMap pm2 = PropertyMapJSONSerializer.fromJSON(json);
    assertEquals(pm, pm2);
  }

  @Test
  public void testLegacyEmptyNoVersion() throws Exception {
    PropertyMap pm = PropertyMapJSONSerializer.fromJSON("{}");
    assertEquals(0, pm.size());
    assertTrue(pm.isEmpty());
  }

  @Test
  public void testLegacyEmptyWithVersion() throws Exception {
    PropertyMap pm = PropertyMapJSONSerializer.fromJSON("1.0\n{}");
    assertEquals(0, pm.size());
    assertTrue(pm.isEmpty());
  }

  static class V {
    String PropType;
    int PropVal;
  }

  static class A {
    V mykey;
  }

  @Test
  public void testLegacySingle() throws Exception {
    A a = new A();
    a.mykey = new V();
    a.mykey.PropType = "Integer";
    a.mykey.PropVal = 42;
    String input = gson.toJson(a);
    System.out.println(input);
    PropertyMap pm = PropertyMapJSONSerializer.fromJSON(input);
    assertEquals(1, pm.size());
    assertFalse(pm.isEmpty());
    assertTrue(pm.containsKey("mykey"));
    assertTrue(pm.containsInteger("mykey"));
    assertEquals(int.class, pm.getValueTypeForKey("mykey"));
    assertEquals(42, pm.getInteger("mykey", -1));

    PropertyMap pm2 = PropertyMapJSONSerializer.fromJSON("1.0\n" + input);
    assertEquals(pm, pm2);
  }

  static class VV {
    String PropType;
    Object PropVal;

    VV(String t, Object v) {
      PropType = t;
      PropVal = v;
    }
  }

  @Test
  public void testLegacyAllValueTypes() throws Exception {
    Map<String, VV> data = new HashMap<String, VV>();
    data.put("test string", new VV("String", "string value"));
    data.put("test int", new VV("Integer", "42"));
    data.put("test long", new VV("Long", 1L + Integer.MAX_VALUE));
    data.put("test double", new VV("Double", 0.5));
    data.put("test boolean", new VV("Boolean", true));
    data.put("test string array", new VV("String array", new String[] {"a", "b"}));
    data.put("test int array", new VV("Integer array", new int[] {1, 2, 3}));
    data.put(
        "test long array",
        new VV("Long array", new long[] {1L + Integer.MAX_VALUE, -1L + Integer.MIN_VALUE}));
    data.put("test double array", new VV("Double array", new double[] {0.25, -0.25}));
    data.put("test boolean array", new VV("Boolean array", new boolean[] {false, true}));
    String input = gson.toJson(data);
    System.out.println(input);
    PropertyMap pm = PropertyMapJSONSerializer.fromJSON(input);
    assertEquals(10, pm.size());
    assertEquals("string value", pm.getString("test string", "blah"));
    assertEquals(42, pm.getInteger("test int", 100));
    assertEquals(1L + Integer.MAX_VALUE, pm.getLong("test long", 100L));
    assertEquals(
        Lists.newArrayList("a", "b"), pm.getStringList("test string array", new String[] {}));
    assertArrayEquals(new int[] {1, 2, 3}, pm.getIntegerList("test int array", new int[] {}));
    assertArrayEquals(
        new long[] {1L + Integer.MAX_VALUE, -1L + Integer.MIN_VALUE},
        pm.getLongList("test long array", new long[] {}));
    assertArrayEquals(
        new double[] {0.25, -0.25},
        pm.getDoubleList("test double array", new double[] {}),
        0.000001);
    assertEquals(false, pm.getBooleanList("test boolean array", new boolean[] {})[0]);
    assertEquals(true, pm.getBooleanList("test boolean array", new boolean[] {})[1]);

    PropertyMap pm2 = PropertyMapJSONSerializer.fromJSON("1.0\n" + input);
    assertEquals(pm.size(), pm2.size());
    assertEquals(pm, pm2);
  }

  @Test
  public void testLegacySerializedObject() throws Exception {
    String base64encoded =
        "rO0ABXNyAB1qYXZhLmF3dC5nZW9tLkFmZmluZVRyYW5zZm9ybRJ4kRVK1f9iAwAGRAADbTAwRAADbTAxRAADbTAyRAADbTEwRAADbTExRAADbTEyeHAAAAAAAAAAAD/gAAAAAAAAQCQAAAAAAAA/4AAAAAAAAD/wAAAAAAAAQDQAAAAAAAB4";
    PropertyMap pm =
        PropertyMapJSONSerializer.fromJSON(
            String.format(
                "{"
                    + "\"KEY\": "
                    + "{"
                    + "\"PropType\": \"Object\", "
                    + "\"PropVal\": \"%s\""
                    + "}"
                    + "}",
                base64encoded));
    assertEquals(
        new AffineTransform(0.0, 0.5, 0.5, 1.0, 10.0, 20.0),
        ((DefaultPropertyMap) pm).getLegacySerializedObject("KEY", null));
  }
}
