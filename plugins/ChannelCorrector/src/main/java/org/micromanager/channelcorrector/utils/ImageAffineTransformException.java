package org.micromanager.channelcorrector.utils;

public class ImageAffineTransformException extends Exception {
  String msg_;

  public ImageAffineTransformException(String msg) {
    msg_ = msg;
  }

  @Override
  public String getMessage() {
    return msg_;
  }
}
