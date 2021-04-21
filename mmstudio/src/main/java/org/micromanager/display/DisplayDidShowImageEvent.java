/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display;

import java.util.List;
import org.micromanager.data.Image;

/** @author mark */
public interface DisplayDidShowImageEvent {
  DataViewer getDataViewer();

  List<Image> getImages();

  Image getPrimaryImage();
}
