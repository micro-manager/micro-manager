// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//               Henry Pinkard, Chris Weisiger, Mark A. Tsuchida
//
// COPYRIGHT:    2006-2015 Regents of the University of California
//               2015-2017 Open Imaging, Inc.
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

package org.micromanager.display.inspector.internal.panels.intensity;

import com.google.common.base.Preconditions;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import org.apache.commons.lang3.event.EventListenerSupport;

public final class HistogramView extends JPanel {
  public static interface Listener {
    void histogramScalingMinChanged(int component, long newMin);

    void histogramScalingMaxChanged(int component, long newMax);

    void histogramGammaChanged(double newGamma);
  }

  private final EventListenerSupport<Listener> listeners_ =
      new EventListenerSupport<>(Listener.class, Listener.class.getClassLoader());

  // Data state
  private static class ComponentState {
    long[] graph_ = new long[0];
    long rangeMax_ = 0;
    Color color_ = Color.GRAY;
    Color highlightColor_ = Color.YELLOW;
    long highlightIntensity_ = -1; // Negative = off
    long scalingMin_ = 0;
    long scalingMax_ = rangeMax_;

    float[] cachedInterpolatedLogScaledGraph_;
    Path2D.Float cachedPath_;
  }

  private final List<ComponentState> componentStates_ = new ArrayList<>();
  private boolean allowGammaScaling_ = true;
  private double gamma_ = 1.0;
  private boolean fillHistograms_ = true;
  private boolean plotLogIntensity_ = false;
  private boolean roiIndicatorEnabled_ = false;
  private String overlayText_ = null;

  // Layout state
  // All in coords relative to the panel:
  private Rectangle graphRect_;
  private Rectangle scalingMinLabelRect_;
  private Rectangle scalingMaxLabelRect_;
  private Rectangle scalingMinHandleRect_;
  private Rectangle scalingMaxHandleRect_;
  private Rectangle scalingMinAreaRect_;
  private Rectangle scalingMaxAreaRect_;
  private Rectangle gammaHandleRect_;
  private Path2D.Float cachedGammaMappingPath_;

  // UI state
  private int selectedComponent_ = 0;

  private static enum Handle {
    NONE,
    SCALING_MIN,
    SCALING_MAX,
    GAMMA,
  }

  private Handle handleBeingDragged_ = Handle.NONE;
  private long scalingHandleDragOriginalValue_;
  private double gammaHandleDragOriginalValue_;

  private static final int HORIZONTAL_MARGIN = 12;
  private static final int VERTICAL_MARGIN = 12;
  private static final int MIN_GRAPH_WIDTH = 128;
  private static final int MIN_GRAPH_HEIGHT = 32;
  private static final int LUT_HANDLE_SIZE = 10;
  private static final int GAMMA_HANDLE_RADIUS = 5;
  private static final float INTENSITY_FONT_SIZE = 11.0f;
  private static final float OVERLAY_FONT_SIZE = 12.0f;
  private static final int OVERLAY_FONT_STYLE = Font.BOLD;
  private static final Color OVERLAY_COLOR = Color.GRAY;
  private static final double GAMMA_MIN = 1e-1;
  private static final double GAMMA_MAX = 1e+1;

  public static HistogramView create() {
    final HistogramView instance = new HistogramView();

    instance.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            instance.mouseClicked(e);
          }

