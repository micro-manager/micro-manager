/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.imaging100x.twophoton;

import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import java.awt.Point;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.VirtualAcquisitionDisplay;
import org.micromanager.api.ImageCache;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.*;

/**
 *
 * @author Henry
 */
public class AcquisitionStitcher implements ImageFocusListener{
   
   private static final String ACQ_NAME = "Stitched";
   
   private ScriptInterface gui_;
   private ImageCache cache_;
   private VirtualAcquisitionDisplay display_;

   private boolean invertX_, invertY_, swapXandY_;
   private int overlapX_, overlapY_;
   private int gridWidth_, gridHeight_;
   private int oldImageWidth_, oldImageHeight_, newImageWidth_ = -1, newImageHeight_ = -1;
   private Comparator gridSorter_;

   
   public AcquisitionStitcher() {
      gui_ = MMStudioMainFrame.getInstance();
      GUIUtils.registerImageFocusListener(this);
   }
   
   public void setStitchParameters(boolean invX, boolean invY, boolean swap, int overlapX, int overlapY) {
      invertX_ = invX;
      invertY_ = invY;
      swapXandY_ = swap;
      overlapX_ = overlapX;
      overlapY_ = overlapY;
   }

   private Object stitchBatch(LinkedList<TaggedImage> batch) {    
      if (gridSorter_ == null) {
         gridSorter_ = makeGridSorter();
      }
      Collections.sort(batch, gridSorter_);
      if (batch.getFirst().pix instanceof short[]) {
         JOptionPane.showMessageDialog(null, "Henry hasn't added support for stitching of images with more than "
                 + "8 bits per pixel because no one has needed it yet. Go talk to him and he will add this");
      }

      if (overlapX_ == 0 && overlapY_ == 0) {
         return stitchPixelsNoOverlap(batch);
      }
      return stitchPixels(batch);
   }
   
   public void createStitchedFromCurrentFrame() {
      try {
         int frameIndex = MDUtils.getFrameIndex(cache_.getLastImageTags());
         int sliceIndex = MDUtils.getSliceIndex(cache_.getLastImageTags());
         int channelIndex = MDUtils.getChannelIndex(cache_.getLastImageTags());
         int positionIndex = MDUtils.getPositionIndex(cache_.getLastImageTags());
         if (frameIndex == 0 && sliceIndex + 1 == display_.getNumSlices() && positionIndex + 1 == display_.getNumPositions()
                 && channelIndex + 1 == display_.getNumChannels()) {
            //first time point complete, do nothing
         } else if (frameIndex == 0) {
            //first time point incomplete, return
            return;
         } else {
            //use last complete time point
            frameIndex--;
         }

         Point location = null;
         double zoom = 1;
         if (gui_.acquisitionExists(ACQ_NAME)) {
            location = gui_.getAcquisition(ACQ_NAME).getAcquisitionWindow().getImagePlus().getWindow().getLocation();
            zoom = gui_.getAcquisition(ACQ_NAME).getAcquisitionWindow().getImagePlus().getWindow().getCanvas().getMagnification();
            gui_.getAcquisition(ACQ_NAME).closeImageWindow();
         }
         gui_.openAcquisition(ACQ_NAME, "", 1, cache_.getNumDisplayChannels(), display_.getNumSlices(), true, false);



         for (int slice = 0; slice < display_.getNumSlices(); slice++) {
            for (int channel = 0; channel < cache_.getNumDisplayChannels(); channel++) {
               LinkedList<TaggedImage> batch = new LinkedList<TaggedImage>();
               for (int position = 0; position < display_.getNumPositions(); position++) {
                  batch.add(cache_.getImage(channel, slice, frameIndex, position));
               }
               if (channel == 0 && slice == 0) {
                  try {
                     calcGridDimensions(batch);
                     
                     gui_.initializeAcquisition(ACQ_NAME, newImageWidth_, newImageHeight_, 1);
                     gui_.getAcquisition(ACQ_NAME).getAcquisitionWindow().promptToSave(false);

                     try {
                        if (location != null) {
                           ImageWindow win = gui_.getAcquisition(ACQ_NAME).getAcquisitionWindow().getImagePlus().getWindow();
                           win.setLocation(location);

                           //Apply same 
                           ImageCanvas canvas = win.getCanvas();
                           if (zoom < canvas.getMagnification()) {
                              while (zoom < canvas.getMagnification()) {
                                 canvas.zoomOut(canvas.getWidth() / 2, canvas.getHeight() / 2);
                              }
                           } else if (zoom > canvas.getMagnification()) {
                              while (zoom > canvas.getMagnification()) {
                                 canvas.zoomIn(canvas.getWidth() / 2, canvas.getHeight() / 2);
                              }
                           }        
                        }
                     } catch (Exception e) {
                        ReportingUtils.showError("Couldnt re use stitched window settings");
                     }

                  } catch (JSONException ex) {
                     ReportingUtils.showError("Couldn't calc grid dimensions");
                  }
               }
               gui_.getAcquisition(ACQ_NAME).insertImage(stitchBatch(batch), 0, channel, slice);

            }
         }
         GUIUtils.invokeAndWait(new Runnable() {

            @Override
            public void run() {
               for (int c = 0; c < cache_.getNumDisplayChannels(); c++) {
                  try {
                     gui_.getAcquisition(ACQ_NAME).getAcquisitionWindow().setChannelContrast(c, cache_.getChannelMin(c),
                             cache_.getChannelMax(c), cache_.getChannelGamma(c));
                     gui_.getAcquisition(ACQ_NAME).setChannelColor(c, cache_.getChannelColor(c).getRGB());
                     gui_.getAcquisition(ACQ_NAME).setChannelName(c, cache_.getChannelName(c));
                  } catch (MMScriptException ex) {
                     
                  }
               }
            }
         });


         }  catch (Exception e) {
         ReportingUtils.showError(e);
      }

   }

