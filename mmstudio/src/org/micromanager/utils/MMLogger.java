package org.micromanager.utils;

import java.util.logging.Logger;

public class MMLogger {
   private static Logger logger_;
   public MMLogger() {
      if (logger_ == null)
         logger_ = Logger.getLogger(this.getClass().getName());
   }
   
   public static Logger getLogger() {
      if (logger_ == null)
         new MMLogger();
      return logger_;
   }

}
