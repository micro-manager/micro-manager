///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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
//
package org.micromanager.multiresviewer;

import org.micromanager.ndviewer.internal.gui.DataViewCoords;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import javax.swing.JPanel;
import org.micromanager.ndviewer.overlay.Overlay;
import org.micromanager.ndviewer.overlay.Roi;
import org.micromanager.ndviewer.overlay.TextRoi;

/**
 * Class that encapsulates calculation of overlays for DisplayPlus
 */
public class BaseOverlayer {

   private static final Color LIGHT_BLUE = new Color(200, 200, 255);

   private ExecutorService taskExecutor_;
   private Future currentTask_;
   private NDViewer display_;
   private volatile boolean showScalebar_ = false;

   public BaseOverlayer(NDViewer display) {
      display_ = display;
      taskExecutor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {

         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, "Overalyer task thread");
         }
      });

   }

   public void setShowScaleBar(boolean show) {
      showScalebar_ = show;
   }

   public void shutdown() {
      taskExecutor_.shutdownNow();
   }

   //always try to cancel the previous task, assuming it is being replaced with a more current one
   public synchronized void redrawOverlay(DataViewCoords viewCoords) {
      if (currentTask_ != null && !currentTask_.isDone()) {
         //cancel current surface calculation--this call does not block until complete
         currentTask_.cancel(true);
      }
      currentTask_ = taskExecutor_.submit(new Runnable() {

         @Override
         public void run() {
            Overlay defaultOverlay = createDefaultOverlay(viewCoords);

            display_.setOverlay(defaultOverlay);

            //TODO: now add in fancy overlay stuff
         }
      });
   }

   private void addScaleBar(Overlay overlay, DataViewCoords viewCoords) {
      int fontSize = 18;
      int scaleBarWidth = 100;
      Font font = new Font("Arial", Font.BOLD, fontSize);
      float textHeight = 0;
      float textWidth = 0;

      double pixelSize = display_.getPixelSize();
      pixelSize /= viewCoords.getDisplayToFullScaleFactor();
      double barSize = pixelSize * scaleBarWidth;
      String text = ((int) Math.round(barSize)) + " \u00B5" + "m";

      JPanel canvas = display_.getCanvasJPanel();
      textHeight = Math.max(textHeight, canvas.getGraphics().getFontMetrics(font
      ).getLineMetrics(text, canvas.getGraphics()).getHeight());
      textWidth = Math.max(textWidth, canvas.getGraphics().getFontMetrics().stringWidth(text));

      TextRoi troi = new TextRoi(canvas.getBounds().width - 100,
              viewCoords.getDisplayImageSize().y - 70, text, font);
      troi.setStrokeColor(Color.white);
      overlay.add(troi);

      Roi outline = new Roi(canvas.getBounds().width - 125,
              viewCoords.getDisplayImageSize().y - 70 + textHeight + 8, scaleBarWidth, 15);
      outline.setFillColor(Color.white);
      overlay.add(outline);
   }

   private void addTextBox(String[] text, Overlay overlay) {
      int fontSize = 12;
      Font font = new Font("Arial", Font.BOLD, fontSize);
      float lineHeight = 0;
      float textWidth = 0;
      JPanel canvas = display_.getCanvasJPanel();
      for (String line : text) {
         lineHeight = Math.max(lineHeight, canvas.getGraphics().getFontMetrics(font).getLineMetrics(line, canvas.getGraphics()).getHeight());
         textWidth = Math.max(textWidth, canvas.getGraphics().getFontMetrics().stringWidth(line));
      }
      float textHeight = lineHeight * text.length;
      //10 pixel border 
      int border = 10;
      int roiWidth = (int) (textWidth + 2 * border);
      int roiHeight = (int) (textHeight + 2 * border);
      Roi rectangle = new Roi(canvas.getBounds().width / 2 - roiWidth / 2,
              canvas.getBounds().height / 2 - roiHeight / 2, roiWidth, roiHeight);
      rectangle.setStrokeWidth(3f);
      rectangle.setFillColor(LIGHT_BLUE);
      overlay.add(rectangle);

      for (int i = 0; i < text.length; i++) {
         TextRoi troi = new TextRoi(canvas.getBounds().width / 2 - roiWidth / 2 + border,
                 canvas.getBounds().height / 2 - roiHeight / 2 + border + lineHeight * i, text[i], font);
         troi.setStrokeColor(Color.black);
         overlay.add(troi);
      }
   }

 
   public Overlay createDefaultOverlay(DataViewCoords viewCoords) {
      Overlay overlay = new Overlay();
      if (display_.isImageXYBounded()) {
         drawZoomIndicator(overlay, viewCoords);
      }
      if (showScalebar_) {
         addScaleBar(overlay, viewCoords);
      }
      return overlay;
   }

   private void drawZoomIndicator(Overlay overlay, DataViewCoords viewCoords) {
      int outerWidth = 100;
      long fullResHeight = viewCoords.yMax_ - viewCoords.yMin_;
      long fullResWidth = viewCoords.xMax_ - viewCoords.xMin_;

      Point2D.Double sourceDataSize = viewCoords.getSourceDataSize();
      int outerHeight = (int) ((double) fullResHeight / (double) fullResWidth * outerWidth);
      //draw outer rectangle representing full image
      Roi outerRect = new Roi(10, 10, outerWidth, outerHeight);
      outerRect.setStrokeColor(new Color(255, 0, 255)); //magenta
      int innerX = (int) Math.round(((double) outerWidth / (double) fullResWidth) * (viewCoords.getViewOffset().x - viewCoords.xMin_));
      int innerY = (int) Math.round(((double) outerHeight / (double) fullResHeight) * (viewCoords.getViewOffset().y - viewCoords.yMin_));
      //outer width * percentage of width of full images that is shown
      int innerWidth = (int) (outerWidth * ((double) sourceDataSize.x / fullResWidth));
      int innerHeight = (int) (outerHeight * ((double) sourceDataSize.y / fullResHeight));
      Roi innerRect = new Roi(10 + innerX, 10 + innerY, innerWidth, innerHeight);
      innerRect.setStrokeColor(new Color(255, 0, 255));
      if (outerWidth != innerWidth || outerHeight != innerHeight) { //dont draw if fully zoomed out
         overlay.add(outerRect);
         overlay.add(innerRect);
      }
   }

}
