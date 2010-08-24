/*
 * Based on Ben Pryor's implementation of Active Objects.
 * http://tinyurl.com/2fk353s
 * and Jonas Bonér's Akka.
 *
 */
package org.micromanager.activeobjects;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.locks.ReentrantLock;

/**
 *
 * @author arthur
 */
public class ActiveObject implements InvocationHandler {

   private List callQueue_ = new ArrayList();
   private Object serviceObject_;
   private boolean stopRequest_ = false;
   ReentrantLock pauseLock;

   private ActiveObject(Object serviceObject) {
      this.serviceObject_ = serviceObject;
      pauseLock = new ReentrantLock();
      new WorkerThread().start();
   }

   public static Object newInstance(Object obj) {
      Class objClass = obj.getClass();
      Class[] interfaces = objClass.getInterfaces();
      if (interfaces.length == 0) {
         interfaces = new Class[]{objClass};
      }
      return Proxy.newProxyInstance(
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

   private synchronized void put(Future future) {
      callQueue_.add(future);
      notifyAll();
   }

   private synchronized void clear() {
      callQueue_.clear();
   }

   private synchronized void requestStop() {
      stopRequest_ = true;
   }

   private synchronized boolean stopRequested() {
      return stopRequest_;
   }

   private synchronized FutureTask get() {
      while (callQueue_.isEmpty()) {
         try {
            wait();
         } catch (InterruptedException e) {
         }
      }
      return (FutureTask) callQueue_.remove(0);
   }

   public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      final Invokable invokable = new Invokable();
      if (method.getName().contentEquals("stop")) {
         requestStop();
         return null;
      } else if (method.getName().contentEquals("pause")) {
         if (!pauseLock.isHeldByCurrentThread()) {
            pauseLock.lock();
         }
         return null;
      } else if (method.getName().contentEquals("resume")) {
         if (pauseLock.isLocked()) {
            pauseLock.unlock();
         }
         return null;
      } else {
         invokable.method = method;
         invokable.args = args;
         FutureTask future = new FutureTask(invokable);
         put(future);
         if (method.getReturnType() == void.class) {
            return null;
         } else {
            return future.get(); //TransparentFuture will require CGLib or AspectWerkz
         }
      }
   }

   private class WorkerThread extends Thread {

      @Override
      public void run() {
         while (!stopRequested()) {
            pauseLock.lock();
            pauseLock.unlock();
            FutureTask future = get();
            try {
               future.run();
            } catch (Throwable t) {
            }
         }
      }
   }

   private class Invokable implements Callable<Object> {
      public Method method;
      public Object[] args;

      public Object call() throws Exception {
         try {
            return method.invoke(serviceObject_, args);
         } catch (InvocationTargetException ex) {
            Throwable th = ex.getTargetException();
            if (th instanceof Exception) {
               throw (Exception) th;
            } else {
               throw new Exception(th.toString());
            }
         }
      }
   }
}
