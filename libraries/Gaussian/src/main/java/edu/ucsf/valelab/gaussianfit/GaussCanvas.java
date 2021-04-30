/*
 * Listener that can be attached to ImageJ Image windows to alter their behavior
 * 
Copyright (c) 2012, Regents of the University of California
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

The views and conclusions contained in the software and documentation are those
of the authors and should not be interpreted as representing official policies,
either expressed or implied, of the FreeBSD Project.
 */

package edu.ucsf.valelab.gaussianfit;

import edu.ucsf.valelab.gaussianfit.data.RowData;
import edu.ucsf.valelab.gaussianfit.datasettransformations.SpotDataFilter;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import java.awt.Dimension;
import java.awt.Rectangle;

/**
 * @author Nico Stuurman
 */
public class GaussCanvas extends ImageCanvas {

   RowData rowData_;
   int renderMethod_;
   ImagePlus originalIP_;
   ImageWindow iw_;
   double myMag_;
   double originalMag_;
   final int orImageWidth_;
   final int orImageHeight_;
   SpotDataFilter sf_;


   public GaussCanvas(ImagePlus sp, RowData rowData,
         int renderMode, double initialMag, SpotDataFilter sf) {
      super(sp);
      rowData_ = rowData;
      renderMethod_ = renderMode;
      originalIP_ = sp;
      originalMag_ = myMag_ = initialMag;
      sf_ = sf;
      orImageWidth_ = sp.getWidth();
      orImageHeight_ = sp.getHeight();
   }
/*
   public void setImageWindow(ImageWindow iw) {
      iw_ = iw;
   }
   */

   /**
    * Transforms the sourceRct Rectangle into a Rectangle in nm coordinates
    *
    * @param magnification
    * @return
    */
   Rectangle sourceRectToNmRect(double magnification) {
      Rectangle resRect = new Rectangle();
      resRect.x = (int) (srcRect.x * rowData_.pixelSizeNm_ / (originalMag_ * magnification));
      resRect.y = (int) (srcRect.y * rowData_.pixelSizeNm_ / (originalMag_ * magnification));
      resRect.width = (int) (srcRect.width * rowData_.pixelSizeNm_ / (originalMag_
            * magnification));
      resRect.height = (int) (srcRect.height * rowData_.pixelSizeNm_ / (originalMag_
            * magnification));

      return resRect;
   }

   /**
    * Transforms the sourceRct Rectangle into a Rectangle in nm coordinates
    *
    * @param magnification
    * @return
    */
   Rectangle sourceRectToRenderRect(double magnification) {
      Rectangle resRect = new Rectangle();
      resRect.x = (int) (srcRect.x / (originalMag_ * magnification));
      resRect.y = (int) (srcRect.y * rowData_.pixelSizeNm_ / (originalMag_ * magnification));
      resRect.width = (int) (srcRect.width * rowData_.pixelSizeNm_ / (originalMag_
            * magnification));
      resRect.height = (int) (srcRect.height * rowData_.pixelSizeNm_ / (originalMag_
            * magnification));

      return resRect;
   }

   @Override
   public void zoomIn(int sx, int sy) {
      if (magnification >= 32) {
         return;
      }
      double newMag = getHigherZoomLevel(magnification);
      int newWidth = (int) (imageWidth * newMag);
      int newHeight = (int) (imageHeight * newMag);
      Dimension newSize = canEnlarge(newWidth, newHeight);
      if (newSize != null) {
         setDrawingSize(newSize.width, newSize.height);
         if (newSize.width != newWidth || newSize.height != newHeight) {
            adjustSourceRect(newMag, sx, sy);
         } else {
            setMagnification(newMag);
         }
         imp.getWindow().pack();
      } else {
         /*
         Rectangle r = getRect(newMag, sx, sy);
         ImageProcessor ipTmp = ImageRenderer.renderData(rowData_, renderMethod_,
                    newMag, r, sf_);

         imp.setProcessor(ipTmp);
         DisplayUtils.AutoStretch(imp);
         //DisplayUtils.SetCalibration(imp, (float) (rowData.pixelSizeNm_ / mag));
         */
         adjustSourceRect(newMag, sx, sy);
      }

      repaint();
      //if (srcRect.width<imageWidth || srcRect.height<imageHeight)
      //	resetMaxBounds();
   }

