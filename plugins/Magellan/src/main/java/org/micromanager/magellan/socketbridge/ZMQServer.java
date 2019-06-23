package org.micromanager.magellan.socketbridge;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
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

   private static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
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

   public ZMQServer(int port) {
      context_ = new ZContext();
      coreExecutor_ = Executors.newSingleThreadExecutor(
              (Runnable r) -> new Thread(r, "ZMQ Core Executor thread"));
      coreExecutor_.submit(runCommandExecutor(port));

   }

   private Runnable runCommandExecutor(final int port) {
      return new Runnable() {
         @Override
         public void run() {
            ZMQ.Socket sock = context_.createSocket(SocketType.REP);
            sock.bind("tcp://127.0.0.1:" + port);

            while (true) {
               String message = sock.recvStr();
               byte[] reply = null;
               try {
                  reply = parseAndExecuteCommand(message);
               } catch (Exception e) {
                  try {
                     JSONObject json = new JSONObject();
                     json.put("Type", "Exception");
                     json.put("Message", e.getMessage());
                     reply = json.toString().getBytes();
                     e.printStackTrace();
                     Log.log(e.getMessage());
                  } catch (JSONException ex) {
                     //This wont happen
                  }
               }
               sock.send(reply);

            }
         }
      };
   }

   private byte[] parseAndExecuteCommand(String message) throws JSONException, NoSuchMethodException, IllegalAccessException {
      JSONObject json = new JSONObject(message);
      if (json.getString("command").equals("send-CMMCore-api")) {
         Class coreClass = Magellan.getCore().getClass();
         return parseAPI(coreClass);
      } else if (json.getString("command").equals("run-method")) {
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
         return serialize(result);
      } else {
         throw new RuntimeException("Unknown Command");
      }
   }

   /**
    * Serialize the object in some way that the client will know how to
    * deserialize
    */
   private byte[] serialize(Object o) {
      try {
         JSONObject json = new JSONObject();
         if (o instanceof String) {
            return ((String) o).getBytes();
         } else if (o == null) {
            json.put("Type", "None");
         } else if (o.getClass().equals(Long.class) || o.getClass().equals(Short.class)
                 || o.getClass().equals(Integer.class) || o.getClass().equals(Float.class)
                 || o.getClass().equals(Double.class) || o.getClass().equals(Boolean.class)) {
            json.put("Type", "Primitive");
            json.put("Value", o);
         } else if (o.getClass().equals(TaggedImage.class)) {
            json.put("Type", "Object");
            json.put("Class", "TaggedImage");
            json.put("Value", new JSONObject());
            json.getJSONObject("Value").put("PixelType", (((TaggedImage) o).pix instanceof byte[]) ? "uint8" : "uint16");
            json.getJSONObject("Value").put("tags", ((TaggedImage) o).tags);
            json.getJSONObject("Value").put("pix", encodeArray(((TaggedImage) o).pix));
         } else if (o.getClass().equals(byte[].class)) {
            json.put("Type", "ByteArray");
            json.put("Value", encodeArray(o));
         } else if (o.getClass().equals(double[].class)) {
            json.put("Type", "DoubleArray");
            json.put("Value", encodeArray(o));
         } else if (o.getClass().equals(int[].class)) {
            json.put("Type", "IntArray");
            json.put("Value", encodeArray(o));
         } else if (o.getClass().equals(float[].class)) {
            json.put("Type", "FloatArray");
            json.put("Value", encodeArray(o));
         } else {
            throw new RuntimeException("Unrecognized class return type");
         }
         return json.toString().getBytes();
      } catch (JSONException e) {
         e.printStackTrace();
         Log.log(e);
      }
      throw new RuntimeException("Object type serialization not defined");
   }

   private String encodeArray(Object array) {
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
    * Check if return types and all argument types can be translated
    *
    * @param t
    * @return
    */
   private static boolean isValidMethod(Method t) {
      List<Class> l = new ArrayList<>();
      for (Class c : t.getParameterTypes()) {
         l.add(c);
      }
      l.add(t.getReturnType());
      for (Class c : l) {
         if (!Arrays.asList(SERIALIZABLE_CLASSES).contains(c)) {
            return false;
         }
      }
      return true;
   }

   /**
    * Go through all methods of the given class, filter the ones that can be
    * translated based on argument and return type, and put them into a big JSON
    * array that describes the API
    *
    * @return
    * @throws JSONException
    */
   private static byte[] parseAPI(Class clazz) throws JSONException {
      //Collect all methods whose return types and arguments we know how to translate, and put them in a JSON array describing them
      Predicate<Method> methodFilter = (Method t) -> {
         return isValidMethod(t);
      };

      Method[] m = clazz.getDeclaredMethods();
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
}
