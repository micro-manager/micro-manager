/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.displaywindow.imagej;

import ij.CompositeImage;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** @author mark */
final class CompositeColorModeStrategy extends ColorColorModeStrategy {
  private final List<Boolean> visibilities_;

  static ColorModeStrategy create(List<Color> colors) {
    return new CompositeColorModeStrategy(colors);
  }

  private CompositeColorModeStrategy(List<Color> colors) {
    super(colors);
    visibilities_ = new ArrayList<Boolean>(Collections.nCopies(colors.size(), true));
  }

  @Override
  protected boolean isVisibleInComposite(int index) {
    if (index >= visibilities_.size()) {
      return true;
    }
    return visibilities_.get(index);
  }

  @Override
  public void applyVisibleInComposite(int index, boolean visible) {
    if (isVisibleInComposite(index) == visible) {
      return;
    }
    if (index >= visibilities_.size()) {
      visibilities_.addAll(Collections.nCopies(index + 1 - visibilities_.size(), true));
    }
    visibilities_.set(index, visible);
    apply();
  }

  @Override
  protected int getModeForCompositeImage() {
    return CompositeImage.COMPOSITE;
  }
}
