package activeobjectproxytest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.Future;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author arthur
 */
public class TransparentFuture implements InvocationHandler {
   Future future_;

   public static Object newInstance(Future future, Class objClass) {
      return java.lang.reflect.Proxy.newProxyInstance(
              objClass.getClassLoader(),
              objClass.getInterfaces(),
              new TransparentFuture(future));
   }

   private TransparentFuture(Future future) {
      future_ = future;
   }

   public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      Object obj = future_.get();
      return method.invoke(obj, args);
   }
}
