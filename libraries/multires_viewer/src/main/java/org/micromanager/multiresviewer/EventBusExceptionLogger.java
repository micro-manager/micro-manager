/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.multiresviewer;

import com.google.common.eventbus.SubscriberExceptionContext;
import com.google.common.eventbus.SubscriberExceptionHandler;

/**
 *
 * @author mark
 */
public class EventBusExceptionLogger implements SubscriberExceptionHandler {
   private static final SubscriberExceptionHandler instance_ =
         new EventBusExceptionLogger();

   public static SubscriberExceptionHandler getInstance() {
      return instance_;
   }

   @Override
   public void handleException(Throwable thrwbl, SubscriberExceptionContext sec) {
      String message = "Exception thrown by EventBus subscriber:\n" +
            "Event: " + sec.getEvent() + "\n" +
            "EventBus: " + sec.getEventBus() + "\n" +
            "Subscriber: " + sec.getSubscriber() + "\n" +
            "Subscriber Method: " + sec.getSubscriberMethod();
      thrwbl.printStackTrace();
      System.err.println(thrwbl.getMessage());
   }
}