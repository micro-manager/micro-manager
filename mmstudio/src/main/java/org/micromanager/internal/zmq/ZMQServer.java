package org.micromanager.internal.zmq;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import static org.micromanager.internal.zmq.ZMQUtil.EXTERNAL_OBJECTS;
import org.zeromq.SocketType;

/**
 * implements request reply server (ie the reply part)
 *
 * ecompasses both the master server and the
 */
public class ZMQServer extends ZMQSocketWrapper {

   private ExecutorService executor_;
   protected static Set<Class> apiClasses_;

   //Copied from magellan version for backwards compatibility, but they are now seperate I guess
   public static final String VERSION = "2.4.0";

   private Function<Class, Object> classMapper_;
   private static ZMQServer masterServer_;

   //for testing
//   public static void main(String[] args) {
//      ZMQServer server = new ZMQServer(DEFAULT_MASTER_PORT_NUMBER, "master", new Function<Class, Object>() {
//         @Override
//         public Object apply(Class t) {
//            return null;
//         }
//      });
//      while (true) {
//         if (portSocketMap_.containsKey(DEFAULT_MASTER_PORT_NUMBER + 1)) {
//            ZMQPullSocket socket = (ZMQPullSocket) portSocketMap_.get(DEFAULT_MASTER_PORT_NUMBER + 1);
//            Object n = socket.next();
//            System.out.println();
//         }
//      }
//   }
   public ZMQServer(Function<Class, Object> classMapper) {
      super(SocketType.REP);
      apiClasses_ = ZMQUtil.getAPIClasses();
      classMapper_ = classMapper;
   }

   public static ZMQServer getMasterServer() {
      return masterServer_;
   }

