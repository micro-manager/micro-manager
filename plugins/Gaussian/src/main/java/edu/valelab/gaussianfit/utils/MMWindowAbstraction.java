/*
 * 
 */
package edu.valelab.gaussianfit.utils;

import ij.ImagePlus;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility class that uses introspection to see if Micro-Manager is available
 * This is so that the plugin can also work when MM is not available
 * Can also be used as an example for introspection
 * 
 * @author nico
 */
public class MMWindowAbstraction {

   private static Method mIsMMWindow_;
   private static Method mGetNumberOfPositions_;
   private static Method mSetPosition_;
   private static Method mGetPosition_;
   private static Constructor[] aCTors_;
   private static boolean mmAvailable_ = true;

   //public MMWindowAbstraction() {
      static {
         try {
         Class<?> mmWin = Class.forName("org.micromanager.api.MMWindow");
         aCTors_ = mmWin.getDeclaredConstructors();
         aCTors_[0].setAccessible(true);
         //Object mw = aCTors[0].newInstance(siPlus);
         Method[] allMethods = mmWin.getDeclaredMethods();

         // assemble all methods we need
         for (Method m : allMethods) {
            String mname = m.getName();
            if (mname.startsWith("isMMWindow")
                    && m.getGenericReturnType() == boolean.class) {
               mIsMMWindow_ = m;
               mIsMMWindow_.setAccessible(true);
            }
            if (mname.startsWith("getNumberOfPositions")) {
               mGetNumberOfPositions_ = m;
               mGetNumberOfPositions_.setAccessible(true);
            }
            if (mname.startsWith("setPosition")) {
               mSetPosition_ = m;
               mSetPosition_.setAccessible(true);
            }
            if (mname.startsWith("getPosition")) {
               mGetPosition_ = m;
               mGetPosition_.setAccessible(true);
            }

         }
      } catch (ClassNotFoundException cnf) {
         mmAvailable_ = false;
         // we do not have access to Micro-Manager code
      }
   }
   
   public static boolean isMMWindow(ImagePlus siPlus) {
         if (!mmAvailable_) {
            return false;
         }
      try {
         Object mw = aCTors_[0].newInstance(siPlus);
         if (mIsMMWindow_ != null && (Boolean) mIsMMWindow_.invoke(mw)) {
            return true;
         }
      } catch (InstantiationException ex) {
      } catch (IllegalAccessException ex) {
      } catch (IllegalArgumentException ex) {
      } catch (InvocationTargetException ex) {
      }
      return false;
   }
   
   public static int getNumberOfPositions(ImagePlus siPlus) {
      int nrPos = 1;
      if (!mmAvailable_) {
         return nrPos;
      }
      try {
         Object mw = aCTors_[0].newInstance(siPlus);

         if (mGetNumberOfPositions_ != null) {
            nrPos = (Integer) mGetNumberOfPositions_.invoke(mw);
         }
      } catch (InstantiationException ex) {
      } catch (IllegalAccessException ex) {
      } catch (IllegalArgumentException ex) {
      } catch (InvocationTargetException ex) {
      }

      return nrPos;
   }

   public static void setPosition(ImagePlus siPlus, int position) {
      if (!mmAvailable_) {
         return;
      }
      try {
         Object mw = aCTors_[0].newInstance(siPlus);
         if (mSetPosition_ != null) {
            mSetPosition_.invoke(mw, position);
         }
      } catch (InstantiationException ex) {
      } catch (IllegalAccessException ex) {
      } catch (IllegalArgumentException ex) {
      } catch (InvocationTargetException ex) {
      }

   }
   
   
   public static int getPosition(ImagePlus siPlus) {
      int pos = 1;
      if (!mmAvailable_) {
         return pos;
      }
      try {
         Object mw = aCTors_[0].newInstance(siPlus);

         if (mGetPosition_ != null) {
            pos = (Integer) mGetPosition_.invoke(mw);
         }
      } catch (InstantiationException ex) {
      } catch (IllegalAccessException ex) {
      } catch (IllegalArgumentException ex) {
      } catch (InvocationTargetException ex) {
      }

      return pos;
   }
   
   
}
