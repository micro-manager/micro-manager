/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.internal.utils;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author mark
 */
public final class ChainedMapView<K, V> implements Map<K, V> {
   private final Map<K, V> front_;
   private final Map<K, V> fallback_;

   public static <K, V> ChainedMapView<K, V> create(
         Map<K, V> front, Map<K, V> fallback) {
      return new ChainedMapView<>(front, fallback);
   }

   private ChainedMapView(Map<K, V> front, Map<K, V> fallback) {
      front_ = front;
      fallback_ = fallback;
   }

   @Override
   public int size() {
      return front_.size()
            + Sets.difference(fallback_.keySet(), front_.keySet()).size();
   }

   @Override
   public boolean isEmpty() {
      return front_.isEmpty() && fallback_.isEmpty();
   }

   @Override
   public boolean containsKey(Object key) {
      return front_.containsKey(key) || fallback_.containsKey(key);
   }

   @Override
   public boolean containsValue(Object value) {
      for (V v : values()) {
         if (v.equals(value)) {
            return true;
         }
      }
      return false;
   }

   @Override
   public V get(Object key) {
      V ret = front_.get(key);
      return ret == null ? fallback_.get(key) : ret;
   }

   @Override
   public V put(K key, V value) {
      throw new UnsupportedOperationException();
   }

   @Override
   public V remove(Object key) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void putAll(Map<? extends K, ? extends V> m) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void clear() {
      throw new UnsupportedOperationException();
   }

   @Override
   public Set<K> keySet() {
      return Sets.union(fallback_.keySet(), front_.keySet());
   }

   @Override
   public Collection<V> values() {
      // Not strictly a view, but since we're unmodifiable...
      // (to construct a real view, we probably need a custom Collection)
      Set<V> values = new HashSet<>();
      values.addAll(front_.values());
      values.addAll(Maps.difference(fallback_, front_).entriesOnlyOnLeft().values());
      return Collections.unmodifiableSet(values);
   }

   @Override
   public Set<Entry<K, V>> entrySet() {
      return Sets.union(front_.entrySet(),
            Maps.difference(fallback_, front_).entriesOnlyOnLeft().entrySet());
   }
}