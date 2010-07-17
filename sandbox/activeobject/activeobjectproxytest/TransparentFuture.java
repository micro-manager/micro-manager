package activeobjectproxytest;

import java.lang.reflect.Method;
import java.util.concurrent.Future;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InterfaceMaker;
import net.sf.cglib.proxy.InvocationHandler;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;
import net.sf.cglib.proxy.Proxy;

/**
 *
 * @author arthur
 */
public class TransparentFuture implements InvocationHandler {
   Future future_;

   public static Object newInstance(Future future, Class objClass) {
      InterfaceMaker interfaceMaker = new InterfaceMaker();
      interfaceMaker.add(objClass);
      Class theFakeInterface = interfaceMaker.create();
      System.out.println("hi");
      return Proxy.newProxyInstance(objClass.getClassLoader(), new Class [] {theFakeInterface}, new TransparentFuture(future));
      
   }

   public TransparentFuture(Future future) {
      future_ = future;
   }

   public Object invoke(Object o, Method method, Object[] os) throws Throwable {
      Object obj = future_.get();
      return method.invoke(obj, os);
   }
}
