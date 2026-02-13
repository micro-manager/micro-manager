package org.micromanager.internal.jacque;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

public abstract class Seq<T> implements Iterable<T> {

   public abstract T first();

   public abstract Seq<T> rest();

   public abstract boolean isEmpty();

   // --- Factory methods ---

   @SuppressWarnings("unchecked")
   public static <T> Seq<T> empty() {
      return (Seq<T>) EmptySeq.INSTANCE;
   }

   public static <T> Seq<T> cons(T head, Supplier<Seq<T>> tail) {
      return new ConsSeq<>(head, tail);
   }

   public static <T> Seq<T> cons(T head, Seq<T> tail) {
      return new ConsSeq<>(head, () -> tail);
   }

   public static <T> Seq<T> lazy(Supplier<Seq<T>> body) {
      return new LazySeq<>(body);
   }

   public static <T> Seq<T> fromList(List<T> list) {
      if (list == null || list.isEmpty()) {
         return empty();
      }
      return new ListSeq<>(list, 0);
   }

   public static Seq<Integer> range(int n) {
      return rangeFrom(0, n);
   }

   private static Seq<Integer> rangeFrom(int start, int end) {
      if (start >= end) {
         return empty();
      }
      return cons(start, () -> rangeFrom(start + 1, end));
   }

   // --- Lazy transformations ---

   public <R> Seq<R> map(Function<T, R> f) {
      Seq<T> self = this;
      return Seq.lazy(() -> {
         if (self.isEmpty()) {
            return Seq.empty();
         }
         return Seq.cons(f.apply(self.first()),
               () -> self.rest().map(f));
      });
   }

   public Seq<T> filter(Predicate<T> pred) {
      Seq<T> self = this;
      return Seq.lazy(() -> {
         Seq<T> s = self;
         while (!s.isEmpty()) {
            if (pred.test(s.first())) {
               T head = s.first();
               Seq<T> tail = s.rest();
               return Seq.cons(head, () -> tail.filter(pred));
            }
            s = s.rest();
         }
         return Seq.empty();
      });
   }

   public <R> Seq<R> flatMap(Function<T, Seq<R>> f) {
      Seq<T> self = this;
      return Seq.lazy(() -> {
         if (self.isEmpty()) {
            return Seq.empty();
         }
         return f.apply(self.first()).concat(self.rest().flatMap(f));
      });
   }

   public Seq<T> concat(Seq<T> other) {
      Seq<T> self = this;
      return Seq.lazy(() -> {
         if (self.isEmpty()) {
            return other;
         }
         return Seq.cons(self.first(),
               () -> self.rest().concat(other));
      });
   }

   public <R> Seq<R> mapWithPrev(BiFunction<T, T, R> f) {
      return mapWithPrevHelper(null, f);
   }

   private <R> Seq<R> mapWithPrevHelper(T prev, BiFunction<T, T, R> f) {
      Seq<T> self = this;
      return Seq.lazy(() -> {
         if (self.isEmpty()) {
            return Seq.empty();
         }
         T curr = self.first();
         return Seq.cons(f.apply(prev, curr),
               () -> self.rest().mapWithPrevHelper(curr, f));
      });
   }

   public <R> Seq<R> mapWithNext(BiFunction<T, T, R> f) {
      Seq<T> self = this;
      return Seq.lazy(() -> {
         if (self.isEmpty()) {
            return Seq.empty();
         }
         Seq<T> tail = self.rest();
         T next = tail.isEmpty() ? null : tail.first();
         return Seq.cons(f.apply(self.first(), next),
               () -> tail.mapWithNext(f));
      });
   }

   public <K> Seq<List<T>> partitionBy(Function<T, K> keyFn) {
      Seq<T> self = this;
      return Seq.lazy(() -> {
         if (self.isEmpty()) {
            return Seq.empty();
         }
         K key = keyFn.apply(self.first());
         List<T> group = new ArrayList<>();
         Seq<T> s = self;
         while (!s.isEmpty()
               && Objects.equals(key, keyFn.apply(s.first()))) {
            group.add(s.first());
            s = s.rest();
         }
         Seq<T> remaining = s;
         return Seq.cons(group,
               () -> remaining.partitionBy(keyFn));
      });
   }

   // --- Terminal operations ---

   public List<T> toList() {
      List<T> result = new ArrayList<>();
      Seq<T> s = this;
      while (!s.isEmpty()) {
         result.add(s.first());
         s = s.rest();
      }
      return result;
   }

   @Override
   public Iterator<T> iterator() {
      return new Iterator<T>() {
         private Seq<T> current = Seq.this;

         @Override
         public boolean hasNext() {
            return !current.isEmpty();
         }

         @Override
         public T next() {
            if (current.isEmpty()) {
               throw new NoSuchElementException();
            }
            T val = current.first();
            current = current.rest();
            return val;
         }
      };
   }

   // --- Inner classes ---

   private static final class EmptySeq<T> extends Seq<T> {
      @SuppressWarnings("rawtypes")
      static final EmptySeq INSTANCE = new EmptySeq();

      @Override
      public T first() {
         throw new NoSuchElementException("first() on empty Seq");
      }

      @Override
      public Seq<T> rest() {
         return this;
      }

      @Override
      public boolean isEmpty() {
         return true;
      }
   }

   private static final class ConsSeq<T> extends Seq<T> {
      private final T head;
      private Supplier<Seq<T>> tailFn;
      private Seq<T> tailVal;

      ConsSeq(T head, Supplier<Seq<T>> tail) {
         this.head = head;
         this.tailFn = tail;
      }

      @Override
      public T first() {
         return head;
      }

      @Override
      public Seq<T> rest() {
         if (tailVal == null) {
            tailVal = tailFn.get();
            tailFn = null;
         }
         return tailVal;
      }

      @Override
      public boolean isEmpty() {
         return false;
      }
   }

   private static final class LazySeq<T> extends Seq<T> {
      private Supplier<Seq<T>> fn;
      private Seq<T> realized;

      LazySeq(Supplier<Seq<T>> fn) {
         this.fn = fn;
      }

      private Seq<T> realize() {
         if (realized == null) {
            realized = fn.get();
            fn = null;
         }
         return realized;
      }

      @Override
      public T first() {
         return realize().first();
      }

      @Override
      public Seq<T> rest() {
         return realize().rest();
      }

      @Override
      public boolean isEmpty() {
         return realize().isEmpty();
      }
   }

   private static final class ListSeq<T> extends Seq<T> {
      private final List<T> list;
      private final int offset;

      ListSeq(List<T> list, int offset) {
         this.list = list;
         this.offset = offset;
      }

      @Override
      public T first() {
         if (offset >= list.size()) {
            throw new NoSuchElementException("first() on empty Seq");
         }
         return list.get(offset);
      }

      @Override
      public Seq<T> rest() {
         if (offset + 1 >= list.size()) {
            return empty();
         }
         return new ListSeq<>(list, offset + 1);
      }

      @Override
      public boolean isEmpty() {
         return offset >= list.size();
      }
   }
}
