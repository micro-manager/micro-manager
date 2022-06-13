package de.embl.rieslab.emu.controller.log;

import org.micromanager.LogManager;

public class Logger {

   LogManager logManager_;

   public Logger() {
   }

   public Logger(LogManager logManager) {
      logManager_ = logManager;
   }

   public void logDebugMessage(String message) {
      if (logManager_ != null) {
         logManager_.logDebugMessage("[EMU] -- " + message);
      } else {
         System.out.println("[EMU debug] -- " + message);
      }
   }

   public void logError(Exception e) {
      if (logManager_ != null) {
         logManager_.logError(e);
      } else {
         e.printStackTrace();
      }
   }

   public void logError(String message) {
      if (logManager_ != null) {
         logManager_.logError("[EMU] -- " + message);
      } else {
         System.out.println("[EMU error] -- " + message);
      }
   }

   public void logMessage(String message) {
      if (logManager_ != null) {
         logManager_.logMessage("[EMU] -- " + message);
      } else {
         System.out.println("[EMU message] -- " + message);
      }
   }
}