          @Override
          public void mousePressed(MouseEvent e) {
            instance.mousePressed(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            instance.mouseReleased(e);
          }
        });

    instance.addMouseMotionListener(
        new MouseMotionAdapter() {
          @Override
          public void mouseDragged(MouseEvent e) {
            instance.mouseDragged(e);
          }
        });

    return instance;
  }

  private HistogramView() {
    super.setOpaque(true);
    super.setMinimumSize(
        new Dimension(
            2 * HORIZONTAL_MARGIN + MIN_GRAPH_WIDTH, 2 * VERTICAL_MARGIN + MIN_GRAPH_HEIGHT));
  }

  public void addListener(Listener listener) {
    listeners_.addListener(listener, true);
  }

  public void removeListener(Listener listener) {
    listeners_.removeListener(listener);
  }

  public void setSelectedComponent(int component) {
    Preconditions.checkElementIndex(component, componentStates_.size());
    selectedComponent_ = component;
    cachedGammaMappingPath_ = null;
  }

  public void setComponentGraph(int component, long[] graph, long rangeMax) {
    setComponentGraph(component, graph, graph.length, rangeMax);
  }

  public void setComponentGraph(int component, long[] graph, int graphLen, long rangeMax) {
    Preconditions.checkArgument(component >= 0);
    Preconditions.checkArgument(graphLen <= graph.length);
    Preconditions.checkArgument(rangeMax > 0);
    addComponentIfNecessary(component);
    ComponentState state = componentStates_.get(component);
    boolean rangeMaxChanged = (rangeMax != state.rangeMax_);
    state.graph_ = Arrays.copyOf(graph, graphLen);
    state.rangeMax_ = rangeMax;

    if (component == selectedComponent_ && rangeMaxChanged) {
      nullRectsAndMappingPath();
    }
    state.cachedInterpolatedLogScaledGraph_ = null;
    state.cachedPath_ = null;
    repaint();
  }

  public void clearGraphs() {
    nullRectsAndMappingPath();
    for (int i = 0; i < componentStates_.size(); ++i) {
      ComponentState state = componentStates_.get(i);
      state.graph_ = null;
      state.rangeMax_ = 0;
      state.scalingMin_ = 0;
      state.scalingMax_ = 0;
      state.cachedInterpolatedLogScaledGraph_ = null;
      state.cachedPath_ = null;
    }
    repaint();
  }

  public void setComponentColor(int component, Color color, Color highlightColor) {
    Preconditions.checkArgument(component >= 0);
    addComponentIfNecessary(component);
    ComponentState state = componentStates_.get(component);
    state.color_ = color;
    state.highlightColor_ = color;
    repaint();
  }

  public void clearComponentHighlights() {
    for (int i = 0; i < componentStates_.size(); ++i) {
      ComponentState state = componentStates_.get(i);
      state.highlightIntensity_ = -1;
    }
    repaint();
  }

  public void setComponentHighlight(int component, long intensityValue) {
    Preconditions.checkArgument(component >= 0);
    addComponentIfNecessary(component);
    componentStates_.get(component).highlightIntensity_ = intensityValue;
    repaint();
  }

  public void setComponentScaling(int component, long scalingMin, long scalingMax) {
    Preconditions.checkArgument(component >= 0);
    addComponentIfNecessary(component);
    ComponentState state = componentStates_.get(component);
    state.scalingMin_ = scalingMin;
    state.scalingMax_ = scalingMax;
    if (component == selectedComponent_) { // Invalidate layout
      nullRectsAndMappingPath();
    }
    repaint();
  }

  private void nullRectsAndMappingPath() {
    scalingMinLabelRect_ = null;
    scalingMaxLabelRect_ = null;
    scalingMinHandleRect_ = null;
    scalingMaxHandleRect_ = null;
    gammaHandleRect_ = null;
    cachedGammaMappingPath_ = null;
  }

  public long getComponentScalingMin(int component) {
    Preconditions.checkElementIndex(component, componentStates_.size());
    return componentStates_.get(component).scalingMin_;
  }

  public long getComponentScalingMax(int component) {
    Preconditions.checkElementIndex(component, componentStates_.size());
    return componentStates_.get(component).scalingMax_;
  }

  public void setGamma(double gamma) {
    Preconditions.checkState(allowGammaScaling_);
    gamma_ = gamma;
    // Invalidate layout
    gammaHandleRect_ = null;
    cachedGammaMappingPath_ = null;
    repaint();
  }

  public void setLogIntensity(boolean useLog) {
    plotLogIntensity_ = useLog;
    for (ComponentState state : componentStates_) {
      state.cachedInterpolatedLogScaledGraph_ = null;
      state.cachedPath_ = null;
    }
    repaint();
  }

  public void setROIIndicator(boolean enable) {
    roiIndicatorEnabled_ = enable;
    repaint();
  }

  public void setOverlayText(String text) {
    overlayText_ = text;
    repaint();
  }

  private void addComponentIfNecessary(int component) {
    while (component >= componentStates_.size()) {
      componentStates_.add(new ComponentState());
    }
    if (componentStates_.size() > 1) {
      fillHistograms_ = false;
      allowGammaScaling_ = false;
      gamma_ = 1.0;
      cachedGammaMappingPath_ = null;
      repaint();
    }
  }

  //
  // Layout
  //

  @Override
  public void invalidate() {
    // Layout is being recomputed, so invalidate our internal layout
    graphRect_ = null;
    scalingMinLabelRect_ = null;
    scalingMaxLabelRect_ = null;
    scalingMinHandleRect_ = null;
    scalingMaxHandleRect_ = null;
    scalingMinAreaRect_ = null;
    scalingMaxAreaRect_ = null;
    gammaHandleRect_ = null;
    cachedGammaMappingPath_ = null;
    for (ComponentState state : componentStates_) {
      state.cachedInterpolatedLogScaledGraph_ = null;
      state.cachedPath_ = null;
    }
    super.validate();
  }

  private Rectangle getGraphRect() {
    if (graphRect_ == null) {
      Rectangle bounds = getBounds();
      graphRect_ =
          new Rectangle(
              HORIZONTAL_MARGIN,
              VERTICAL_MARGIN,
              bounds.width - 2 * HORIZONTAL_MARGIN,
              bounds.height - 2 * VERTICAL_MARGIN);
    }
    return graphRect_;
  }

  private float getScalingHandlePos(int component, boolean top) {
    ComponentState state = componentStates_.get(component);
    float intensity = top ? state.scalingMax_ : state.scalingMin_;
    float xPos = intensityFractionToGraphXPos(intensity / state.rangeMax_);
    return (float) (top ? Math.ceil(xPos) : Math.floor(xPos));
  }

  // See also: drawScalingHandle()
  private Rectangle getScalingHandleRect(int component, boolean top) {
    if (top && scalingMaxHandleRect_ == null || !top && scalingMinHandleRect_ == null) {
      Rectangle rect = getGraphRect();
      int x = (int) getScalingHandlePos(component, top);
      int y = top ? rect.y : rect.y + rect.height;

      final int s = LUT_HANDLE_SIZE;
      if (top) {
        scalingMaxHandleRect_ = new Rectangle(x, y - s, s, s);
      } else {
        scalingMinHandleRect_ = new Rectangle(x - s, y, s, s);
      }
    }
    return top ? scalingMaxHandleRect_ : scalingMinHandleRect_;
  }

  // See also: drawScalingLabel()
  private Rectangle getScalingLabelRect(int component, boolean top) {
    if (top && scalingMaxLabelRect_ == null || !top && scalingMinLabelRect_ == null) {
      Rectangle rect = getGraphRect();
      ComponentState state = componentStates_.get(component);
      long intensity = top ? state.scalingMax_ : state.scalingMin_;
      String text = Long.toString(intensity);

      int x = (int) getScalingHandlePos(component, top);

      boolean drawOnLeftOfHandle = top;
      if (top && intensity < 0.5 * state.rangeMax_) {
        drawOnLeftOfHandle = false;
      } else if (!top && intensity > 0.5 * state.rangeMax_) {
        drawOnLeftOfHandle = true;
      }

      FontMetrics metrics = getFontMetrics(getFont().deriveFont(INTENSITY_FONT_SIZE));
      int width = metrics.stringWidth(text);
      if (top) {
        x += drawOnLeftOfHandle ? -width - 1 : LUT_HANDLE_SIZE;
        scalingMaxLabelRect_ = new Rectangle(x, 0, width, VERTICAL_MARGIN);
      } else {
        x += drawOnLeftOfHandle ? -width - LUT_HANDLE_SIZE : 2;
        scalingMinLabelRect_ = new Rectangle(x, rect.y + rect.height, width, VERTICAL_MARGIN);
      }
    }
    return top ? scalingMaxLabelRect_ : scalingMinLabelRect_;
  }

  private Rectangle getScalingAreaRect(boolean top) {
    if (top && scalingMaxAreaRect_ == null || !top && scalingMinAreaRect_ == null) {
      Rectangle rect = getGraphRect();
      if (top) {
        scalingMaxAreaRect_ = new Rectangle(rect.x, 0, rect.width, rect.y);
      } else {
        scalingMinAreaRect_ =
            new Rectangle(rect.x, rect.y + rect.height, rect.width, getBounds().height);
      }
    }
    return top ? scalingMaxAreaRect_ : scalingMinAreaRect_;
  }

  private Rectangle getGammaHandleRect() {
    if (gammaHandleRect_ == null) {
      ComponentState state = componentStates_.get(selectedComponent_);
      Rectangle rect = getGraphRect();
      float loX = intensityFractionToGraphXPos((float) state.scalingMin_ / state.rangeMax_);
      float hiX = intensityFractionToGraphXPos((float) state.scalingMax_ / state.rangeMax_);
      int x = Math.round(0.5f * (loX + hiX));
      float yFrac = (float) Math.pow(0.5, gamma_);
      int y = Math.round(frequencyFractionToGraphYPos(yFrac));

      int s = GAMMA_HANDLE_RADIUS;
      gammaHandleRect_ = new Rectangle(x - s, y - s, 2 * s, 2 * s);
    }
    return gammaHandleRect_;
  }

  private float intensityFractionToGraphXPos(float intensityFraction) {
    Rectangle rect = getGraphRect();
    return rect.x + intensityFraction * rect.width - 0.5f;
  }

  private float frequencyFractionToGraphYPos(float freqFraction) {
    Rectangle rect = getGraphRect();
    return rect.y + (1.0f - freqFraction) * rect.height - 0.5f;
  }

  private float graphXPosToIntensityFraction(float x) {
    Rectangle rect = getGraphRect();
    return (x - rect.x) / rect.width;
  }

  private float graphYPosToFrequencyFraction(float y) {
    Rectangle rect = getGraphRect();
    return 1.0f - (y + 0.5f - rect.y) / rect.height;
  }

  private double graphPosToGamma(float x, float y) {
    ComponentState state = componentStates_.get(selectedComponent_);
    Rectangle rect = getGraphRect();
    float loX = intensityFractionToGraphXPos((float) state.scalingMin_ / state.rangeMax_);
    float hiX = intensityFractionToGraphXPos((float) state.scalingMax_ / state.rangeMax_);

    float xFrac = (x + 0.5f - loX) / (hiX - loX);
    float yFrac = graphYPosToFrequencyFraction(y);

    if (xFrac >= 1.0f || yFrac <= 0.0f) {
      return GAMMA_MAX;
    }
    if (xFrac <= 0.0f || yFrac >= 1.0f) {
      return GAMMA_MIN;
    }
    double gamma = Math.log(yFrac) / Math.log(xFrac);
    return Math.max(GAMMA_MIN, Math.min(GAMMA_MAX, gamma));
  }

  //
  // Drawing
  //

  @Override
  protected void paintComponent(Graphics graphics) {
    super.paintComponent(graphics);
    Graphics2D g = (Graphics2D) graphics.create();

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    int numComponents = componentStates_.size();

    drawGraphBackground(g);
    if (numComponents > 0) {
      drawRangeMaxLabel(g);
    }

    for (int i = 0; i < componentStates_.size(); ++i) {
      if (i != selectedComponent_) {
        drawComponentGraph(i, g);
      }
    }
    if (numComponents > 0) {
      drawComponentGraph(selectedComponent_, g);
    }

    for (int i = 0; i < componentStates_.size(); ++i) {
      if (i != selectedComponent_) {
        drawComponentScalingLimits(i, g);
        drawHighlightedIntensity(i, g);
      }
    }
    if (numComponents > 0) {
      drawComponentScalingLimits(selectedComponent_, g);
      drawHighlightedIntensity(selectedComponent_, g);
    }

    if (numComponents > 0) {
      drawScalingHandlesAndLabels(selectedComponent_, g);
      drawGammaMappingAndHandle(selectedComponent_, g);
    }

    drawLogIntensityIndicator(g);
    drawGammaIndicator(g);
    drawROIIndicator(g);
    drawOverlayText(g);
  }

  private void drawGraphBackground(Graphics2D g) {
    Rectangle rect = getGraphRect();

    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setColor(Color.BLACK);
    g2d.fillRect(rect.x, rect.y, rect.width, rect.height);
  }

  private void drawRangeMaxLabel(Graphics2D g) {
    if (componentStates_.isEmpty()) {
      return;
    }
    final long rangeMax = componentStates_.get(0).rangeMax_;
    for (ComponentState state : componentStates_) {
      if (state.rangeMax_ != rangeMax) {
        return; // Only draw if all components have the same max
      }
    }
    String text = Long.toString(rangeMax);
    Rectangle rect = getGraphRect();
    Point graphBottomRight = new Point(rect.x + rect.width, rect.y + rect.height);

    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setFont(g.getFont().deriveFont(INTENSITY_FONT_SIZE));
    FontMetrics metrics = g.getFontMetrics();
    int x = graphBottomRight.x - metrics.stringWidth(text);
    if (x <= getScalingHandlePos(selectedComponent_, false)) {
      return; // Hide when scaling lower limit handle overlaps
    }
    g2d.drawString(text, x, graphBottomRight.y + metrics.getAscent());
  }

  private void drawComponentGraph(int component, Graphics2D g) {
    Path2D.Float path = getComponentGraphPath(component);
    if (path == null) {
      return;
    }
    Rectangle rect = getGraphRect();

    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setClip(rect.x, rect.y, rect.width, rect.height);
    g2d.setColor(componentStates_.get(component).color_);
    if (fillHistograms_) {
      g2d.fill(path);
    } else {
      g2d.setStroke(new BasicStroke(2.0f));
      g2d.draw(path);
    }
  }

  private void drawComponentScalingLimits(int component, Graphics2D g) {
    Rectangle rect = getGraphRect();
    ComponentState state = componentStates_.get(component);
    if (state.rangeMax_ <= 0) {
      return;
    }
    int offset = 2 * component;
    float loXPos = intensityFractionToGraphXPos((float) state.scalingMin_ / state.rangeMax_);
    float hiXPos = intensityFractionToGraphXPos((float) state.scalingMax_ / state.rangeMax_);

    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setClip(rect.x, rect.y, rect.width, rect.height);
    g2d.setColor(state.color_);
    g2d.setStroke(
        new BasicStroke(
            1.0f,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            10.0f,
            new float[] {5.0f, 5.0f},
            offset));
    g2d.draw(new Line2D.Float(loXPos, rect.y, loXPos, rect.y + rect.height));
    g2d.draw(new Line2D.Float(hiXPos, rect.y, hiXPos, rect.y + rect.height));
  }

  private void drawHighlightedIntensity(int component, Graphics2D g) {
    ComponentState state = componentStates_.get(component);
    if (state.rangeMax_ <= 0) {
      return;
    }
    if (state.highlightIntensity_ < 0) {
      return;
    }
    int offset = 2 * component;
    float xPos = intensityFractionToGraphXPos((float) state.highlightIntensity_ / state.rangeMax_);
    Rectangle rect = getGraphRect();

    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setClip(rect.x, rect.y, rect.width, rect.height);
    g2d.setColor(state.highlightColor_);
    g2d.setStroke(
        new BasicStroke(
            1.5f,
            BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER,
            10.0f,
            new float[] {5.0f, 5.0f},
            offset));
    g2d.draw(new Line2D.Float(xPos, rect.y, xPos, rect.y + rect.height));
  }

  private void drawScalingHandlesAndLabels(int component, Graphics2D g) {
    drawScalingHandle(component, true, g);
    drawScalingHandle(component, false, g);
    drawScalingLabel(component, true, g);
    drawScalingLabel(component, false, g);
  }

  // See also: getScalingHandleRect()
  private void drawScalingHandle(int component, boolean top, Graphics2D g) {
    Rectangle rect = getGraphRect();
    ComponentState state = componentStates_.get(component);
    if (state.rangeMax_ <= 0) {
      return;
    }
    float x = getScalingHandlePos(component, top);
    float y = top ? rect.y : rect.y + rect.height;
    if (x < rect.x - 1 || x > rect.x + rect.width) {
      return;
    }

    final int s = LUT_HANDLE_SIZE * (top ? -1 : 1);
    Path2D.Float path = new Path2D.Float(Path2D.WIND_EVEN_ODD, 3);
    path.moveTo(x, y);
    path.lineTo(x, y + s);
    path.lineTo(x - s, y + s);
    path.closePath();

    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setStroke(new BasicStroke(1.0f));
    g2d.setColor(state.color_);
    g2d.fill(path);
    g2d.setColor(Color.BLACK);
    g2d.draw(path);
  }

  // See also: getScalingLabelRect()
  private void drawScalingLabel(int component, boolean top, Graphics2D g) {
    Rectangle rect = getGraphRect();
    ComponentState state = componentStates_.get(component);
    if (state.rangeMax_ <= 0) {
      return;
    }
    long intensity = top ? state.scalingMax_ : state.scalingMin_;
    String text = Long.toString(intensity);

    float x = getScalingHandlePos(component, top);
    float y = top ? rect.y : rect.y + rect.height;
    if (x < rect.x - 1 || x > rect.x + rect.width) {
      return;
    }

    boolean drawOnLeftOfHandle = top;
    if (top && intensity < 0.5 * state.rangeMax_) {
      drawOnLeftOfHandle = false;
    } else if (!top && intensity > 0.5 * state.rangeMax_) {
      drawOnLeftOfHandle = true;
    }

    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setFont(g.getFont().deriveFont(INTENSITY_FONT_SIZE));
    FontMetrics metrics = g.getFontMetrics();
    int width = metrics.stringWidth(text);
    int vOffset = top ? -1 : metrics.getAscent() - 1;
    if (top) {
      x += drawOnLeftOfHandle ? -width - 1 : LUT_HANDLE_SIZE;
    } else {
      x += drawOnLeftOfHandle ? -width - LUT_HANDLE_SIZE : 2;
    }
    y += vOffset;
    g2d.drawString(text, x, y);
  }

  private void drawGammaMappingAndHandle(int component, Graphics2D g) {
    if (componentStates_.get(component).rangeMax_ <= 0) {
      return;
    }
    Path2D.Float path = getGammaMappingPath(component);
    Rectangle rect = getGraphRect();

    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setClip(rect.x, rect.y, rect.width, rect.height);
    g2d.setColor(Color.GRAY);
    g2d.setStroke(new BasicStroke(1.5f));
    g2d.draw(path);

    if (allowGammaScaling_) {
      Rectangle handleRect = getGammaHandleRect();
      g2d.fillOval(handleRect.x, handleRect.y, handleRect.width, handleRect.height);
    }
  }

  private void drawLogIntensityIndicator(Graphics2D g) {
    if (!plotLogIntensity_) {
      return;
    }
    String text = "LOG-Y";
    Rectangle rect = getGraphRect();
    Point graphTopRight = new Point(rect.x + rect.width, rect.y);

    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setColor(OVERLAY_COLOR);
    g2d.setFont(g.getFont().deriveFont(OVERLAY_FONT_SIZE).deriveFont(OVERLAY_FONT_STYLE));
    FontMetrics metrics = g.getFontMetrics();
    g2d.drawString(
        text,
        graphTopRight.x - metrics.stringWidth(text) - 3,
        graphTopRight.y + metrics.getAscent());
  }

  private void drawGammaIndicator(Graphics2D g) {
    if (!allowGammaScaling_ || handleBeingDragged_ != Handle.GAMMA) {
      return;
    }
    String text = "gamma = " + String.format("%1.2f", gamma_);
    Rectangle rect = getGraphRect();
    Point graphBottomRight = new Point(rect.x + rect.width, rect.y + rect.height);

    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setColor(OVERLAY_COLOR);
    g2d.setFont(g2d.getFont().deriveFont(OVERLAY_FONT_SIZE).deriveFont(OVERLAY_FONT_STYLE));
    FontMetrics metrics = g2d.getFontMetrics();
    g2d.drawString(
        text,
        graphBottomRight.x - metrics.stringWidth(text) - 3,
        graphBottomRight.y - metrics.getMaxDescent());
  }

  private void drawROIIndicator(Graphics2D g) {
    if (!roiIndicatorEnabled_) {
      return;
    }
    String text = "ROI";
    Rectangle rect = getGraphRect();
    Point graphRight = new Point(rect.x + rect.width, rect.y + rect.height / 2);

    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setColor(OVERLAY_COLOR);
    g2d.setFont(g2d.getFont().deriveFont(OVERLAY_FONT_SIZE).deriveFont(OVERLAY_FONT_STYLE));
    FontMetrics metrics = g2d.getFontMetrics();
    g2d.drawString(
        text, graphRight.x - metrics.stringWidth(text) - 3, graphRight.y + metrics.getAscent() / 2);
  }

  private void drawOverlayText(Graphics2D g) {
    if (overlayText_ == null || overlayText_.isEmpty()) {
      return;
    }
    String text = overlayText_;
    Rectangle rect = getGraphRect();
    Point graphTop = new Point(rect.x + rect.width / 2, rect.y);

    Graphics2D g2d = (Graphics2D) g.create();
    g2d.setColor(OVERLAY_COLOR);
    g2d.setFont(g2d.getFont().deriveFont(OVERLAY_FONT_SIZE).deriveFont(OVERLAY_FONT_STYLE));
    FontMetrics metrics = g2d.getFontMetrics();
    g2d.drawString(
        text, graphTop.x - metrics.stringWidth(text) / 2, graphTop.y + metrics.getAscent());
  }

  private Path2D.Float getComponentGraphPath(int component) {
    ComponentState state = componentStates_.get(component);
    if (state.cachedPath_ == null) {
      Rectangle rect = getGraphRect();
      float[] data = getComponentInterpolatedLogScaledData(component);
      float dataMax = getComponentInterpolatedLogScaledDataMax(component);
      float dataScaling = (float) rect.height / dataMax;
      float pixelsPerBin = (float) rect.width / data.length;

      if (dataMax == 0.0) {
        // A zero-area path can cause rendering artifacts (seen with Apple
        // Java 6), so skip the graph entirely
        return null;
      }

      state.cachedPath_ = new Path2D.Float(Path2D.WIND_EVEN_ODD, 2 * data.length + 2);
      state.cachedPath_.moveTo(0.0f, (float) rect.height);
      for (int i = 0; i < data.length; ++i) {
        float x = i * pixelsPerBin;
        float y = rect.height - dataScaling * data[i];
        state.cachedPath_.lineTo(x, y); // Vertical
        state.cachedPath_.lineTo(x + pixelsPerBin, y); // Horizontal
      }
      state.cachedPath_.lineTo((float) rect.width, (float) rect.height);
      if (fillHistograms_) {
        state.cachedPath_.closePath();
      }
      state.cachedPath_.transform(AffineTransform.getTranslateInstance(rect.x - 0.5, rect.y - 0.5));
    }
    return state.cachedPath_;
  }

  private float[] getComponentInterpolatedLogScaledData(int component) {
    Rectangle rect = getGraphRect();
    ComponentState state = componentStates_.get(component);
    if (state.cachedInterpolatedLogScaledGraph_ == null) {
      if (state.graph_ == null || state.graph_.length == 0) {
        return new float[0];
      }

      // Reduce histogram bin count to something reasonable for drawing.
      // To avoid discretization artifacts, we should try to use integer
      // (thus power-of-2) binning. Also, resample to at least 2x pixel
      // resolution when possible, to take advantage of antialiasing.
      // Assumption: state.graph_.length is a power of 2 (otherwise we would
      // need to factor it, or quality will be degraded).
      int interpolatedLen = 64;
      while (interpolatedLen < 2 * rect.width) {
        interpolatedLen *= 2;
      }
      while (interpolatedLen > state.graph_.length) {
        interpolatedLen /= 2;
      }
      if (interpolatedLen < state.graph_.length) {
        state.cachedInterpolatedLogScaledGraph_ =
            makeInterpolatedHistogram(state.graph_, interpolatedLen);
      } else { // No interpolation necessary
        state.cachedInterpolatedLogScaledGraph_ = new float[state.graph_.length];
        for (int i = 0; i < state.cachedInterpolatedLogScaledGraph_.length; ++i) {
          state.cachedInterpolatedLogScaledGraph_[i] = state.graph_[i];
        }
      }

      // Apply log scaling if requested
      if (plotLogIntensity_) {
        for (int i = 0; i < state.cachedInterpolatedLogScaledGraph_.length; ++i) {
          state.cachedInterpolatedLogScaledGraph_[i] =
              state.cachedInterpolatedLogScaledGraph_[i] > 1.0f
                  ? (float) Math.log(state.cachedInterpolatedLogScaledGraph_[i])
                  : 0.0f;
        }
      }
    }
    return state.cachedInterpolatedLogScaledGraph_;
  }

  private float getComponentInterpolatedLogScaledDataMax(int component) {
    float[] fGraph = getComponentInterpolatedLogScaledData(component);
    float max = 0.0f;
    for (float v : fGraph) {
      if (v > max) {
        max = v;
      }
    }
    return max;
  }

  private float[] makeInterpolatedHistogram(long[] data, int width) {
    float[] result = new float[width];
    float binsPerPixel = (float) data.length / width;
    // Distribute fractional bin values proportionally
    for (int i = 0; i < width; ++i) {
      float startBin = i * binsPerPixel;
      float endBin = startBin + binsPerPixel;
      int startFloor = (int) Math.floor(startBin);
      int endFloor = (int) Math.floor(endBin);
      result[i] += data[startFloor] * (startBin - startFloor);
      for (int k = startFloor + 1; k < endFloor; ++k) {
        result[i] += data[k];
      }
      if (endFloor < width - 1) {
        result[i] += data[endFloor + 1] * (endFloor + 1 - endBin);
      }
    }
    return result;
  }

  private Path2D.Float getGammaMappingPath(int component) {
    if (cachedGammaMappingPath_ == null) {
      ComponentState state = componentStates_.get(component);
      Rectangle rect = getGraphRect();
      float loX = intensityFractionToGraphXPos((float) state.scalingMin_ / state.rangeMax_);
      float hiX = intensityFractionToGraphXPos((float) state.scalingMax_ / state.rangeMax_);
      int width = (int) Math.floor(hiX - loX);

      cachedGammaMappingPath_ = new Path2D.Float();
      cachedGammaMappingPath_.moveTo(0.0f, (float) rect.height);
      for (int x = 1; x < width - 1; ++x) {
        float xFrac = x / (hiX - loX);
        float yFrac = (float) Math.pow(xFrac, gamma_);
        cachedGammaMappingPath_.lineTo(x, (1.0f - yFrac) * rect.height);
      }
      cachedGammaMappingPath_.lineTo(hiX - loX, 0.0f);

      cachedGammaMappingPath_.transform(
          AffineTransform.getTranslateInstance(loX, frequencyFractionToGraphYPos(1.0f)));
    }
    return cachedGammaMappingPath_;
  }

  //
  // Mouse event handling
  //

  private void mouseClicked(MouseEvent e) {
    if (e.getClickCount() == 2) {
      if (isPointInScalingLabel(e.getPoint(), true)) {
        startScalingEdit(true);
      } else if (isPointInScalingLabel(e.getPoint(), false)) {
        startScalingEdit(false);
      } else if (isPointInScalingArea(e.getPoint(), true)) {
        jumpSetScaling(e.getPoint().x, true);
      } else if (isPointInScalingArea(e.getPoint(), false)) {
        jumpSetScaling(e.getPoint().x, false);
      } else if (isPointInGammaHandle(e.getPoint())) {
        setGamma(1.0);
        listeners_.fire().histogramGammaChanged(1.0);
      }
    }
  }

  private void mousePressed(MouseEvent e) {
    if (isPointInScalingHandle(e.getPoint(), true)) {
      handleBeingDragged_ = Handle.SCALING_MAX;
      startScalingHandleDrag(e.getPoint(), true);
    } else if (isPointInScalingHandle(e.getPoint(), false)) {
      handleBeingDragged_ = Handle.SCALING_MIN;
      startScalingHandleDrag(e.getPoint(), false);
    } else if (isPointInGammaHandle(e.getPoint())) {
      handleBeingDragged_ = Handle.GAMMA;
      startGammaHandleDrag(e.getPoint());
    }
  }

  private void mouseReleased(MouseEvent e) {
    try {
      switch (handleBeingDragged_) {
        case NONE:
          return;
        case SCALING_MIN:
          finishScalingHandleDrag(e.getPoint(), false);
          break;
        case SCALING_MAX:
          finishScalingHandleDrag(e.getPoint(), true);
          break;
        case GAMMA:
          finishGammaHandleDrag(e.getPoint());
          break;
        default:
          throw new AssertionError(handleBeingDragged_.name());
      }
    } finally {
      handleBeingDragged_ = Handle.NONE;
    }
  }

  private void mouseDragged(MouseEvent e) {
    switch (handleBeingDragged_) {
      case NONE:
        return;
      case SCALING_MIN:
        continueScalingHandleDrag(e.getPoint(), false);
        break;
      case SCALING_MAX:
        continueScalingHandleDrag(e.getPoint(), true);
        break;
      case GAMMA:
        continueGammaHandleDrag(e.getPoint());
        break;
      default:
        throw new AssertionError(handleBeingDragged_.name());
    }
  }

  private boolean isPointInScalingLabel(Point p, boolean top) {
    return getScalingLabelRect(selectedComponent_, top).contains(p);
  }

  private boolean isPointInScalingHandle(Point p, boolean top) {
    return getScalingHandleRect(selectedComponent_, top).contains(p);
  }

  private boolean isPointInScalingArea(Point p, boolean top) {
    return getScalingAreaRect(top).contains(p);
  }

  private boolean isPointInGammaHandle(Point p) {
    if (!allowGammaScaling_) {
      return false;
    }
    return getGammaHandleRect().contains(p);
  }

  private void startScalingEdit(final boolean top) {
    final ComponentState state = componentStates_.get(selectedComponent_);
    long intensity = top ? state.scalingMax_ : state.scalingMin_;
    long min = top ? state.scalingMin_ + 1 : 0;
    long max = top ? state.rangeMax_ : state.scalingMax_ - 1;
    // TODO spinner model fails if not min <= value <= max!!!
    final JSpinner scalingSpinner =
        new JSpinner(new SpinnerNumberModel((int) intensity, (int) min, (int) max, 1));
    scalingSpinner.addChangeListener(
        (ChangeEvent e) -> {
          long intensity1 = (Integer) scalingSpinner.getValue();
          if (top) {
            intensity1 = Math.max(state.scalingMin_ + 1, intensity1);
            setComponentScaling(selectedComponent_, state.scalingMin_, intensity1);
            listeners_.fire().histogramScalingMaxChanged(selectedComponent_, intensity1);
          } else {
            intensity1 = Math.min(state.scalingMax_ - 1, intensity1);
            setComponentScaling(selectedComponent_, intensity1, state.scalingMax_);
            listeners_.fire().histogramScalingMinChanged(selectedComponent_, intensity1);
          }
        });
    JPopupMenu popup = new JPopupMenu();
    popup.add(scalingSpinner);
    popup.validate();
    int x = (int) getScalingHandlePos(selectedComponent_, top) - popup.getPreferredSize().width / 2;
    int y = top ? -popup.getPreferredSize().height : getBounds().height;
    popup.show(this, x, y);
  }

  private void jumpSetScaling(int mouseX, boolean top) {
    ComponentState state = componentStates_.get(selectedComponent_);
    long intensity = (long) Math.round(graphXPosToIntensityFraction(mouseX) * state.rangeMax_);
    intensity = Math.max(0, Math.min(state.rangeMax_, intensity));
    if (top) {
      intensity = Math.max(state.scalingMin_ + 1, intensity);
      setComponentScaling(selectedComponent_, state.scalingMin_, intensity);
      listeners_.fire().histogramScalingMaxChanged(selectedComponent_, intensity);
    } else {
      intensity = Math.min(state.scalingMax_ - 1, intensity);
      setComponentScaling(selectedComponent_, intensity, state.scalingMax_);
      listeners_.fire().histogramScalingMinChanged(selectedComponent_, intensity);
    }
  }

  private void startScalingHandleDrag(Point startPoint, boolean top) {
    ComponentState state = componentStates_.get(selectedComponent_);
    scalingHandleDragOriginalValue_ = top ? state.scalingMax_ : state.scalingMin_;
    handleScalingHandleMovement(startPoint, top);
  }

  private void startGammaHandleDrag(Point startPoint) {
    gammaHandleDragOriginalValue_ = gamma_;
  }

  private void continueScalingHandleDrag(Point mousePosition, boolean top) {
    handleScalingHandleMovement(mousePosition, top);
  }

  private void continueGammaHandleDrag(Point mousePosition) {
    handleGammaHandleMovement(mousePosition);
  }

  private void finishScalingHandleDrag(Point mousePosition, boolean top) {
    handleScalingHandleMovement(mousePosition, top);
  }

  private void finishGammaHandleDrag(Point mousePosition) {
    handleGammaHandleMovement(mousePosition);
  }

  private void handleScalingHandleMovement(Point mousePosition, boolean top) {
    ComponentState state = componentStates_.get(selectedComponent_);
    long intensity;
    if (getValidDragRect().contains(mousePosition)) {
      float intensityFrac = graphXPosToIntensityFraction(mousePosition.x);
      intensity = (long) Math.round(intensityFrac * state.rangeMax_);
    } else {
      intensity = scalingHandleDragOriginalValue_;
    }
    intensity = Math.max(0, Math.min(state.rangeMax_, intensity));
    if (top) {
      intensity = Math.max(state.scalingMin_ + 1, intensity);
      setComponentScaling(selectedComponent_, state.scalingMin_, intensity);
      listeners_.fire().histogramScalingMaxChanged(selectedComponent_, intensity);
    } else {
      intensity = Math.min(state.scalingMax_ - 1, intensity);
      setComponentScaling(selectedComponent_, intensity, state.scalingMax_);
      listeners_.fire().histogramScalingMinChanged(selectedComponent_, intensity);
    }
  }

  private void handleGammaHandleMovement(Point mousePosition) {
    double gamma;
    if (getValidDragRect().contains(mousePosition)) {
      gamma = graphPosToGamma(mousePosition.x, mousePosition.y);
    } else {
      gamma = gammaHandleDragOriginalValue_;
    }
    setGamma(gamma);
    listeners_.fire().histogramGammaChanged(gamma);
  }

  private Rectangle getValidDragRect() {
    Rectangle frameBounds = getTopLevelAncestor().getBounds();
    Point frameLoc = frameBounds.getLocation();
    SwingUtilities.convertPointFromScreen(frameLoc, this);
    return new Rectangle(frameLoc.x, frameLoc.y, frameBounds.width, frameBounds.height);
  }
}