   /*

    public void paint(Graphics g) {
		Roi roi = imp.getRoi();
		if (roi!=null) {
			//if (roi!=null) 
          //  roi.updatePaste();
			//if (!IJ.isMacOSX() && imageWidth!=0) {
			//	paintDoubleBuffered(g);
			//	return;
			//}
		}
		try {
			      if (imageUpdated) {
            imageUpdated = false;
            imp.updateImage();
         }
         //setInterpolation(g, Prefs.interpolateScaledImages);
         if (magnification > 1.0) {
            double f = magnification * originalMag_;
            Rectangle magRect = new Rectangle((int) (f * srcRect.x), (int) (f * srcRect.y),
                    imp.getWidth(), imp.getHeight());
            ImageProcessor ipTmp = ImageRenderer.renderData(rowData_, renderMethod_,
                    f, magRect, sf_);
            imp.setProcessor(ipTmp);
            
            Image img = imp.getImage();
            g.drawImage(img, 0, 0, magRect.width, magRect.height, null);
         } else {
            Image img = imp.getImage();
            if (img != null) {
               g.drawImage(img, 0, 0, (int) (srcRect.width * magnification), (int) (srcRect.height * magnification),
                       srcRect.x, srcRect.y, srcRect.x + srcRect.width, srcRect.y + srcRect.height, null);
            }
         }
			//if (overlay!=null) drawOverlay(g);
			//if (showAllROIs) drawAllROIs(g);
			//if (roi!=null) drawRoi(roi, g);
			//if (srcRect.width<imageWidth || srcRect.height<imageHeight)
			//	super.drawZoomIndicator(g);
			//if (IJ.debugMode) showFrameRate(g);
		}
		catch(OutOfMemoryError e) {IJ.outOfMemory("Paint");}
    }
*/

   Rectangle getRect(double newMag, int x, int y) {
      //IJ.log("adjustSourceRect1: "+newMag+" "+dstWidth+"  "+dstHeight);
      int w = (int) Math.round(dstWidth / newMag);
      if (w * newMag < dstWidth) {
         w++;
      }
      int h = (int) Math.round(dstHeight / newMag);
      if (h * newMag < dstHeight) {
         h++;
      }
      x = offScreenX(x);
      y = offScreenY(y);
      Rectangle r = new Rectangle(x - w / 2, y - h / 2, w, h);
      if (r.x < 0) {
         r.x = 0;
      }
      if (r.y < 0) {
         r.y = 0;
      }
      if (r.x + w > imageWidth) {
         r.x = imageWidth - w;
      }
      if (r.y + h > imageHeight) {
         r.y = imageHeight - h;
      }
      r.x *= newMag;
      r.y *= newMag;
      r.width *= newMag;
      r.height *= newMag;
      return r;
   }

   void adjustSourceRect(double newMag, int x, int y) {
      //IJ.log("adjustSourceRect1: "+newMag+" "+dstWidth+"  "+dstHeight);
      int w = (int) Math.round(dstWidth / newMag);
      if (w * newMag < dstWidth) {
         w++;
      }
      int h = (int) Math.round(dstHeight / newMag);
      if (h * newMag < dstHeight) {
         h++;
      }
      x = offScreenX(x);
      y = offScreenY(y);
      Rectangle r = new Rectangle(x - w / 2, y - h / 2, w, h);
      if (r.x < 0) {
         r.x = 0;
      }
      if (r.y < 0) {
         r.y = 0;
      }
      if (r.x + w > imageWidth) {
         r.x = imageWidth - w;
      }
      if (r.y + h > imageHeight) {
         r.y = imageHeight - h;
      }
      srcRect = r;
      setMagnification(newMag);
      //IJ.log("adjustSourceRect2: "+srcRect+" "+dstWidth+"  "+dstHeight);
   }
   
/*
   @Override
   public void mouseReleased(MouseEvent me) {
      if (Toolbar.getToolId() != Toolbar.MAGNIFIER) {
         super.mouseReleased(me);
         return;
      }
      
      // TODO: override zoomin, zoomout functions instead
      double mag = this.getMagnification() * myMag_;
      myMag_ = mag;
      Rectangle vis = this.getBounds();
      Dimension d = this.getSize();
      Rectangle vis2 = this.getBounds();
      Point a = this.getLocation();
      Point c = this.getCursorLoc();

      System.out.println("mag : " + mag);

      if (mag > 1) {
         Rectangle roi = null;
         if (d.width >= (mag * rowData_.width_)) {
            roi = new Rectangle(0, 0, (int) (mag * rowData_.width_),
                    (int) (mag * rowData_.height_));
         } else {
            int xCenter = (int) (c.x * mag);
            int yCenter = (int) (c.y * mag);
            int halfWidth = d.width / 2;
            int halfHeight = d.height / 2;
            if (xCenter - halfWidth < 0) {
               xCenter = halfWidth;
            }
            if (xCenter + halfWidth > (int) (mag * rowData_.width_)) {
               xCenter = (int) (mag * rowData_.width_) - halfWidth;
            }
            if (yCenter - halfHeight < 0) {
               yCenter = halfHeight;
            }
            if (yCenter + halfHeight > (int) (mag * rowData_.height_)) {
               yCenter = (int) (mag * rowData_.height_) - halfHeight;
            }

            roi = new Rectangle(xCenter - halfWidth, yCenter - halfHeight,
                    d.width, d.height);

         }
         //ImageRenderer.renderData(iw_, rowData_, renderMethod_, mag, roi, sf_);

      } else {
         //iw_.setImage(originalIP_);
         // myMag_ = originalMag_;
      }

   }

   
*/

}
