/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.event;

import java.util.ArrayList;
import java.util.List;
import org.micromanager.data.Image;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayDidShowImageEvent;

/**
 * This event posts when a DataViewer displays an image(s). It only posts once the
 * image is actually showing.
 *
 * This event posts on the DataViewer event bus.
 * Register using {@link DataViewer#registerForEvents(Object)}.
 */
public class DefaultDisplayDidShowImageEvent implements DisplayDidShowImageEvent {
   private final DataViewer viewer_;
   private final List<Image> images_;
   private final Image primaryImage_;

   public static DefaultDisplayDidShowImageEvent create(DataViewer viewer,
         List<Image> images, Image primaryImage)
   {
      return new DefaultDisplayDidShowImageEvent(viewer, images,
      primaryImage);
   }

   private DefaultDisplayDidShowImageEvent(DataViewer viewer,
         List<Image> images, Image primaryImage)
   {
      viewer_ = viewer;
      images_ = new ArrayList<Image>(images);
      primaryImage_ = primaryImage;
   }

   /**
    * @return DataViewer instance displaying the image
    */
   @Override
   public DataViewer getDataViewer() {
      return viewer_;
   }

   /**
    * @return List of images newly displayed.
    */
   @Override
   public List<Image> getImages() {
      return new ArrayList<Image>(images_);
   }

   /**
    * @return TODO: what is this and what is the significance?
    */
   @Override
   public Image getPrimaryImage() {
      return primaryImage_;
   }
}