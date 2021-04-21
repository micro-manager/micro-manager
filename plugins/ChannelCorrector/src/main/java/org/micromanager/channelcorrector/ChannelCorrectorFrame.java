///////////////////////////////////////////////////////////////////////////////
// FILE:          ChannelCorrectorFrame.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     ChannelCorrector plugin
// -----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    Regents of the University of California 2020
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

package org.micromanager.channelcorrector;

import com.google.common.eventbus.Subscribe;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.channelcorrector.utils.ImageAffineTransform;
import org.micromanager.channelcorrector.utils.ImageAffineTransformException;
import org.micromanager.display.DataViewer;
import org.micromanager.display.internal.event.DataViewerDidBecomeActiveEvent;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.propertymap.MutablePropertyMapView;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JSeparator;
import javax.swing.WindowConstants;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** @author nico */
public class ChannelCorrectorFrame extends JFrame {
  private final Studio studio_;
  private final MutablePropertyMapView settings_;
  private final List<ChannelCorrectorPanel> channelCorrectorPanels_;
  private final ExecutorService executor_ = Executors.newSingleThreadExecutor();
  private DataViewer dataViewer_;

  private static final String USE_ALL_POS_KEY = "UseAllPositions";

  public ChannelCorrectorFrame(Studio studio) {
    studio_ = studio;
    channelCorrectorPanels_ = new ArrayList<>();
    settings_ = studio_.profile().getSettings(this.getClass());

    super.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    super.setLayout(new MigLayout("flowx, fill, insets 8"));
    super.setTitle(ChannelCorrector.MENUNAME);
    super.setIconImage(
        Toolkit.getDefaultToolkit()
            .getImage(getClass().getResource("/org/micromanager/icons/microscope.gif")));

    JButton applyButton = new JButton("Apply");
    applyButton.addActionListener(
        (ActionEvent ae) ->
            executor_.submit(
                new Runnable() {
                  @Override
                  public void run() {
                    try {
                      apply();
                    } catch (IOException | ImageAffineTransformException e) {
                      studio_.logs().showError(e.getMessage());
                    }
                  }
                }));
    JCheckBox useAllPositions = new JCheckBox("all positions");
    useAllPositions.addActionListener(
        (ActionEvent ae) -> {
          for (ChannelCorrectorPanel ccp : channelCorrectorPanels_) {
            ccp.setUseAllPositions(useAllPositions.isSelected());
            settings_.putBoolean(USE_ALL_POS_KEY, useAllPositions.isSelected());
          }
        });
    useAllPositions.setSelected(settings_.getBoolean(USE_ALL_POS_KEY, false));
    super.add(applyButton, "span 4, split 2, center, wmin button");
    super.add(useAllPositions, "right, wrap");
    super.add(new JSeparator(), "span 4, growx, wrap");

    if (studio_.displays().getActiveDataViewer() != null) {
      dataViewer_ = studio_.displays().getActiveDataViewer();
      redrawChannelPanels(dataViewer_);
    }

    super.pack();
    super.setVisible(true);
  }

  @Subscribe
  public void onDataViewerBecameActive(DataViewerDidBecomeActiveEvent dvEvent) {
    DataViewer dataViewer = dvEvent.getDataViewer();
    if (dataViewer != null) {
      if (dataViewer != dataViewer_) {
        dataViewer_ = dataViewer;
        redrawChannelPanels(dataViewer_);
      }
    }
  }

  @Subscribe
  public void onDataViewerWillClose(DataViewerWillCloseEvent dvEvent) {
    DataViewer dataViewer = dvEvent.getDataViewer();
    if (dataViewer != null) {
      if (dataViewer == dataViewer_) {
        for (ChannelCorrectorPanel ccp : channelCorrectorPanels_) {
          ccp.updateValues();
          super.remove(ccp);
        }
        channelCorrectorPanels_.clear();
        super.pack();
      }
    }
  }

  private void redrawChannelPanels(DataViewer dataViewer) {
    for (ChannelCorrectorPanel ccp : channelCorrectorPanels_) {
      ccp.updateValues();
      super.remove(ccp);
    }
    channelCorrectorPanels_.clear();

    int nrCh = dataViewer.getDataProvider().getSummaryMetadata().getChannelNameList().size();

    for (int row = 1; row < nrCh; row++) {
      ChannelCorrectorPanel ccp =
          new ChannelCorrectorPanel(
              studio_, dataViewer, row, executor_, settings_.getBoolean(USE_ALL_POS_KEY, false));
      super.add(ccp, "span 4, wrap");
      channelCorrectorPanels_.add(ccp);
    }
    super.pack();
  }

  public void dispose() {
    for (ChannelCorrectorPanel ccp : channelCorrectorPanels_) {
      ccp.updateValues();
    }
    super.dispose();
  }

  public void apply() throws IOException, ImageAffineTransformException {
    final String dataViewerName = dataViewer_.getName();
    studio_.alerts().postAlert("ChannelCorrector", this.getClass(), "Correcting " + dataViewerName);
    ArrayList<AffineTransform> affineTransforms = new ArrayList<>(channelCorrectorPanels_.size());
    for (ChannelCorrectorPanel ccp : channelCorrectorPanels_) {
      affineTransforms.add(ccp.getAffineTransform());
    }
    // Note that types other than Nearest Neighbor lead to strange pixel values
    ImageAffineTransform iat =
        new ImageAffineTransform(
            studio_, dataViewer_, affineTransforms, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
    iat.apply(settings_.getBoolean(USE_ALL_POS_KEY, false));
    studio_
        .alerts()
        .postAlert("ChannelCorrector", this.getClass(), "Finished correcting " + dataViewerName);
  }
}
