package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;

import ij.gui.ImageCanvas;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyAdapter;
import java.awt.Graphics;

import javax.swing.border.TitledBorder;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DisplaySettings;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.Metadata;
import org.micromanager.api.display.OverlayPanel;

/**
 * This overlay draws the timestamps of the currently-displayed images.
 */
public class TimestampOverlayPanel extends OverlayPanel {
   private Datastore store_;
   private EventBus displayBus_;

   private JCheckBox shouldDraw_;
   private JCheckBox amMultiChannel_;
   private JTextField xOffset_;
   private JTextField yOffset_;
   private JComboBox position_;
   private JComboBox color_;

   public TimestampOverlayPanel(Datastore store) {
      setBorder(new TitledBorder("Timestamp display"));
      setLayout(new MigLayout("flowx"));

      ActionListener redrawListener = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            redraw();
         }
      };

      shouldDraw_ = new JCheckBox("Draw timestamps");
      shouldDraw_.addActionListener(redrawListener);
      add(shouldDraw_);

      add(new JLabel("Position: "), "split 2, flowy");
      position_ = new JComboBox(new String[] {
            "Upper left", "Upper right", "Lower right", "Lower left"});
      position_.addActionListener(redrawListener);
      add(position_, "wrap");

      add(new JLabel("X offset: "), "split 2");
      xOffset_ = new JTextField("0", 3);
      xOffset_.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent event) {
            redraw();
         }
      });
      add(xOffset_);

      add(new JLabel("Y offset: "), "split 2");
      yOffset_ = new JTextField("0", 3);
      yOffset_.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent event) {
            redraw();
         }
      });
      add(yOffset_, "wrap");

      amMultiChannel_ = new JCheckBox("Per-channel");
      amMultiChannel_.setToolTipText("Draw one timestamp per channel in the display.");
      amMultiChannel_.addActionListener(redrawListener);
      add(amMultiChannel_);

      add(new JLabel("Text color: "), "split 2, flowy");
      color_ = new JComboBox(new String[] {
            "White", "Black", "Channel color"});
      color_.addActionListener(redrawListener);
      add(color_);
   }

   public void setBus(EventBus bus) {
      displayBus_ = bus;
   }

   private void redraw() {
      if (displayBus_ != null) {
         displayBus_.post(new DefaultRequestToDrawEvent());
      }
   }

   /**
    * We draw one row of text per channel in multi-channel mode, or just the
    * provided image's timestamp in normal mode.
    */
   @Override
   public void drawOverlay(Graphics g, Datastore store, Image image,
         ImageCanvas canvas) {
      if (!shouldDraw_.isSelected()) {
         return;
      }
      if (amMultiChannel_.isSelected()) {
         for (int i = 0; i < store.getMaxIndex("channel") + 1; ++i) {
            Coords channelCoords = image.getCoords().copy().position("channel", i).build();
            Image channelImage = store.getImage(channelCoords);
            if (channelImage != null) {
               drawImageTimestamp(g, store, canvas, channelImage, i);
            }
         }
      }
      else {
         drawImageTimestamp(g, store, canvas, image, 0);
      }
   }

   /**
    * Draw a single image's timestamp.
    */
   private void drawImageTimestamp(Graphics g, Datastore store,
         ImageCanvas canvas, Image image, int position) {
      // Default to black.
      Color textColor = Color.BLACK;
      String textMode = (String) color_.getSelectedItem();
      if (textMode.equals("White")) {
         textColor = Color.WHITE;
      }
      else if (textMode.equals("Black")) {
         // Redundant with the default, but keeps code clear.
         textColor = Color.BLACK;
      }
      else if (textMode.equals("Channel color")) {
         DisplaySettings settings = store.getDisplaySettings();
         Color[] colors = settings.getChannelColors();
         int channel = image.getCoords().getPositionAt("channel");
         if (colors != null && colors.length >= channel) {
            textColor = colors[channel];
         }
      }
      g.setColor(textColor);

      // This code is copied from the ScaleBarOverlayPanel, and then slightly
      // adapted to our different Y-positioning needs.
      int xOffset = 0, yOffset = 0;
      try {
         xOffset = Integer.parseInt(xOffset_.getText());
         yOffset = Integer.parseInt(yOffset_.getText());
      }
      catch (NumberFormatException e) {} // Just use what we have.
      yOffset += (position + 1) * 13;

      String location = (String) position_.getSelectedItem();
      Dimension canvasSize = canvas.getPreferredSize();
      if (location.equals("Upper right") || location.equals("Lower right")) {
         xOffset = canvasSize.width - xOffset - 80;
      }
      if (location.equals("Lower left") || location.equals("Lower right")) {
         yOffset = canvasSize.height - yOffset - 13;
         // Invert the position-based offset from our default value.
         yOffset -= (position + 1) * 26;
      }

      Metadata metadata = image.getMetadata();
      // Try various fallback options for the timestamp.
      String text = metadata.getReceivedTime();
      if (text == null) {
         Double elapsedTime = metadata.getElapsedTimeMs();
         if (elapsedTime != null) {
            text = String.format("T+%.2fms", elapsedTime);
         }
         else {
            text = "No timestamp";
         }
      }
      g.drawString(text, xOffset, yOffset);
   }
}
