package org.micromanager.internal.zmq;

import ij.IJ;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
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

   //Constructor for server the base class that runs on its own thread
   public ZMQServer(Class baseClass, int port) {
      super(baseClass, SocketType.REP, port);
   }

   @Override
   public void initialize(int port) {
      // Can we be initialized multiple times?  If so, we should cleanup
      // the multiple instances of executors and sockets cleanly
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
      // Do we need to unbing the socket when closing?  If so, how do we keep 
      // track of the port to unbind from?
      if (executor_ != null) {
         executor_.shutdownNow();
         socket_.close();
      }
   }

   @Override
   protected byte[] parseAndExecuteCommand(String message) throws Exception {
      JSONObject request = new JSONObject(message);
      JSONObject reply;
      switch (request.getString("command")) {
         case "connect":
            //Called from constructor of PygellanBridge in Pygellan
            reply = new JSONObject();
            reply.put("type", "none");
            reply.put("version", VERSION);
            initializeAPIClasses();
            return reply.toString().getBytes();
         case "constructor":
            Class baseClass = null;
            for (Class c : apiClasses_) {
               if (c.getName().equals(request.getString("classpath"))) {
                  baseClass = c;
               }
            }
            if (baseClass == null) {
               throw new RuntimeException("Couldnt find class with name" + request.getString("classpath"));
            }

            Object instance;
            if (baseClass.equals(Studio.class)) {
               instance = studio_;
            } else if (baseClass.equals(CMMCore.class)) {
               instance = studio_.getCMMCore();
            } else {
               instance = baseClass.newInstance();
            }
            if (request.has("port")) {
               //start the server for this class and store it
               int port = request.getInt("port");
               servers_.put(port, new ZMQServer(baseClass, port));
            }
            reply = new JSONObject();
            this.serialize(instance, reply);
            return reply.toString().getBytes();
         case "run-method": {
            String hashCode = request.getString("hash-code");
//            System.out.println("get object: " + hashCode);
            Object target = EXTERNAL_OBJECTS.get(hashCode);
            return runMethod(target, request);

         }
         case "get-field": {
            String hashCode = request.getString("hash-code");
//            System.out.println("get object: " + hashCode);
            Object target = EXTERNAL_OBJECTS.get(hashCode);
            return getField(target, request);

         }
         case "destructor": {
            String hashCode = request.getString("hash-code");
            //TODO this is defined in superclass, maybe it would be good to merge these?
//            System.out.println("remove object: " + hashCode);
            EXTERNAL_OBJECTS.remove(hashCode);
            reply = new JSONObject();

            reply.put("type", "none");
            return reply.toString().getBytes();
         }
         default:
            break;
      }
      throw new RuntimeException("Unknown Command");
   }

   //Add java classes that are allowed to pass to python to avoid stuff leaking out
   private void initializeAPIClasses() {
      apiClasses_ = new HashSet<>();

      //recursively get all names that have org.micromanager, but not internal in the name
      ArrayList<String> mmPackages = new ArrayList<>();
      Package[] p = Package.getPackages();
      for (Package pa : p) {
         //Add all non internal MM classes
         if (pa.getName().contains("org.micromanager") && !pa.getName().contains("internal")) {
            mmPackages.add(pa.getName());
         }
         //Add all core classes
         if (pa.getName().contains("mmcorej")) {
            mmPackages.add(pa.getName());
         }
      }


      // ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      ClassLoader classLoader = IJ.getClassLoader();
      studio_.logs().logDebugMessage("ClassLoader in ZMQServer: " + classLoader.toString());  
      for (String packageName : mmPackages) {
         String path = packageName.replace('.', '/');
         studio_.logs().logDebugMessage("ZMQServer-packageName: " + path);
         Enumeration<URL> resources;
         try {
            resources = classLoader.getResources(path);
         } catch (IOException ex) {
            throw new RuntimeException("Invalid package name in ZMQ server: " + path);
         }
         List<File> dirs = new ArrayList<>();
         while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            studio_.logs().logDebugMessage("ZMQServer-resource: " + resource.getFile());
            String file = resource.getFile().replaceAll("^file:", "");
            dirs.add(new File(file));
         }

         for (File directory : dirs) {
            if (directory.getAbsolutePath().contains(".jar")) {
               apiClasses_.addAll(getClassesFromJarFile(directory));
            } else {
               apiClasses_.addAll(getClassesFromDirectory(packageName, directory));
            }
         }       
      }

      for (Class c : apiClasses_) {
         studio_.logs().logDebugMessage("ZMQServer class: " + c.getName());
      }
      if (apiClasses_.isEmpty()) {
         studio_.logs().logDebugMessage("ZMQServer: no classes found");
      }

   }

   private static Collection<Class> getClassesFromJarFile( File directory) {
      List<Class> classes = new ArrayList<Class>();

      try {
         String jarPath = Stream.of(directory.getAbsolutePath().split(File.pathSeparator))
                 .flatMap((String t) -> Stream.of(t.split("!")))
                 .filter((String t) -> t.contains(".jar")).findFirst().get();
         String jp = jarPath.replaceAll("%20", " ");
         JarFile jarFile = new JarFile(jp);
         Enumeration<JarEntry> entries = jarFile.entries();
         while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            //include classes but not inner classes
            if (name.endsWith(".class") && !name.contains("$")) {
               try {
                  classes.add(Class.forName(name.replace("/", ".").
                          substring(0, name.length() - 6)));
               } catch (ClassNotFoundException ex) {
                  studio_.logs().logError("Class not found in ZMQ server: " + name);
               }
            }
         }
      } catch (IOException ex) {
         studio_.logs().logError(ex);
         //throw new RuntimeException(ex);
      }

      return classes;
   }

   private static Collection<Class> getClassesFromDirectory(String packageName, File directory) {
      List<Class> classes = new ArrayList<Class>();

      // get jar files from top-level directory
      List<File> jarFiles = listFiles(directory, new FilenameFilter() {
         @Override
         public boolean accept(File dir, String name) {
            return name.endsWith(".jar");
         }
      }, false);

      for (File file : jarFiles) {
         classes.addAll(getClassesFromJarFile(file));
      }

      // get all class-files
      List<File> classFiles = listFiles(directory, new FilenameFilter() {
         @Override
         public boolean accept(File dir, String name) {
            return name.endsWith(".class");
         }
      }, true);

      for (File file : classFiles) {
         if (!file.isDirectory()) {
            try {
               classes.add(Class.forName(packageName + '.' + file.getName().
                       substring(0, file.getName().length() - 6)));
            } catch (ClassNotFoundException ex) {
               studio_.logs().logError("Failed to load class: " + file.getName());
            }
         }
      }
      return classes;
   }

   private static List<File> listFiles(File directory, FilenameFilter filter, boolean recurse) {
      List<File> files = new ArrayList<File>();
      File[] entries = directory.listFiles();

      // Go over entries
      for (File entry : entries) {
         // If there is no filter or the filter accepts the
         // file / directory, add it to the list
         if (filter == null || filter.accept(directory, entry.getName())) {
            files.add(entry);
         }

         // If the file is a directory and the recurse flag
         // is set, recurse into the directory
         if (recurse && entry.isDirectory()) {
            files.addAll(listFiles(entry, filter, recurse));
         }
      }

      // Return collection of files
      return files;
   }

}
