package org.micromanager.internal.zmq;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mmcorej.CMMCore;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.Studio;
import org.zeromq.SocketType;

/**
 * implements request reply server (ie the reply part)
 */
public class ZMQServer extends ZMQSocketWrapper {

   //Copied from magellan version for backwards compatibility, but they are now seperate I guess
   public static final String VERSION = "2.3.0"; 
   public static final int DEFAULT_PORT_NUMBER = 4827;

   private ExecutorService executor_;
   //map of port numbers to servers, each of which has its own thread and base class
   private static HashMap<Integer, ZMQServer> servers_
           = new HashMap<Integer, ZMQServer>();

   //Constructor for master server
   public ZMQServer(Studio studio) {
      super(studio, SocketType.REP);
   }

   //Constructor for server the a base class that runs on its own thread
   public ZMQServer(Class baseClass, int port) {
      super(baseClass, SocketType.REP, port);
   }

   @Override
   public void initialize(int port) {
      executor_ = Executors.newSingleThreadExecutor(
              (Runnable r) -> new Thread(r, "ZMQ Server " + name_));
      executor_.submit(() -> {
         socket_ = context_.createSocket(type_);
         port_ = port;
         socket_.bind("tcp://127.0.0.1:" + port);

         while (true) {
            String message = socket_.recvStr();
            byte[] reply = null;
            try {
               reply = parseAndExecuteCommand(message);
            } catch (Exception e) {
               try {
                  JSONObject json = new JSONObject();
                  json.put("type", "exception");
                  json.put("value", e.getMessage());
                  reply = json.toString().getBytes();
                  studio_.logs().logError(e);
               } catch (JSONException ex) {
                  // This wont happen
                  studio_.logs().logError(ex);
               }
            }
            socket_.send(reply);
         }
      });
   }

   public void close() {
      if (executor_ != null) {
         executor_.shutdownNow();
      }
   }

   @Override
   protected byte[] parseAndExecuteCommand(String message) throws Exception {
      JSONObject json = new JSONObject(message);

      switch (json.getString("command")) {
         case "connect":
            String server = json.getString("classpath");
            if (server.equals("master")) {
               //Called from constructor of MagellanBridge in Pygellan
               JSONObject reply = new JSONObject();
               reply.put("reply", "success");
               reply.put("version", VERSION);
//               initializeAPIClasses(json.getString("packages").split(":"));
               initializeAPIClasses();

               return reply.toString().getBytes();
            } else {
               Class baseClass = null;
               for (Class c : apiClasses_) {
                  if (c.getName().equals(json.getString("classpath"))) {
                     baseClass = c;
                  }
               }
               if (baseClass == null) {
                  throw new RuntimeException("Couldnt find class with name" + json.getString("classpath"));
               }

               Object instance;
               if (baseClass.equals(Studio.class)) {
                  instance = studio_;
               } else if (baseClass.equals(CMMCore.class)) {
                  instance = studio_.getCMMCore();
               } else {
                  instance = baseClass.getField("instance_").get(null);
               }

               //start the server for this class and store it
               int port = json.getInt("port");
               servers_.put(port, new ZMQServer(baseClass, port));
               JSONObject reply = new JSONObject();
               reply.put("reply", "success");
               this.serialize(instance, reply);
               return reply.toString().getBytes();
            }

         case "run-method": {
            String hashCode = json.getString("hash-code");
            Object target = EXTERNAL_OBJECTS.get(hashCode);
            return runMethod(target, json);
         }
         case "destructor": {
            String hashCode = json.getString("hash-code");
            //TODO this is defined in superclass, maybe it would be good to merge these?
            EXTERNAL_OBJECTS.remove(hashCode);
            JSONObject reply = new JSONObject();
            reply.put("reply", "success");
            return reply.toString().getBytes();
         }
         default:
            break;
      }
      throw new RuntimeException(
              "Unknown Command");
   }

   //Add java classes that are allowed to pass to python to avoid stuff leaking out
   private void initializeAPIClasses() {
      apiClasses_ = new HashSet<>();
      //manually add this one
      apiClasses_.add(CMMCore.class);

      //recursively get all names that have org.micromånager, but not internal in the name
      ArrayList<String> mmPackages = new ArrayList<String>();
      Package[] p = Package.getPackages();
      for (Package pa : p) {
//         System.out.println(pa.getName());
         if (pa.getName().contains("org.micromanager") && !pa.getName().contains("internal")) {
            mmPackages.add(pa.getName());
         }
      }

//      System.out.println("\n\n");
//      for (String s : mmPackages) {
//         System.out.println(s);
//      }

      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      for (String packageName : mmPackages) {
         String path = packageName.replace('.', '/');
         Enumeration<URL> resources;
         try {
            resources = classLoader.getResources(path);
         } catch (IOException ex) {
            throw new RuntimeException("Invalid package neame in ZMQ server: " + path);
         }
         List<File> dirs = new ArrayList<File>();
         while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            dirs.add(new File(resource.getFile()));
         }

         for (File directory : dirs) {
            File[] files = directory.listFiles();
            if (files != null) {
               for (File file : files) {
                  if (!file.isDirectory()) {
                     try {
                        apiClasses_.add(Class.forName(packageName + '.' + file.getName().
                                substring(0, file.getName().length() - 6)));
                     } catch (ClassNotFoundException ex) {
                        throw new RuntimeException("Problem loading class:" + file.getName());
                     }
                  }
               }
            }
         }
      }
   }
}
