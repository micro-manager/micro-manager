package main;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import gui.GUI;
import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.util.HashMap;
import java.util.Map;
import java.util.prefs.Preferences;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author Henry
 */
public class Navigator implements MMPlugin{

   private static final String VERSION = "Beta";
           
   public static final String menuName = "Navigator";
   public static final String tooltipDescription = "Navigator plugin";

   private Preferences prefs_;
   private ScriptInterface mmAPI_;
   
   
   public Navigator() {
      prefs_ = Preferences.userNodeForPackage(Navigator.class);
   }
   
   @Override
   public void dispose() {
   }

   @Override
   public void setApp(ScriptInterface si) {
      mmAPI_ = si;
   }

   @Override
   public void show() {
//      Toolkit.getDefaultToolkit().getSystemEventQueue().push(new TracingEventQueue());
      
      new GUI(prefs_, mmAPI_,VERSION);
   }

   @Override
   public String getDescription() {
      return "test description";
   }

   @Override
   public String getInfo() {
      return "test info";
   }

   @Override
   public String getVersion() {
      return VERSION;
   }

   @Override
   public String getCopyright() {
      return "Henry Pinkard UCSF 2014";
   }
  
//   class TracingEventQueue extends EventQueue {
//
//      private TracingEventQueueThread tracingThread;
//
//      public TracingEventQueue() {
//         this.tracingThread = new TracingEventQueueThread(500);
//         this.tracingThread.start();
//      }
//
//      @Override
//      protected void dispatchEvent(AWTEvent event) {
//         this.tracingThread.eventDispatched(event);
//         super.dispatchEvent(event);
//         this.tracingThread.eventProcessed(event);
//      }
//   }
//
//   class TracingEventQueueThread extends Thread {
//
//      private long thresholdDelay;
//
//        private Map<AWTEvent, Long> eventTimeMap;
//
//        public TracingEventQueueThread(long thresholdDelay) {
//                this.thresholdDelay = thresholdDelay;
//                this.eventTimeMap = new HashMap<AWTEvent, Long>();
//        }
//
//        public synchronized void eventDispatched(AWTEvent event) {
//                this.eventTimeMap.put(event, System.currentTimeMillis());
//        }
//
//        public synchronized void eventProcessed(AWTEvent event) {
//                this.checkEventTime(event, System.currentTimeMillis(),
//                                this.eventTimeMap.get(event));
//                this.eventTimeMap.put(event, null);
//        }
//
//        private void checkEventTime(AWTEvent event, long currTime, long startTime) {
//                long currProcessingTime = currTime - startTime;
//                if (currProcessingTime >= this.thresholdDelay) {
//                        System.out.println("Event [" + event.hashCode() + "] "
//                                        + event.getClass().getName()
//                                        + " is taking too much time on EDT (" + currProcessingTime
//                                        + ")");
//                }
//        }
//
//        @Override
//        public void run() {
//                while (true) {
//                        long currTime = System.currentTimeMillis();
//                        synchronized (this) {
//                                for (Map.Entry<AWTEvent, Long> entry : this.eventTimeMap
//                                                .entrySet()) {
//                                        AWTEvent event = entry.getKey();
//                                        if (entry.getValue() == null)
//                                                continue;
//                                        long startTime = entry.getValue();
//                                        this.checkEventTime(event, currTime, startTime);
//                                }
//                        }
//                        try {
//                                Thread.sleep(100);
//                        } catch (InterruptedException ie) {
//                        }
//                }
//        }
//}
}
