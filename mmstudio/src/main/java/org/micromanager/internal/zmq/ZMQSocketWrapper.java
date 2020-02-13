package org.micromanager.internal.zmq;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.Studio;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

// Base class that wraps a ZMQ socket and implements type conversions as well 
// as the impicit JSON message syntax
public abstract class ZMQSocketWrapper {

   private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
   public final static Map<Class<?>, Class<?>> PRIMITIVE_CLASS_MAP = new HashMap<Class<?>, Class<?>>();

   static {
      PRIMITIVE_CLASS_MAP.put(Boolean.class, boolean.class);
      PRIMITIVE_CLASS_MAP.put(Byte.class, byte.class);
      PRIMITIVE_CLASS_MAP.put(Short.class, short.class);
      PRIMITIVE_CLASS_MAP.put(Character.class, char.class);
      PRIMITIVE_CLASS_MAP.put(Integer.class, int.class);
      PRIMITIVE_CLASS_MAP.put(Long.class, long.class);
      PRIMITIVE_CLASS_MAP.put(Float.class, float.class);
      PRIMITIVE_CLASS_MAP.put(Double.class, double.class);
   }

   //map of objects that exist in some client of the server
   protected final static ConcurrentHashMap<String, Object> EXTERNAL_OBJECTS
           = new ConcurrentHashMap<String, Object>();

   protected static HashSet<Class> apiClasses_;

   protected static Studio studio_;
   protected static ZContext context_;
   protected SocketType type_;
   protected ZMQ.Socket socket_;
   protected int port_;
   protected String name_;

   public ZMQSocketWrapper(Studio studio, SocketType type) {
      studio_ = studio;
      type_ = type;
      name_ = "master";
      if (context_ == null) {
         context_ = new ZContext();
      }
      //dont initialize, this is done in a seperate call for the master server
   }

   public ZMQSocketWrapper(Class clazz, SocketType type, int port) {
      type_ = type;
      name_ = clazz == null ? "master" : clazz.getName();
      if (context_ == null) {
         context_ = new ZContext();
      }
      initialize(port);
   }

   public abstract void initialize(int port);

   /**
    * send a command from a Java client to a python server and wait for response
    *
    * @param request Command to be send through the port
    * @return response from the Python side
    */
   protected Object sendRequest(String request) {
      socket_.send(request);
      byte[] reply = socket_.recv();
      return deserialize(reply);
   }

   protected abstract byte[] parseAndExecuteCommand(String message) throws Exception;

   protected byte[] getField(Object obj, JSONObject json) throws JSONException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException   {
      String fieldName = json.getString("name");
      Object field = obj.getClass().getField(fieldName).get(obj);
      JSONObject serialized = new JSONObject();
      serialize(field, serialized);
      return serialized.toString().getBytes();
   }
   
   protected byte[] runMethod(Object obj, JSONObject json) throws NoSuchMethodException, IllegalAccessException, JSONException {
      String methodName = json.getString("name");

      Object[] argClasses = new Object[json.getJSONArray("arguments").length()];
      Object[] argVals = new Object[json.getJSONArray("arguments").length()];
      for (int i = 0; i < argVals.length; i++) {
         Class c = json.getJSONArray("arguments").get(i).getClass();
         if (json.getJSONArray("arguments").get(i) instanceof JSONObject
                 && json.getJSONArray("arguments").getJSONObject(i).has("hash-code")) {
            //Passed in a javashadow object as an argument
            argVals[i] = EXTERNAL_OBJECTS.get(
                    json.getJSONArray("arguments").getJSONObject(i).get("hash-code"));
            //abstract to superclasses/interfaces in the API
            ParamList<Class> potentialClasses = new ParamList<Class>();
            for (Class apiClass : apiClasses_) {
               if (apiClass.isAssignableFrom(argVals[i].getClass())) {
                  potentialClasses.add(apiClass);
               }
            }
            argClasses[i] = potentialClasses;
            continue;
         } else if (PRIMITIVE_CLASS_MAP.containsKey(c)) {
            c = PRIMITIVE_CLASS_MAP.get(c);
         }
         //TODO probably some more work to do here in deserializing argumen types (e.g. TaggedImage)
         argClasses[i] = c;
         argVals[i] = json.getJSONArray("arguments").get(i);
      }

      //Generate every possible combination of parameters given multiple interfaces
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
         result = ex.getCause();
         studio_.logs().logError(ex);
      }

