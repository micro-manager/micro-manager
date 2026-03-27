package org.micromanager.internal.jacque;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class SeqTest {

   // --- empty ---

   @Test
   public void testEmptyIsEmpty() {
      assertTrue(Seq.empty().isEmpty());
   }

   @Test(expected = NoSuchElementException.class)
   public void testEmptyFirstThrows() {
      Seq.empty().first();
   }

   @Test
   public void testEmptyRestIsEmpty() {
      assertTrue(Seq.empty().rest().isEmpty());
   }

   @Test
   public void testEmptyToList() {
      assertTrue(Seq.empty().toList().isEmpty());
   }

   // --- cons ---

   @Test
   public void testConsBasic() {
      Seq<Integer> s = Seq.cons(1, Seq.cons(2, Seq.empty()));
      assertFalse(s.isEmpty());
      assertEquals(Integer.valueOf(1), s.first());
      assertEquals(Integer.valueOf(2), s.rest().first());
      assertTrue(s.rest().rest().isEmpty());
   }

   @Test
   public void testConsWithSupplier() {
      Seq<Integer> s = Seq.cons(1, () -> Seq.cons(2, Seq.empty()));
      assertEquals(Arrays.asList(1, 2), s.toList());
   }

   @Test
   public void testConsTailCached() {
      AtomicInteger count = new AtomicInteger(0);
      Seq<Integer> s = Seq.cons(1, () -> {
         count.incrementAndGet();
         return Seq.cons(2, Seq.empty());
      });
      s.rest();
      s.rest();
      assertEquals(1, count.get());
   }

   // --- lazy ---

   @Test
   public void testLazyBasic() {
      Seq<Integer> s = Seq.lazy(() -> Seq.cons(42, Seq.empty()));
      assertEquals(Integer.valueOf(42), s.first());
      assertTrue(s.rest().isEmpty());
   }

   @Test
   public void testLazyCached() {
      AtomicInteger count = new AtomicInteger(0);
      Seq<Integer> s = Seq.lazy(() -> {
         count.incrementAndGet();
         return Seq.cons(1, Seq.empty());
      });
      s.first();
      s.first();
      s.rest();
      assertEquals(1, count.get());
   }

   @Test
   public void testLazyEmpty() {
      Seq<Integer> s = Seq.lazy(Seq::empty);
      assertTrue(s.isEmpty());
   }

   // --- fromList ---

   @Test
   public void testFromListEmpty() {
      assertTrue(Seq.fromList(Arrays.asList()).isEmpty());
   }

   @Test
   public void testFromListNull() {
      assertTrue(Seq.fromList(null).isEmpty());
   }

   @Test
   public void testFromList() {
      Seq<String> s = Seq.fromList(Arrays.asList("a", "b", "c"));
      assertEquals(Arrays.asList("a", "b", "c"), s.toList());
   }

   // --- range ---

   @Test
   public void testRangeZero() {
      assertTrue(Seq.range(0).isEmpty());
   }

   @Test
   public void testRange() {
      assertEquals(Arrays.asList(0, 1, 2, 3, 4), Seq.range(5).toList());
   }

   // --- map ---

   @Test
   public void testMap() {
      Seq<Integer> s = Seq.fromList(Arrays.asList(1, 2, 3));
      List<Integer> result = s.map(x -> x * 10).toList();
      assertEquals(Arrays.asList(10, 20, 30), result);
   }

   @Test
   public void testMapEmpty() {
      Seq<Integer> s = Seq.<Integer>empty().map(x -> x * 10);
      assertTrue(s.isEmpty());
   }

   @Test
   public void testMapIsLazy() {
      AtomicInteger count = new AtomicInteger(0);
      Seq<Integer> s = Seq.range(100).map(x -> {
         count.incrementAndGet();
         return x;
      });
      s.first();
      assertEquals(1, count.get());
   }

   // --- filter ---

   @Test
   public void testFilter() {
      List<Integer> result = Seq.range(6)
            .filter(x -> x % 2 == 0).toList();
      assertEquals(Arrays.asList(0, 2, 4), result);
   }

   @Test
   public void testFilterEmpty() {
      assertTrue(Seq.<Integer>empty().filter(x -> true).isEmpty());
   }

   @Test
   public void testFilterNoneMatch() {
      assertTrue(Seq.range(5).filter(x -> x > 10).isEmpty());
   }

   @Test
   public void testFilterIsLazy() {
      AtomicInteger count = new AtomicInteger(0);
      Seq<Integer> s = Seq.range(100).filter(x -> {
         count.incrementAndGet();
         return x >= 3;
      });
      // Accessing first() should test elements 0..3 (4 tests)
      s.first();
      assertEquals(4, count.get());
   }

   // --- flatMap ---

   @Test
   public void testFlatMap() {
      List<Integer> result = Seq.range(3).flatMap(x ->
            Seq.fromList(Arrays.asList(x, x * 10))).toList();
      assertEquals(Arrays.asList(0, 0, 1, 10, 2, 20), result);
   }

   @Test
   public void testFlatMapEmpty() {
      assertTrue(Seq.<Integer>empty().flatMap(
            x -> Seq.cons(x, Seq.empty())).isEmpty());
   }

   @Test
   public void testFlatMapIsLazy() {
      AtomicInteger count = new AtomicInteger(0);
      Seq<Integer> s = Seq.range(100).flatMap(x -> {
         count.incrementAndGet();
         return Seq.cons(x, Seq.empty());
      });
      s.first();
      assertEquals(1, count.get());
   }

   // --- concat ---

   @Test
   public void testConcat() {
      Seq<Integer> a = Seq.fromList(Arrays.asList(1, 2));
      Seq<Integer> b = Seq.fromList(Arrays.asList(3, 4));
      assertEquals(Arrays.asList(1, 2, 3, 4), a.concat(b).toList());
   }

   @Test
   public void testConcatEmptyLeft() {
      Seq<Integer> b = Seq.fromList(Arrays.asList(3, 4));
      assertEquals(Arrays.asList(3, 4),
            Seq.<Integer>empty().concat(b).toList());
   }

   @Test
   public void testConcatEmptyRight() {
      Seq<Integer> a = Seq.fromList(Arrays.asList(1, 2));
      assertEquals(Arrays.asList(1, 2),
            a.concat(Seq.empty()).toList());
   }

   // --- mapWithPrev ---

   @Test
   public void testMapWithPrev() {
      // f(prev, curr): prev is null for first element
      List<String> result = Seq.fromList(Arrays.asList(1, 2, 3))
            .mapWithPrev((prev, curr) ->
                  (prev == null ? "null" : prev.toString()) + "->" + curr)
            .toList();
      assertEquals(Arrays.asList("null->1", "1->2", "2->3"), result);
   }

   @Test
   public void testMapWithPrevEmpty() {
      assertTrue(Seq.<Integer>empty()
            .mapWithPrev((prev, curr) -> curr).isEmpty());
   }

   @Test
   public void testMapWithPrevSingle() {
      List<String> result = Seq.cons(42, Seq.<Integer>empty())
            .mapWithPrev((prev, curr) -> prev + ":" + curr)
            .toList();
      assertEquals(1, result.size());
      assertEquals("null:42", result.get(0));
   }

   // --- mapWithNext ---

   @Test
   public void testMapWithNext() {
      // f(curr, next): next is null for last element
      List<String> result = Seq.fromList(Arrays.asList(1, 2, 3))
            .mapWithNext((curr, next) ->
                  curr + "->" + (next == null ? "null" : next.toString()))
            .toList();
      assertEquals(Arrays.asList("1->2", "2->3", "3->null"), result);
   }

   @Test
   public void testMapWithNextEmpty() {
      assertTrue(Seq.<Integer>empty()
            .mapWithNext((curr, next) -> curr).isEmpty());
   }

   @Test
   public void testMapWithNextSingle() {
      List<String> result = Seq.cons(42, Seq.<Integer>empty())
            .mapWithNext((curr, next) -> curr + ":" + next)
            .toList();
      assertEquals(1, result.size());
      assertEquals("42:null", result.get(0));
   }

   // --- partitionBy ---

   @Test
   public void testPartitionBy() {
      Seq<Integer> s = Seq.fromList(
            Arrays.asList(1, 1, 2, 2, 2, 3, 1));
      List<List<Integer>> result =
            s.partitionBy(x -> x).toList();
      assertEquals(4, result.size());
      assertEquals(Arrays.asList(1, 1), result.get(0));
      assertEquals(Arrays.asList(2, 2, 2), result.get(1));
      assertEquals(Arrays.asList(3), result.get(2));
      assertEquals(Arrays.asList(1), result.get(3));
   }

   @Test
   public void testPartitionByEmpty() {
      assertTrue(Seq.<Integer>empty()
            .partitionBy(x -> x).isEmpty());
   }

   @Test
   public void testPartitionBySingleGroup() {
      List<List<Integer>> result = Seq.range(4)
            .partitionBy(x -> "same").toList();
      assertEquals(1, result.size());
      assertEquals(Arrays.asList(0, 1, 2, 3), result.get(0));
   }

   @Test
   public void testPartitionByIsLazy() {
      AtomicInteger count = new AtomicInteger(0);
      Seq<Integer> s = Seq.range(100).map(x -> {
         count.incrementAndGet();
         return x;
      });
      // Partition by even/odd; only realize first group
      Seq<List<Integer>> partitions = s.partitionBy(x -> x / 5);
      partitions.first();
      // Should realize only the first group (elements 0..4) plus the
      // start of the next group (element 5) to detect boundary
      assertTrue(count.get() <= 6);
   }

   // --- toList ---

   @Test
   public void testToList() {
      List<Integer> result = Seq.cons(1,
            Seq.cons(2, Seq.cons(3, Seq.empty()))).toList();
      assertEquals(Arrays.asList(1, 2, 3), result);
   }

   // --- iterator ---

   @Test
   public void testIterator() {
      Seq<Integer> s = Seq.fromList(Arrays.asList(10, 20, 30));
      Iterator<Integer> it = s.iterator();
      assertTrue(it.hasNext());
      assertEquals(Integer.valueOf(10), it.next());
      assertEquals(Integer.valueOf(20), it.next());
      assertEquals(Integer.valueOf(30), it.next());
      assertFalse(it.hasNext());
   }

   @Test(expected = NoSuchElementException.class)
   public void testIteratorExhausted() {
      Iterator<Integer> it = Seq.<Integer>empty().iterator();
      it.next();
   }

   @Test
   public void testForEach() {
      int sum = 0;
      for (int x : Seq.range(5)) {
         sum += x;
      }
      assertEquals(10, sum);
   }

   // --- Laziness verification ---

   @Test
   public void testLazyChainDoesNotRealizeAll() {
      AtomicInteger created = new AtomicInteger(0);
      Seq<Integer> s = Seq.range(1000)
            .map(x -> { created.incrementAndGet(); return x; })
            .filter(x -> x % 2 == 0)
            .map(x -> x * 3);
      // Nothing realized yet
      assertEquals(0, created.get());
      // Consume only 3 elements
      Seq<Integer> curr = s;
      for (int i = 0; i < 3 && !curr.isEmpty(); i++) {
         curr.first();
         curr = curr.rest();
      }
      // Should have realized far fewer than 1000 elements
      assertTrue("Expected fewer than 20 elements realized, got "
            + created.get(), created.get() < 20);
   }
}
