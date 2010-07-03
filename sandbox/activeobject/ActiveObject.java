/*
 * Based on Ben Pryor's implementation of Active Objects.
 * http://tinyurl.com/2fk353s
 * and Jonas Bonér's Akka.
 *
 */
package activeobjectproxytest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

/**
 *
 * @author arthur
 */
public class ActiveObject implements InvocationHandler {

   private List callQueue = new ArrayList();
   private Object serviceObject;
   private boolean stopRequest_ = false;
   
   public static Object newInstance(Object obj) {
      Class objClass = obj.getClass();
      Class [] interfaces = objClass.getInterfaces();
      if (interfaces.length == 0)
         interfaces = new Class [] {objClass};
      return java.lang.reflect.Proxy.newProxyInstance(
              objClass.getClassLoader(),
              interfaces,
              new ActiveObject(obj));
   }

   public static Object newInstance(Class objClass) throws InstantiationException, IllegalAccessException {
      return java.lang.reflect.Proxy.newProxyInstance(
              objClass.getClassLoader(),
              objClass.getInterfaces(),
              new ActiveObject(objClass.newInstance()));
   }

   private interface Stoppable {
      public void _stop();
   }

   private ActiveObject(Object serviceObject) {
      this.serviceObject = serviceObject;
      new WorkerThread().start();
   }

   private synchronized void put(Future future) {
      callQueue.add(future);
      notifyAll();
   }

   private synchronized FutureTask get() {
      while (callQueue.size() == 0) {
         try {
            wait();
         } catch (InterruptedException e) {
         }
      }

      return (FutureTask) callQueue.remove(0);
   }

   public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      final Invokable invokable = new Invokable();
      invokable.method = method;
      invokable.args = args;
      if (method.getName().contentEquals("_stop")) {
         stopRequest_ = true;
         return null;
      } else {
         FutureTask future = new FutureTask(invokable);
         put(future);

         if (method.getReturnType() == void.class)
            return null;
         else
            return future.get(); //TransparentFuture will require CGLib or AspectWerkz
      }
   }

   private class WorkerThread extends Thread {
      @Override
      public void run() {
         while (!stopRequest_) {
            FutureTask future = get();
            try {
               future.run();
            } catch (Throwable t) {
               //Do nothing here:
               //Future.get() will return an ExecutionException to
               //the caller.
            }
         }
      }
   }

   private class Invokable implements Callable<Object> {
      public Method method;
      public Object[] args;

      public Object call() throws Exception {
         try {
            return method.invoke(serviceObject, args);
         } catch (InvocationTargetException ex) {
            Throwable th = ex.getTargetException();
            if (th instanceof Exception)
               throw (Exception) th;
            else
               throw new Exception(th.toString());
         }
      }
   }

   
}
