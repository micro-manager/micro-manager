/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.socketbridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.magellan.json.JSONArray;
import org.micromanager.magellan.json.JSONException;
import org.micromanager.magellan.json.JSONObject;
import org.micromanager.magellan.main.Magellan;
import org.micromanager.magellan.misc.Log;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class ZMQServer {

   public final static Map<Class<?>, Class<?>> primitiveClassMap_ = new HashMap<Class<?>, Class<?>>();

   static {
      primitiveClassMap_.put(Boolean.class, boolean.class);
      primitiveClassMap_.put(Byte.class, byte.class);
      primitiveClassMap_.put(Short.class, short.class);
      primitiveClassMap_.put(Character.class, char.class);
      primitiveClassMap_.put(Integer.class, int.class);
      primitiveClassMap_.put(Long.class, long.class);
      primitiveClassMap_.put(Float.class, float.class);
      primitiveClassMap_.put(Double.class, double.class);
   }

   
   private static Class[] SERIALIZABLE_CLASSES = new Class[]{String.class, Void.TYPE, Short.TYPE,
      Long.TYPE, Integer.TYPE, Float.TYPE, Double.TYPE, Boolean.TYPE,
      byte[].class, double[].class, int[].class, TaggedImage.class};

   private ZContext context_;
   private ExecutorService coreExecutor_;
   

   public ZMQServer() {
      context_ = new ZContext();
      coreExecutor_ = Executors.newSingleThreadExecutor(
              (Runnable r) -> new Thread(r, "ZMQ Core Executor thread"));
      coreExecutor_.submit(runCoreExecutor());

   }

   private Runnable runCoreExecutor() {
      return new Runnable() {
         @Override
         public void run() {
            ZMQ.Socket sock = context_.createSocket(SocketType.REP);
            sock.bind("tcp://127.0.0.1:4827");

            while (true) {
               String message = sock.recvStr();
               byte[] reply = "error".getBytes();
               try {
                  reply = parseCommand(message);
               } catch (Exception e) {
                  e.printStackTrace();
                  Log.log(e.getMessage());
               }
               sock.send(reply);

            }
         }
      };
   }

   private byte[] parseCommand(String message) throws JSONException, NoSuchMethodException, IllegalAccessException, IllegalArgumentException {
      JSONObject json = new JSONObject(message);
      if (json.getString("command").equals("send-core-api")) {
         return parseCoreAPI();
      } else if (json.getString("command").equals("run-method") ) {
         String methodName = json.getString("name");
         
         Class[] argClasses = new Class[json.getJSONArray("arguments").length()];
         Object[] argVals = new Object[json.getJSONArray("arguments").length()];
         for (int i = 0; i < argVals.length; i++) {
            //Converts onpbjects to primitives
            Class c = json.getJSONArray("arguments").get(i).getClass();
            if (primitiveClassMap_.containsKey(c)) {
               c = primitiveClassMap_.get(c);
            }                      
            argClasses[i] = c;
            argVals[i] = json.getJSONArray("arguments").get(i);
         }
         
         Method method = CMMCore.class.getMethod(methodName, argClasses);
         
         Object result = null;
         try {
            result = method.invoke(Magellan.getCore(), argVals);
         } catch (InvocationTargetException ex) {
            Log.log(ex);
         }
         if (result == null) {
            return "void".getBytes();
         } else {
            return serialize(result);
         }
      } else {
         throw new RuntimeException("Unknown Command");
      }
   }

   private byte[] serialize(Object o) {
      if (o instanceof String) {
         return ((String) o).getBytes();
      } 
      return null;
   }

   private static boolean isValidCoreMethod(Method t) {
      List<Class> l = new ArrayList<>();
      for (Class c : t.getParameterTypes()) {
         l.add(c);
      }
      l.add(t.getReturnType());
      for (Class c : l) {
         if (! Arrays.asList(SERIALIZABLE_CLASSES).contains(c)) {
            return false;
         }
      }       
      return true;
   }
   

   private static byte[] parseCoreAPI() throws JSONException {
      //Collect all methods whose return types and arguments we know how to translate, and put them in a JSON array describing them
      Predicate<Method> methodFilter = (Method t) -> {
         return isValidCoreMethod(t);
      };

      Class coreClass = Magellan.getCore().getClass();
      Method[] m = coreClass.getDeclaredMethods();
      Stream<Method> s = Arrays.stream(m);
      s = s.filter(methodFilter);
      List<Method> validMethods = s.collect(Collectors.toList());
      JSONArray methodArray = new JSONArray();
      for (Method method : validMethods) {
         JSONObject methJSON = new JSONObject();
         methJSON.put("name", method.getName());
         methJSON.put("return-type", method.getReturnType().getCanonicalName());
         JSONArray args = new JSONArray();
         for (Class arg : method.getParameterTypes()) {
            args.put(arg.getCanonicalName());
         }
         methJSON.put("arguments", args);
         methodArray.put(methJSON);
      }
      return methodArray.toString().getBytes();
   }

//   public static void run() {
//     parseCoreAPI();
//   }
//   public static void main(String[] args) {
//
//      try (ZContext context = new ZContext()) {
//
//         
//         while (true) {
//         }
//
//         //Push socket
////           ZMQ.Socket sock = context.createSocket(SocketType.PUSH);
////           sock.connect("tcp://127.0.0.1:4827");
////       
////           int i = 0;   
////           while (true) {
////              byte[] message = new byte[1024 * 1024];
////              Random rand = new Random();
////              rand.nextBytes(message);
////              sock.send(message);
////              System.out.println("sent message " + i);
////              i++;
////           }
//      }
//   }
}
