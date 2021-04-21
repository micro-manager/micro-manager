/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.displaywindow.imagej;

import com.google.common.base.Preconditions;
import ij.CompositeImage;
import ij.process.LUT;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;

/**
 * The color mode strategy for single-color mode.
 *
 * <p>(No, it's not a typo.)
 *
 * @author mark
 */
class ColorColorModeStrategy extends AbstractColorModeStrategy {
  private final List<Color> colors_;

  static ColorModeStrategy create(List<Color> colors) {
    return new ColorColorModeStrategy(colors);
  }

  static ColorModeStrategy create(Color monochromeColor) {
    return create(Collections.singletonList(monochromeColor));
  }

  protected ColorColorModeStrategy(List<Color> colors) {
    super(colors.size());
    colors_ = new ArrayList<Color>(colors);
  }

  protected Color getColor(int index) {
    if (index >= colors_.size()) {
      // Nobody uses gray. So we will know that there is a bug and the color
      // has not been set (if we default to white or some other color, we
      // might think the mode is set incorrectly; if we default to black,
      // it's hard to tell what's going on).
      return Color.GRAY;
    }
    return colors_.get(index);
  }

  @Override
  public void applyColor(int index, Color color) {
    Preconditions.checkNotNull(color);
    if (index >= colors_.size()) {
      colors_.addAll(Collections.nCopies(index + 1 - colors_.size(), Color.GRAY));
    }
    if (color.equals(getColor(index))) {
      return;
    }
    super.flushCachedLUTs();
    colors_.set(index, color);
    super.apply();
  }

  @Override
  protected LUT getLUT(int index, double gamma) {
    return ImageUtils.makeLUT(getColor(index), gamma);
  }

  @Override
  protected int getModeForCompositeImage() {
    return CompositeImage.COLOR;
  }
}
