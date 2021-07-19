package org.micromanager.display;

import java.util.List;
import org.micromanager.MMEvent;
import org.micromanager.data.Image;

/**
 * This event posts when a DataViewer displays an image(s). It only posts once the
 * image is actually showing
 *
 * <p>The default implementation of this event posts on the DataViewer event bus.
 * Register using {@link DataViewer#registerForEvents(Object)}.</p>
 */
public interface DisplayDidShowImageEvent extends MMEvent {

   /**
    * Returns the Dataviewer displaying the image.
    *
    * @return DataViewer instance displaying the image
    */
   DataViewer getDataViewer();

   /**
    * Returns the images newly displayed.
    *
    * @return List of images newly displayed.
    */
   List<Image> getImages();

   /**
    * Unsure what this is or means.
    *
    * @return TODO: what is this and what is the significance?
    */
   Image getPrimaryImage();
}