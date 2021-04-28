/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display;

import java.util.List;

import org.micromanager.MMEvent;
import org.micromanager.data.Image;

/**
 * This event posts when a DataViewer displays an image(s). It only posts once the
 * image is actually showing
 *
 * The default implementation of this event posts on the DataViewer event bus.
 * Register using {@link DataViewer#registerForEvents(Object)}.
 */
public interface DisplayDidShowImageEvent extends MMEvent {

   /**
    * @return DataViewer instance displaying the image
    */
   DataViewer getDataViewer();

   /**
    * @return List of images newly displayed.
    */
   List<Image> getImages();

   /**
    * @return TODO: what is this and what is the significance?
    */
   Image getPrimaryImage();
}