   @Override
   public void focusReceived(ImageWindow iw) {
      VirtualAcquisitionDisplay vad = VirtualAcquisitionDisplay.getDisplay(iw.getImagePlus());
      if (vad != null && vad.getNumPositions() > 1) {
         ImageCache cache = vad.getImageCache();
         if (!cache.isFinished() ) {
            //this is valid for stitching
            cache_ = cache;
            display_ = vad;
         }
      }
   }
   
   private int getTilePixel(byte[] pixels, int x, int y) {
      return pixels[x + oldImageWidth_ * y] & 0xff;
   }

    private byte[] stitchPixels(LinkedList<TaggedImage> batch) {
      byte[] newPixels = new byte[newImageWidth_ * newImageHeight_];
      for (int y = 0; y < newImageHeight_; y++) {
         //Grid width and grid height are indices of larger numbered tile
         for (int x = 0; x < newImageWidth_; x++) {
            int gridX = Math.min(x / (oldImageWidth_ - overlapX_), gridWidth_ - 1);
            int gridY = Math.min(y / (oldImageHeight_ - overlapY_), gridHeight_ - 1);

            boolean xOverlap = x % (oldImageWidth_ - overlapX_) < overlapX_;
            boolean yOverlap = y % (oldImageHeight_ - overlapY_) < overlapY_;

            //Coordinates of pixels of interest in tiles
            int tileX = (x % (oldImageWidth_ - overlapX_)) % oldImageWidth_;
            int tileY = y % (oldImageHeight_ - overlapY_);

            //special cases for starting edges
            if (x < oldImageWidth_ - overlapX_) {
               xOverlap = false;
            }
            if (y < oldImageHeight_ - overlapY_) {
               yOverlap = false;
            }
            //special cases for ending edges
            if (x >= gridWidth_ * (oldImageWidth_ - overlapX_)) {
               xOverlap = false;
               tileX += oldImageWidth_ - overlapX_;
            }
            if (y >= gridHeight_ * (oldImageHeight_ - overlapY_)) {
               yOverlap = false;
               tileY += oldImageHeight_ - overlapY_;
            }

            int tileAboveX = tileX;
            int tileAboveY = oldImageHeight_ - overlapY_ + tileY;
            int tileLeftX = oldImageWidth_ - overlapX_ + tileX;
            int tileLeftY = tileY;


            byte[] tilePix = (byte[]) batch.get(gridY * gridWidth_ + gridX).pix;
            if(tilePix == null) {
               //If the tile pixels are ever null, return null;
               return null;
            }
            byte[] tileAbovePix = null, tileLeftPix = null, tileDiagonalPix = null;
            //get tiles above and left of tile as needed
            if (xOverlap) {
               tileLeftPix = (byte[]) batch.get(gridY * gridWidth_ + gridX - 1).pix;
            }
            if (yOverlap) {
               tileAbovePix = (byte[]) batch.get((gridY - 1) * gridWidth_ + gridX).pix;
            }
            if (xOverlap && yOverlap) {
               tileDiagonalPix = (byte[]) batch.get((gridY - 1) * gridWidth_ + gridX - 1).pix;
            }

            if (xOverlap && yOverlap) {
               //average all four overlapping values
               newPixels[x + newImageWidth_ * y] = (byte) ((getTilePixel(tilePix, tileX, tileY)
                       + getTilePixel(tileAbovePix, tileAboveX, tileAboveY)
                       + getTilePixel(tileLeftPix, tileLeftX, tileLeftY)
                       + getTilePixel(tileDiagonalPix, tileLeftX, tileAboveY)) / 4);
            } else if (xOverlap) {
               newPixels[x + newImageWidth_ * y] = (byte) ((getTilePixel(tilePix, tileX, tileY)
                       + getTilePixel(tileLeftPix, tileLeftX, tileLeftY)) / 2);
            } else if (yOverlap) {
               newPixels[x + newImageWidth_ * y] = (byte) ((getTilePixel(tilePix, tileX, tileY)
                       + getTilePixel(tileAbovePix, tileAboveX, tileAboveY)) / 2);
            } else {
               int numPixelsToCopy = oldImageWidth_ - 2 * overlapX_;
               //special cases for left and right side of image
               if (x < oldImageWidth_ - overlapX_) {
                  numPixelsToCopy = oldImageWidth_ - overlapX_;
               } else if (x >= gridWidth_ * (oldImageWidth_ - overlapX_)) {
                  numPixelsToCopy = overlapX_;
               }
               try {
                  System.arraycopy(tilePix, tileY * oldImageWidth_ + tileX, newPixels, x + newImageWidth_ * y, numPixelsToCopy);
               } catch (Exception e) {
                  System.out.println();
               }
               //subtract one to account for increment in loop
               x += numPixelsToCopy - 1;
            }
         }
      }
      return newPixels;
   }
  
