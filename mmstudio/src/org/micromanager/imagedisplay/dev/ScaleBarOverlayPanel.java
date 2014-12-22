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

import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.Image;
import org.micromanager.api.display.DisplaySettings;
import org.micromanager.api.display.DisplayWindow;
import org.micromanager.api.display.OverlayPanel;


/**
 * This class provides a GUI for drawing a scale bar.
 */
public class ScaleBarOverlayPanel extends OverlayPanel {
   private static Color[] COLORS = new Color[] {
      Color.black, Color.white, Color.gray, Color.yellow, Color.orange,
         Color.red, Color.magenta, Color.blue, Color.cyan, Color.green};
   // Would you believe this isn't built-in to the Color module? Naturally
   // this list of names must match the list of colors, above.
   private static String[] COLORNAMES = new String[] {
      "Black", "White", "Gray", "Yellow", "Orange", "Red", "Magenta",
         "Blue", "Cyan", "Green"
   };

   private EventBus displayBus_;

   private JCheckBox shouldDraw_;
   private JCheckBox isBarFilled_;
   private JComboBox color_;
   private JTextField xOffset_;
   private JTextField yOffset_;
   private JComboBox position_;
   
   public ScaleBarOverlayPanel(DisplayWindow display) {
      setBorder(new TitledBorder("Scale bar"));
      setLayout(new MigLayout("flowy"));
      DisplaySettings settings = display.getDisplaySettings();

      ActionListener redrawListener = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
               redraw();
            }
      };

      shouldDraw_ = new JCheckBox("Draw scale bar");
      shouldDraw_.addActionListener(redrawListener);
      if (settings.getShouldShowScaleBar() != null) {
         shouldDraw_.setSelected(settings.getShouldShowScaleBar());
      }
      add(shouldDraw_);
      
      add(new JLabel("Color: "));
      color_ = new JComboBox(COLORNAMES);
      color_.addActionListener(redrawListener);
      if (settings.getScaleBarColorIndex() != null) {
         color_.setSelectedIndex(settings.getScaleBarColorIndex());
      }
      add(color_);

      add(new JLabel("X offset: "), "split 2, flowx");
      xOffset_ = new JTextField("0", 3);
      xOffset_.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent event) {
            redraw();
         }
      });
      if (settings.getScaleBarOffsetX() != null) {
         xOffset_.setText(String.valueOf(settings.getScaleBarOffsetX()));
      }
      add(xOffset_, "wrap");

      isBarFilled_ = new JCheckBox("Solid scale bar");
      isBarFilled_.addActionListener(redrawListener);
      if (settings.getScaleBarIsFilled() != null) {
         isBarFilled_.setSelected(settings.getScaleBarIsFilled());
      }
      add(isBarFilled_);

      add(new JLabel("Position: "));
      position_ = new JComboBox(new String[] {
            "Upper left", "Upper right", "Lower right", "Lower left"});
      position_.addActionListener(redrawListener);
      if (settings.getScaleBarLocationIndex() != null) {
         position_.setSelectedIndex(settings.getScaleBarLocationIndex());
      }
      add(position_);

      add(new JLabel("Y offset: "), "split 2, flowx");
      yOffset_ = new JTextField("0", 3);
      yOffset_.addKeyListener(new KeyAdapter() {
         @Override
         public void keyPressed(KeyEvent event) {
            redraw();
         }
      });
      if (settings.getScaleBarOffsetY() != null) {
         yOffset_.setText(String.valueOf(settings.getScaleBarOffsetY()));
      }
      add(yOffset_);
   }

   public void setBus(EventBus bus) {
      displayBus_ = bus;
   }

   private void redraw() {
      if (displayBus_ != null) {
         displayBus_.post(new DefaultRequestToDrawEvent());
      }
   }

   public void drawOverlay(Graphics g, DisplayWindow display, Image image, ImageCanvas canvas) {
      if (!shouldDraw_.isSelected()) {
         return;
      }
      Double pixelSize = image.getMetadata().getPixelSizeUm();
      if (pixelSize == null) {
         return;
      }

      double width = pixelSize * 80 / canvas.getMagnification();
      g.setColor(COLORS[color_.getSelectedIndex()]);
      int xOffset = 0, yOffset = 0;
      try {
         xOffset = Integer.parseInt(xOffset_.getText());
         yOffset = Integer.parseInt(yOffset_.getText());
      }
      catch (NumberFormatException e) {} // Just use what we have.

      String position = (String) position_.getSelectedItem();
      Dimension canvasSize = canvas.getPreferredSize();
      if (position.equals("Upper right") || position.equals("Lower right")) {
         xOffset = canvasSize.width - xOffset - 80;
      }
      if (position.equals("Lower left") || position.equals("Lower right")) {
         yOffset = canvasSize.height - yOffset - 13;
      }

      g.drawString(String.format("Scale: %.2fum", width), xOffset, yOffset);
      if (isBarFilled_.isSelected()) {
         g.fillRect(xOffset, yOffset + 6, 80, 5);
      }
      else {
         g.drawRect(xOffset, yOffset + 6, 80, 5);
      }
   }
}
