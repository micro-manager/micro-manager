package org.micromanager.autofocus.optimizers;

import org.micromanager.Studio;

/**
 * Interface for focus optimizers.
 */
public interface FocusOptimizer {

   /**
    * Set the context of the Studio object.
    *
    * @param studio The Studio object that will be used to control the camera and Z-stage.
    */
   void setContext(Studio studio);

   /**
    * Setter for Display Image flag.
    *
    * @param display If `true` then the images taken by the focuser will be displayed in
    *                real-time.
    */
   void setDisplayImages(boolean display);

   /**
    * Getter for Display Image flag.
    *
    * @return `true` if the images taken by the focuser will be displayed in real-time.
    */
   boolean getDisplayImages();

   /**
    * Getter for the number of images taken by the focuser.
    *
    * @return The number of images taken by the focuser.
    */
   int getImageCount();

   /**
    * Setter for the search range.
    *
    * @param searchRange The range in micrometers that the focuser will search for the best focus.
    */
   void setSearchRange(double searchRange);

   /**
    * Getter for the search range.
    *
    * @return The range in micrometers that the focuser will search for the best focus.
    */
   double getSearchRange();

   /**
    * Setter for the absolute tolerance.
    *
    * @param tolerance The absolute tolerance in micrometers that the focuser will use to determine
    *                  when the best focus has been found.
    */
   void setAbsoluteTolerance(double tolerance);

   /**
    * Getter for the absolute tolerance.
    *
    * @return The absolute tolerance in micrometers that the focuser will use to determine
    *         when the best focus has been found.
    */
   double getAbsoluteTolerance();


   /**
    * Acquires the images and calculates the best focus positions.
    *
    * @return Optimal Z stage position.
    * @throws Exception A common exception is failure to set the Z position in the hardware
    */
   double runAutofocusAlgorithm() throws Exception;

}
