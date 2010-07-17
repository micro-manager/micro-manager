/*
 *
 */
package activeobjectproxytest;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import net.sf.cglib.asm.Type;

/**
 *
 * @author arthur
 */
public class ActiveObject implements MethodInterceptor {
   private ExecutorService executorService;
   private Class theClass;

   public static Object newInstance(Class objClass) {
      return Enhancer.create(objClass, new ActiveObject());
   }

   public Object intercept(final Object o, final Method method, final Object[] args, final MethodProxy mp) throws Throwable {

      if (method.getName().contentEquals("_stop")) {
         executorService.shutdown();
         return null;
      } else {
         Future<Object> future = executorService.submit(new Callable() {
            public Object call() throws Exception {
               try {
                  return mp.invokeSuper(o, args);
               } catch (Throwable ex) {
                  if (ex instanceof Exception)
                     throw (Exception) ex;
                  else
                     throw new Exception(ex.getMessage());
               }
            }
         });

         if (method.getReturnType() == void.class)
            return null;
         else {
            Class returnClass = future.getClass().getTypeParameters()[0].getClass();
            return TransparentFuture.newInstance(future, returnClass); //TransparentFuture will require CGLib or AspectWerkz
         }
      }
   }

   private ActiveObject() {
      executorService = Executors.newSingleThreadExecutor();
   }
}
