package org.micromanager.internal.jacque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.junit.Test;

public class MmUtilsTest {

   @Test
   public void testGetCurrentTimeStr() {
      String ts = MmUtils.getCurrentTimeStr();
      assertNotNull(ts);
      assertTrue("Should contain date separator",
            ts.contains("-"));
      assertTrue("Should contain time separator",
            ts.contains(":"));
   }

   @Test
   @SuppressWarnings("unchecked")
   public void testJsonToDataObject() throws Exception {
      JSONObject obj = new JSONObject();
      obj.put("key1", "value1");
      obj.put("key2", 42);
      obj.put("key3", JSONObject.NULL);
      Map<String, Object> result =
            (Map<String, Object>) MmUtils.jsonToData(obj);
      assertEquals("value1", result.get("key1"));
      assertEquals(42, result.get("key2"));
      assertNull(result.get("key3"));
   }

   @Test
   @SuppressWarnings("unchecked")
   public void testJsonToDataArray() throws Exception {
      JSONArray arr = new JSONArray();
      arr.put("a");
      arr.put("b");
      arr.put("c");
      List<Object> result = (List<Object>) MmUtils.jsonToData(arr);
      assertEquals(3, result.size());
      assertEquals("a", result.get(0));
      assertEquals("b", result.get(1));
      assertEquals("c", result.get(2));
   }

   @Test
   @SuppressWarnings("unchecked")
   public void testJsonToDataNested() throws Exception {
      JSONObject inner = new JSONObject();
      inner.put("x", 1);
      JSONObject outer = new JSONObject();
      outer.put("inner", inner);
      outer.put("arr", new JSONArray("[10, 20]"));
      Map<String, Object> result =
            (Map<String, Object>) MmUtils.jsonToData(outer);
      Map<String, Object> innerMap =
            (Map<String, Object>) result.get("inner");
      assertEquals(1, innerMap.get("x"));
      List<Object> arrList = (List<Object>) result.get("arr");
      assertEquals(2, arrList.size());
   }

   @Test
   public void testJsonToDataPrimitive() {
      assertEquals("hello", MmUtils.jsonToData("hello"));
      assertEquals(42, MmUtils.jsonToData(42));
   }

   @Test
   public void testAttemptAllSuccess() {
      AtomicInteger counter = new AtomicInteger(0);
      MmUtils.attemptAll(
            counter::incrementAndGet,
            counter::incrementAndGet,
            counter::incrementAndGet);
      assertEquals(3, counter.get());
   }

   @Test
   public void testAttemptAllFirstThrows() {
      AtomicInteger counter = new AtomicInteger(0);
      try {
         MmUtils.attemptAll(
               () -> { throw new RuntimeException("first"); },
               counter::incrementAndGet,
               counter::incrementAndGet);
         fail("Should have thrown");
      } catch (RuntimeException e) {
         assertEquals("first", e.getMessage());
      }
      assertEquals("Should execute remaining actions", 2, counter.get());
   }

   @Test
   public void testAttemptAllMiddleThrows() {
      AtomicInteger counter = new AtomicInteger(0);
      try {
         MmUtils.attemptAll(
               counter::incrementAndGet,
               () -> { throw new RuntimeException("middle"); },
               counter::incrementAndGet);
         fail("Should have thrown");
      } catch (RuntimeException e) {
         assertEquals("middle", e.getMessage());
      }
      assertEquals(2, counter.get());
   }

   @Test
   public void testAttemptAllMultipleThrow() {
      try {
         MmUtils.attemptAll(
               () -> { throw new RuntimeException("first"); },
               () -> { throw new RuntimeException("second"); });
         fail("Should have thrown");
      } catch (RuntimeException e) {
         assertEquals("First exception thrown", "first", e.getMessage());
      }
   }
}
