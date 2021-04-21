// Copyright (C) 2017 Open Imaging, Inc.
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.utils.performance;

import com.google.common.base.Strings;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An indexable skip list, sorted by key of type K, that also keeps track of the order of insertion.
 *
 * <p>This data structure is designed for running quantile computation. It allows maintaining a
 * sorted list of values that can be quickly (O(log N)) indexed and from which the oldest inserted
 * values can be removed efficiently.
 *
 * <p>See <a href="https://en.wikipedia.org/wiki/Skip_list">Skip List</a> on Wikipedia, which also
 * mentions the idea of using an indexable skip list for running median computation.
 *
 * @param <K> the key type, which must be comparable
 * @param <V> the value type
 * @author Mark A. Tsuchida
 */
final class IndexableOrderedSkipList<K extends Comparable<K>, V> {

  private static final class Cell<K extends Comparable<K>, V> implements Comparable<Cell<K, V>> {

    private final List<Cell<K, V>> nexts_;
    private final List<Integer> distances_;
    private Cell<K, V> nextInserted_;
    private final K key_;
    private V value_;
    private static final AtomicLong nextSerial_ = new AtomicLong(0);
    private final long serialNr_;

    Cell(int levels, K key, V value) {
      nexts_ = new ArrayList<Cell<K, V>>(Collections.nCopies(levels, (Cell<K, V>) null));
      distances_ = new ArrayList<Integer>(Collections.nCopies(levels, 0));
      nextInserted_ = null;
      key_ = key;
      value_ = value;
      serialNr_ = nextSerial_.getAndIncrement();
    }

    Map.Entry<K, V> getEntry() {
      return new AbstractMap.SimpleEntry<K, V>(key_, value_);
    }

    void setNext(int level, Cell<K, V> next, int distance) {
      nexts_.set(level, next);
      distances_.set(level, distance);
    }

    Cell<K, V> getNext(int level) {
      return nexts_.get(level);
    }

    void incrementDistance(int level) {
      distances_.set(level, distances_.get(level) + 1);
    }

    void decrementDistance(int level) {
      distances_.set(level, distances_.get(level) - 1);
    }

    int getDistance(int level) {
      return distances_.get(level);
    }

    void setNextInserted(Cell<K, V> next) {
      nextInserted_ = next;
    }

    Cell<K, V> getNextInserted() {
      return nextInserted_;
    }

    @Override
    public int compareTo(Cell<K, V> other) {
      if (key_ == null) {
        if (other.key_ == null) {
          return 0;
        }
        return -1;
      }
      if (other.key_ == null) {
        return +1;
      }
      int cmp = key_.compareTo(other.key_);
      if (cmp != 0) {
        return cmp;
      }
      return serialNr_ > other.serialNr_ ? +1 : serialNr_ < other.serialNr_ ? -1 : 0;
    }
  }

  private final int levels_;
  private final Cell<K, V> skipListHead_;
  private final Cell<K, V> orderHead_;
  private Cell<K, V> lastInserted_;

  private final Random random_ = new Random();

  public static <K extends Comparable<K>, V> IndexableOrderedSkipList<K, V> create(int levels) {
    if (levels < 1) {
      throw new IllegalArgumentException();
    }
    return new IndexableOrderedSkipList<K, V>(levels);
  }

  private IndexableOrderedSkipList(int levels) {
    levels_ = levels;
    skipListHead_ = new Cell<K, V>(levels_, null, null);
    for (int level = 0; level < levels_; ++level) {
      skipListHead_.setNext(level, skipListHead_, 1);
    }
    orderHead_ = new Cell<K, V>(levels, null, null);
    orderHead_.setNextInserted(orderHead_);
    lastInserted_ = orderHead_;
  }

  // Returns the index at which the key was inserted
  public int insert(K key, V value) {
    Cell<K, V> newCell = new Cell(levels_, key, value);
    lastInserted_.setNextInserted(newCell);
    lastInserted_ = newCell;
    int result = insertIntoLevel(levels_ - 1, newCell, skipListHead_, skipListHead_);
    return (result > 0 ? result : -result) - 1;
  }

