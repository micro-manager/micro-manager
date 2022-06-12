/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.profile.internal;

/**
 * @author mark
 */
public class UserProfileChangedEvent {
   private static final UserProfileChangedEvent INSTANCE = new UserProfileChangedEvent();

   public static UserProfileChangedEvent create() {
      return INSTANCE;
   }

   private UserProfileChangedEvent() {
   }
}