   private byte[] stitchPixelsNoOverlap(LinkedList<TaggedImage> batch) {
      byte[] newPixels = new byte[oldImageHeight_ * oldImageWidth_ * gridWidth_ * gridHeight_];
      for (int line = 0; line < (gridWidth_ * gridHeight_) * (oldImageHeight_); line++) {
         int gridX = line % gridWidth_;
         int gridY = (line / gridWidth_) / oldImageHeight_;
         int oldImageLineNumber = (line / gridWidth_) % oldImageHeight_;
         int tileIndex = gridY * gridWidth_ + gridX;
         if (batch.get(tileIndex).pix == null) {
            return null;
         }
         System.arraycopy(batch.get(tileIndex).pix, oldImageLineNumber * oldImageWidth_,
                 newPixels, line * oldImageWidth_, oldImageWidth_);
      }
      return newPixels;
   }

   private void calcGridDimensions(LinkedList<TaggedImage> batch) throws JSONException {
      TreeSet<Double> xPositions = new TreeSet<Double>(), yPositions = new TreeSet<Double>();
      for (TaggedImage img : batch) {
         xPositions.add(img.tags.getDouble("XPositionUm"));
         yPositions.add(img.tags.getDouble("YPositionUm"));
      }
      oldImageWidth_ = MDUtils.getWidth(batch.getFirst().tags);
      oldImageHeight_ = MDUtils.getHeight(batch.getFirst().tags);
      if (swapXandY_) {
         gridWidth_ = yPositions.size();
         gridHeight_ = xPositions.size();
      } else {
         gridWidth_ = xPositions.size();
         gridHeight_ = yPositions.size();
      }
      newImageWidth_ = gridWidth_ * oldImageWidth_ - (gridWidth_ - 1)*overlapX_;
      newImageHeight_ = gridHeight_ * oldImageHeight_ - (gridHeight_ - 1)*overlapY_;
   }
   
    private Comparator<TaggedImage> makeGridSorter() {
      return new Comparator<TaggedImage>() {
         @Override
         public int compare(TaggedImage img1, TaggedImage img2) {
            if (swapXandY_) {
               try {
                  double x1 = img1.tags.getDouble("XPositionUm");
                  double x2 = img2.tags.getDouble("XPositionUm");
                  if (x1 != x2) {
                     return (int) (invertX_ ? (x1 - x2) : (x2 - x1));
                  }
                  double y1 = img1.tags.getDouble("YPositionUm");
                  double y2 = img2.tags.getDouble("YPositionUm");
                  if (y1 != y2) {
                     return (int) (invertY_ ? (y1 - y2) : (y2 - y1));
                  }
               } catch (JSONException ex) {
                 ReportingUtils.showError("Couldn't find stage coordinates");
               }
            } else {
               try {
                  double y1 = img1.tags.getDouble("YPositionUm");
                  double y2 = img2.tags.getDouble("YPositionUm");
                  if (y1 != y2) {
                     return (int) (invertY_ ? (y1 - y2) : (y2 - y1));
                  }
                  double x1 = img1.tags.getDouble("XPositionUm");
                  double x2 = img2.tags.getDouble("XPositionUm");
                  if (x1 != x2) {
                     return (int) (invertX_ ? (x1 - x2) : (x2 - x1));
                  }
               } catch (JSONException ex) {
                  ReportingUtils.showError("Couldn't find stage coordinates");
               }
            }
            return 0;
         }
      };
   }
}
