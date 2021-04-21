/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display;

import org.micromanager.data.Image;

import java.util.List;

/** @author mark */
public interface DisplayDidShowImageEvent {
  DataViewer getDataViewer();

  List<Image> getImages();

  Image getPrimaryImage();
}