  // Recursive insert implementation
  // Absolute value of return value is distance from upperLevelLeft (>= 1)
  // Sign of return value: is negative if the new cell is skipped in the
  // current level; positive otherwise.
  private int insertIntoLevel(
      int level,
      final Cell<K, V> newCell,
      final Cell<K, V> upperLevelLeft,
      final Cell<K, V> upperLevelRight) {
    Cell<K, V> left = upperLevelLeft;
    int distanceFromUpperLeftToLeft = 0;
    for (; ; ) {
      Cell<K, V> right = left.getNext(level);
      if (right == upperLevelRight || right.compareTo(newCell) > 0) {
        if (level == 0) {
          left.setNext(0, newCell, 1);
          newCell.setNext(0, right, 1);
          return distanceFromUpperLeftToLeft + 1;
        } else {
          int lowerLevelResult = insertIntoLevel(level - 1, newCell, left, right);
          boolean skipping = lowerLevelResult < 0;
          int distanceFromLeftToNew = skipping ? -lowerLevelResult : lowerLevelResult;
          boolean skip;
          if (!skipping && fiftyPercentChance()) {
            skip = false;
            int distanceFromLeftToRight = left.getDistance(level);
            int distanceFromNewToRight = distanceFromLeftToRight - distanceFromLeftToNew + 1;
            newCell.setNext(level, right, distanceFromNewToRight);
            left.setNext(level, newCell, distanceFromLeftToNew);
          } else {
            skip = true;
            left.incrementDistance(level);
          }
          int distanceFromUpperLeftToNew = distanceFromUpperLeftToLeft + distanceFromLeftToNew;
          return skip ? -distanceFromUpperLeftToNew : distanceFromUpperLeftToNew;
        }
      }
      distanceFromUpperLeftToLeft += left.getDistance(level);
      left = right;
    }
  }

  public void removeOldest() {
    Cell<K, V> oldest = orderHead_.getNextInserted();
    if (oldest == orderHead_) {
      throw new NoSuchElementException();
    }
    orderHead_.setNextInserted(oldest.getNextInserted());
    if (lastInserted_ == oldest) {
      lastInserted_ = orderHead_;
    }
    removeFromLevel(levels_ - 1, oldest, skipListHead_);
  }

  private void removeFromLevel(
      int level, final Cell<K, V> oldCell, final Cell<K, V> upperLevelLeft) {
    Cell<K, V> left = upperLevelLeft;
    for (; ; ) {
      Cell<K, V> right = left.getNext(level);
      int cmp = right.compareTo(oldCell);
      if (cmp >= 0 || right == skipListHead_) {
        if (level > 0) {
          removeFromLevel(level - 1, oldCell, left);
        }
        if (cmp == 0) {
          left.setNext(
              level,
              oldCell.getNext(level),
              left.getDistance(level) + oldCell.getDistance(level) - 1);
        } else {
          left.decrementDistance(level);
        }
        return;
      }
      left = right;
    }
  }

  public int size() {
    int ret = 0;
    Cell<K, V> cell = skipListHead_;
    for (; ; ) {
      Cell<K, V> next = cell.getNext(levels_ - 1);
      ret += cell.getDistance(levels_ - 1);
      if (next == skipListHead_) {
        return ret - 1;
      }
      cell = next;
    }
  }

  public Map.Entry<K, V> get(int index) {
    return getCellAtIndex(index).getEntry();
  }

  public List<Map.Entry<K, V>> sublist(int start, int length) {
    List<Map.Entry<K, V>> ret = new ArrayList<Map.Entry<K, V>>(length);
    Cell<K, V> cell = getCellAtIndex(start);
    for (int i = 0; i < length; ++i) {
      if (cell == skipListHead_) {
        throw new IndexOutOfBoundsException();
      }
      ret.add(cell.getEntry());
      cell = cell.getNext(0);
    }
    return ret;
  }

  private Cell<K, V> getCellAtIndex(int index) {
    return getCellAtIndexImpl(levels_ - 1, index, skipListHead_, -1);
  }

  private Cell<K, V> getCellAtIndexImpl(
      int level, int index, final Cell<K, V> upperLevelLeft, int upperLeftIndex) {
    int leftIndex = upperLeftIndex;
    Cell<K, V> left = upperLevelLeft;
    for (; ; ) {
      int rightIndex = leftIndex + left.getDistance(level);
      if (rightIndex == index) {
        return left.getNext(level);
      }
      if (rightIndex > index) {
        return getCellAtIndexImpl(level - 1, index, left, leftIndex);
      }
      leftIndex = rightIndex;
      left = left.getNext(level);
      if (left == skipListHead_) {
        throw new IndexOutOfBoundsException();
      }
    }
  }

  Boolean overrideFiftyPercentChanceForDeterministicTesting = null;

  private boolean fiftyPercentChance() {
    return overrideFiftyPercentChanceForDeterministicTesting == null
        ? random_.nextBoolean()
        : overrideFiftyPercentChanceForDeterministicTesting;
  }

  String dump() {
    StringBuilder sb = new StringBuilder();
    for (int level = levels_ - 1; level >= 0; --level) {
      Cell<K, V> cell = skipListHead_;
      sb.append('H');
      for (; ; ) {
        sb.append(Strings.repeat("=", cell.getDistance(level)));
        cell = cell.getNext(level);
        if (cell == skipListHead_) {
          sb.append('H');
          break;
        }
        sb.append(cell.key_);
      }
      sb.append('\n');
    }
    return sb.toString();
  }
}
