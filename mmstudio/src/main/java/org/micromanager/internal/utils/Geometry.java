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

package org.micromanager.internal.utils;

import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;

/**
 *
 * @author mark
 */
public final class Geometry {
   private Geometry() {} // Noninstantiable

   public static void insetRectangle(Rectangle rect, Insets insets) {
      rect.x += insets.left;
      rect.y += insets.top;
      rect.width -= (insets.left + insets.right);
      rect.height -= (insets.top + insets.bottom);
   }

   public static Rectangle insettedRectangle(Rectangle rect, Insets insets) {
      return new Rectangle(
            rect.x + insets.left,
            rect.y + insets.top,
            rect.width - insets.left - insets.right,
            rect.height - insets.top - insets.bottom);
   }

   public static Point nearestPointInRectangle(Point point, Rectangle rect) {
      if (point.x < rect.x) {
         if (point.y < rect.y) {
            return new Point(rect.getLocation());
         } else if (point.y < rect.y + rect.height) {
            return new Point(rect.x, point.y);
         } else {
            return new Point(rect.x, rect.y + rect.height - 1);
         }
      } else if (point.x < rect.x + rect.width) {
         if (point.y < rect.y) {
            return new Point(point.x, rect.y);
         } else if (point.y < rect.y + rect.height) {
            return new Point(point);
         } else {
            return new Point(point.x, rect.y + rect.height - 1);
         }
      } else {
         if (point.y < rect.y) {
            return new Point(rect.x + rect.width - 1, rect.y);
         } else if (point.y < rect.y + rect.height) {
            return new Point(rect.x + rect.width - 1, point.y);
         } else {
            return new Point(rect.x + rect.width - 1,
                  rect.y + rect.height - 1);
         }
      }
   }
}