      JSONObject serialized = new JSONObject();
      serialize(result, serialized);
      return serialized.toString().getBytes();
   }

   protected Object deserialize(byte[] message) {
      String s = new String(message);

      JSONObject json = null;
      try {
         json = new JSONObject(s);
      } catch (JSONException ex) {
         throw new RuntimeException("Problem turning message into JSON. Message was: " + s);
      }
      try {
         String type = json.getString("type");
         String value = json.getString("value");
         //TODO: decode values
         if (type.equals("object")) {
            String clazz = json.getString("class");
         } else if (type.equals("primitive")) {

         }
         return null;
         //TODO: return exception maybe?
      } catch (JSONException ex) {
         throw new RuntimeException("Message missing command field");
      }

   }

   /**
    * Serialize the object in some way that the client will know how to
    * deserialize
    *
    * @param o Object to be serialized
    * @param json JSONObject that will contain the serialized Object can not be
    * null
    */
   protected void serialize(Object o, JSONObject json) {
      try {
         if (o instanceof Exception) {
            json.put("type", "exception");

            Throwable root = ((Exception) o).getCause() == null
                    ? ((Exception) o) : ((Exception) o).getCause();
            String s = root.toString() + "\n";
            for (StackTraceElement el : root.getStackTrace()) {
               s += el.toString() + "\n";
            }
            json.put("value", s);
         } else if (o instanceof String) {
            json.put("type", "string");
            json.put("value", o);
         } else if (o == null) {
            json.put("type", "none");
         } else if (o.getClass().equals(Long.class) || o.getClass().equals(Short.class)
                 || o.getClass().equals(Integer.class) || o.getClass().equals(Float.class)
                 || o.getClass().equals(Double.class) || o.getClass().equals(Boolean.class)) {
            json.put("type", "primitive");
            json.put("value", o);
         } else if (o.getClass().equals(JSONObject.class)) {
            json.put("type", "object");
            json.put("class", "JSONObject");
            json.put("value", o.toString());
         } else if (o.getClass().equals(byte[].class)) {
            json.put("type", "byte-array");
            json.put("value", encodeArray(o));
         } else if (o.getClass().equals(short[].class)) {
            json.put("type", "short-array");
            json.put("value", encodeArray(o));
         } else if (o.getClass().equals(double[].class)) {
            json.put("type", "double-array");
            json.put("value", encodeArray(o));
         } else if (o.getClass().equals(int[].class)) {
            json.put("type", "int-array");
            json.put("value", encodeArray(o));
         } else if (o.getClass().equals(float[].class)) {
            json.put("type", "float-array");
            json.put("value", encodeArray(o));
         } else if (Stream.of(o.getClass().getInterfaces()).anyMatch((Class t) -> t.equals(List.class))) {
            //Serialize java lists as JSON arrays so tehy canbe converted into python lists
            json.put("type", "list");
            json.put("value", new JSONArray());
            for (Object element : (List) o) {
               JSONObject e = new JSONObject();
               json.getJSONArray("value").put(e);
               serialize(element, e);
            }
         } else {
            //Don't serialize the object, but rather send out its name so that python side
            //can construct a shadow version of it
            //Keep track of which objects have been sent out, so that garbage collection can be synchronized between 
            //the two languages
            String hash = Integer.toHexString(System.identityHashCode(o));
            //Add a random UUID to account for the fact that there may be multiple
            //pythons shadows of the same object
            hash += UUID.randomUUID();
            EXTERNAL_OBJECTS.put(hash, o);
            json.put("type", "unserialized-object");
            json.put("class", o.getClass().getName());
            json.put("hash-code", hash);
            json.put("port", port_);

            //check to make sure that only exposing methods corresponding to API interfaces
            ArrayList<Class> apiInterfaces = new ArrayList<>();
            for (Class apiClass : apiClasses_) {
               if (apiClass.isAssignableFrom(o.getClass())) {
                  apiInterfaces.add(apiClass);
               }
            }

            if (apiInterfaces.isEmpty()) {
               throw new RuntimeException("Internal class accidentally exposed");
            }
            //List all API interfaces this class implments in case its passed
            //back as an argument to another function
            JSONArray e = new JSONArray();
            json.put("interfaces", e);
            for (Class c : apiInterfaces) {
               e.put(c.getName());
            }

            //copy in all public fields of the object
            JSONArray f = new JSONArray();
            json.put("fields", f);
            for (Field field : o.getClass().getFields()) {
               int modifiers = field.getModifiers();
               if (Modifier.isPublic(modifiers)) {
                  f.put(field.getName());
               }
            }

            json.put("api", parseAPI(apiInterfaces));
         }
      } catch (JSONException e) {
         studio_.logs().logError(e);
      }
   }

   protected String encodeArray(Object array) {
      byte[] byteArray = null;
      if (array instanceof byte[]) {
         byteArray = (byte[]) array;
      } else if (array instanceof short[]) {
         ByteBuffer buffer = ByteBuffer.allocate((((short[]) array)).length * Short.BYTES);
         buffer.order(BYTE_ORDER).asShortBuffer().put((short[]) array);
         byteArray = buffer.array();
      } else if (array instanceof int[]) {
         ByteBuffer buffer = ByteBuffer.allocate((((int[]) array)).length * Integer.BYTES);
         buffer.order(BYTE_ORDER).asIntBuffer().put((int[]) array);
         byteArray = buffer.array();
      } else if (array instanceof double[]) {
         ByteBuffer buffer = ByteBuffer.allocate((((double[]) array)).length * Double.BYTES);
         buffer.order(BYTE_ORDER).asDoubleBuffer().put((double[]) array);
         byteArray = buffer.array();
      } else if (array instanceof float[]) {
         ByteBuffer buffer = ByteBuffer.allocate((((float[]) array)).length * Float.BYTES);
         buffer.order(BYTE_ORDER).asFloatBuffer().put((float[]) array);
         byteArray = buffer.array();
      }
      return Base64.getEncoder().encodeToString(byteArray);
   }

   /**
    * Go through all methods of the given class and put them into a big JSON
    * array that describes the API
    *
    * @param apiClasses Classes to be translated into JSON
    * @return Classes translated to JSON
    * @throws JSONException
    */
   protected static JSONArray parseAPI(ArrayList<Class> apiClasses) throws JSONException {
      JSONArray methodArray = new JSONArray();
      for (Class clazz : apiClasses) {
         Method[] m = clazz.getDeclaredMethods();
         Stream<Method> s = Arrays.stream(m);
         List<Method> validMethods = s.collect(Collectors.toList());
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
      }
      return methodArray;
   }
}

class ParamList<E> extends ArrayList<E> {

}