   @Override
   public void initialize(int port) {
      // Can we be initialized multiple times?  If so, we should cleanup
      // the multiple instances of executors and sockets cleanly
      executor_ = Executors.newSingleThreadExecutor(
              (Runnable r) -> new Thread(r, "ZMQ Server "));
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
                  json.put("value", e.toString());
                  reply = json.toString().getBytes();
                  e.printStackTrace();

               } catch (JSONException ex) {
                  throw new RuntimeException(ex);
                  // This wont happen          
               }
            }
            socket_.send(reply);
         }
      });
   }

   public void close() {
      if (executor_ != null) {
         executor_.shutdownNow();
         socket_.close();
      }
   }

   protected byte[] getField(Object obj, JSONObject json) throws JSONException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
      String fieldName = json.getString("name");
      Object field = obj.getClass().getField(fieldName).get(obj);
      JSONObject serialized = new JSONObject();
      ZMQUtil.serialize(apiClasses_, field, serialized, port_);
      return serialized.toString().getBytes();
   }
   
   protected void setField(Object obj, JSONObject json) throws JSONException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException {
      String fieldName = json.getString("name");
      Object val = json.get("value");
      if (val instanceof JSONObject) {
         val = EXTERNAL_OBJECTS.get(((JSONObject) val).getString("hash-code"));
      }
      obj.getClass().getField(fieldName).set(obj, val);
   }

   private LinkedList<LinkedList<Class>> getParamCombos(JSONObject message, Object[] argVals) throws JSONException {

      Object[] argClasses = new Object[message.getJSONArray("arguments").length()];
      for (int i = 0; i < argVals.length; i++) {
//         Class c = message.getJSONArray("arguments").get(i).getClass();
         if (message.getJSONArray("arguments").get(i) instanceof JSONObject
                 && message.getJSONArray("arguments").getJSONObject(i).has("hash-code")) {
            //Passed in a javashadow object as an argument
            argVals[i] = EXTERNAL_OBJECTS.get(
                    message.getJSONArray("arguments").getJSONObject(i).get("hash-code"));
            //abstract to superclasses/interfaces in the API
            ParamList<Class> potentialClasses = new ParamList<Class>();
            for (Class apiClass : apiClasses_) {
               if (apiClass.isAssignableFrom(argVals[i].getClass())) {
                  potentialClasses.add(apiClass);
               }
            }
            argClasses[i] = potentialClasses;
         } else if (ZMQUtil.PRIMITIVE_NAME_CLASS_MAP.containsKey(message.getJSONArray("argument-types").get(i))) {
            argClasses[i] = ZMQUtil.PRIMITIVE_NAME_CLASS_MAP.get(
                    message.getJSONArray("argument-types").get(i));         
            Object primitive = message.getJSONArray("arguments").get(i); //Double, Integer, Long, Boolean
            argVals[i] = ZMQUtil.convertToPrimitiveClass(primitive, (Class) argClasses[i]);            
         } else if (message.getJSONArray("argument-types").get(i).equals("java.lang.String")) {
            //Strings are a special case
            argClasses[i] = java.lang.String.class;
            argVals[i] = message.getJSONArray("arguments").getString(i);
         }
      }

      //Generate every possible combination of parameters given multiple interfaces for classes
      //so that the correct method can be located
      LinkedList<LinkedList<Class>> paramCombos = new LinkedList<LinkedList<Class>>();
      for (Object argument : argClasses) {
         if (argument instanceof ParamList) {
            if (paramCombos.isEmpty()) {
               //Add an entry for each possible type of the argument
               for (Class c : (ArrayList<Class>) argument) {
                  paramCombos.add(new LinkedList<Class>());
                  paramCombos.getLast().add(c);
               }
            } else {
               //multiply each existing combo by each possible value of the arg
               LinkedList<LinkedList<Class>> newComboList = new LinkedList<LinkedList<Class>>();
               for (Class c : (ArrayList<Class>) argument) {
                  for (LinkedList<Class> argList : paramCombos) {
                     LinkedList<Class> newArgList = new LinkedList<Class>(argList);
                     newArgList.add(c);
                     newComboList.add(newArgList);
                  }
               }
               paramCombos = newComboList;
            }
         } else {
            //only one type, simply add it to every combo
            if (paramCombos.isEmpty()) {
               //Add an entry for each possible type of the argument
               paramCombos.add(new LinkedList<Class>());
            }
            for (LinkedList<Class> argList : paramCombos) {
               argList.add((Class) argument);
            }
         }
      }
      return paramCombos;
   }

   private Object runConstructor(JSONObject message, Class baseClass) throws
           JSONException, InstantiationException, IllegalAccessException,
           IllegalArgumentException, InvocationTargetException {

      Object[] argVals = new Object[message.getJSONArray("arguments").length()];

      LinkedList<LinkedList<Class>> paramCombos = getParamCombos(message, argVals);

      Constructor mathcingConstructor = null;
      if (paramCombos.isEmpty()) { //Constructor with no argumetns
         try {
            mathcingConstructor = baseClass.getConstructor(new Class[]{});
         } catch (Exception ex) {
            throw new RuntimeException(ex);
         }
      } else { //Figure out which constructor matches given argumetns
         for (LinkedList<Class> argList : paramCombos) {
            Class[] classArray = argList.stream().toArray(Class[]::new);
            try {
               mathcingConstructor = baseClass.getConstructor(classArray);
               break;
            } catch (NoSuchMethodException e) {
               //ignore
            }
         }
      }
      if (mathcingConstructor == null) {
         throw new RuntimeException("No Matching method found with argumetn types");
      }

      return mathcingConstructor.newInstance(argVals);
   }

   private byte[] runMethod(Object obj, JSONObject message) throws NoSuchMethodException, IllegalAccessException, JSONException {
      String methodName = message.getString("name");
      Object[] argVals = new Object[message.getJSONArray("arguments").length()];
      LinkedList<LinkedList<Class>> paramCombos = getParamCombos(message, argVals);

      Method matchingMethod = null;
      if (paramCombos.isEmpty()) {
         //0 argument funtion
         matchingMethod = obj.getClass().getMethod(methodName);
      } else {
         for (LinkedList<Class> argList : paramCombos) {
            Class[] classArray = argList.stream().toArray(Class[]::new);
            try {
               matchingMethod = obj.getClass().getMethod(methodName, classArray);
               break;
            } catch (NoSuchMethodException e) {
               //ignore
            }
         }
      }
      if (matchingMethod == null) {
         throw new RuntimeException("No Matching method found with argumetn types");
      }

      Object result;
      try {
         result = matchingMethod.invoke(obj, argVals);
      } catch (InvocationTargetException ex) {
         ex.printStackTrace();
         result = ex.getCause();
      }

      JSONObject serialized = new JSONObject();
      ZMQUtil.serialize(apiClasses_, result, serialized, port_);
      return serialized.toString().getBytes();
   }

   protected byte[] parseAndExecuteCommand(String message) throws Exception {
      JSONObject request = new JSONObject(message);
      JSONObject reply;
      switch (request.getString("command")) {
         case "connect": {//Connect to master server
            masterServer_ = this;
            //Called by master process
            reply = new JSONObject();
            reply.put("type", "none");
            reply.put("version", VERSION);
            reply.put("api", ZMQUtil.parseConstructors(apiClasses_));
//            for (Class c : apiClasses_) {
//               System.out.println(c);
//            }
            return reply.toString().getBytes();
         }
//         case "pull-socket": { //Create a new Pull socket on the specified port
//            int port = Collections.max(portSocketMap_.keySet()) + 1;
//            new ZMQPullSocket(port, "ZMQ pull socket");
//            reply = new JSONObject();
//            reply.put("type", "none");
//            reply.put("port", port);
//            return reply.toString().getBytes();
//         }
         case "constructor": { //construct a new object (or grab an exisitng instance)
            Class baseClass = null;
            for (Class c : apiClasses_) {
               if (c.getName().equals(request.getString("classpath"))) {
                  baseClass = c;
               }
            }
            if (baseClass == null) {
               throw new RuntimeException("Couldnt find class with name" + request.getString("classpath"));
            }

            Object instance = classMapper_.apply(baseClass);
            //if this is not one of the classes that is supposed to grab an existing 
            //object, construct a new one
            if (instance == null) {
               instance = runConstructor(request, baseClass);
            }

            if (request.has("new-port") && request.getBoolean("new-port")) {
               //start the server for this class and store it
               new ZMQServer(classMapper_);
            }
            reply = new JSONObject();
            ZMQUtil.serialize(apiClasses_, instance, reply, port_);
            return reply.toString().getBytes();
         }
         case "run-method": {
            String hashCode = request.getString("hash-code");
            Object target = EXTERNAL_OBJECTS.get(hashCode);
            return runMethod(target, request);
         }
         case "get-field": {
            String hashCode = request.getString("hash-code");
            Object target = EXTERNAL_OBJECTS.get(hashCode);
            return getField(target, request);
         }
         case "set-field": {
            String hashCode = request.getString("hash-code");
            Object target = EXTERNAL_OBJECTS.get(hashCode);
            setField(target, request);
            reply = new JSONObject();
            reply.put("type", "none");
            return reply.toString().getBytes();
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

}

class ParamList<E> extends ArrayList<E> {

}
