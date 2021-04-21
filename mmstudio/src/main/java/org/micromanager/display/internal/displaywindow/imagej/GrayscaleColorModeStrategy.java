/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.displaywindow.imagej;

import com.google.common.base.Preconditions;
import ij.CompositeImage;
import java.awt.Color;
import java.util.Collections;

/** @author mark */
class GrayscaleColorModeStrategy extends ColorColorModeStrategy {
  static ColorModeStrategy create(int nChannels) {
    return new GrayscaleColorModeStrategy(nChannels);
  }

  static ColorModeStrategy create() {
    return new GrayscaleColorModeStrategy(1);
  }

  private GrayscaleColorModeStrategy(int nChannels) {
    super(Collections.nCopies(nChannels, Color.WHITE));
  }

  @Override
  protected int getModeForCompositeImage() {
    return CompositeImage.GRAYSCALE;
  }

  @Override
  public void applyColor(int index, Color color) {
    Preconditions.checkNotNull(color);
  }

  @Override
  protected Color getColor(int index) {
    return Color.WHITE;
  }
}
