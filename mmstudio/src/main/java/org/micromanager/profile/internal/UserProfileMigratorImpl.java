/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.profile.internal;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.EventBus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.micromanager.UserProfile;
import org.micromanager.profile.UserProfileMigration;

/**
 * Automatically migrate user profile settings.
 *
 * <p>Migrations are run when registered (on currently loaded profiles) and when
 * a profile is loaded.
 *
 * @author Mark A. Tsuchida
 * @see org.micromanager.profile.UserProfileMigration
 */
public final class UserProfileMigratorImpl {
   private UserProfileMigratorImpl() {
   }

   /*
    * As an exception to our normal avoidance of static state, we register
    * profile migrations statically, as they are tied to loaded classes and
    * are thus fundamentally static.
    * We do, however, avoid accessing a static UserProfile.
    */
   private static final Map<Class<?>, List<UserProfileMigration>> migrations_ =
         new HashMap<Class<?>, List<UserProfileMigration>>();

   private static final EventBus bus_ = new EventBus();

   private static class RegisteredEvent
         implements UserProfileMigrationsRegisteredEvent {
      private final Class<?> owner_;
      private final List<UserProfileMigration> migrations_;

      public static UserProfileMigrationsRegisteredEvent create(Class<?> owner,
                                              List<UserProfileMigration> migrations) {
         return new RegisteredEvent(
               owner, migrations);
      }

      private RegisteredEvent(Class<?> owner,
                              List<UserProfileMigration> migrations) {
         owner_ = owner;
         migrations_ = migrations;
      }

      @Override
      public void runMigrations(UserProfile profile) {
         for (UserProfileMigration m : migrations_) {
            runMigration(profile, owner_, m);
         }
      }
   }

   static void registerForEvents(Object recipient) {
      bus_.register(recipient);
   }

   static void unregisterForEvents(Object recipient) {
      bus_.unregister(recipient);
   }

   public static void registerMigrations(Class<?> newOwner,
                                         UserProfileMigration... migrations) {
      Preconditions.checkNotNull(newOwner);
      if (migrations == null) {
         return;
      }
      if (!migrations_.containsKey(newOwner)) {
         migrations_.put(newOwner, new ArrayList<UserProfileMigration>());
      }
      migrations_.get(newOwner).addAll(Arrays.asList(migrations));

      bus_.post(RegisteredEvent.create(newOwner, Arrays.asList(migrations)));
   }

   static void runAllMigrations(UserProfile profile) {
      for (Map.Entry<Class<?>, List<UserProfileMigration>> e : migrations_.entrySet()) {
         for (UserProfileMigration m : e.getValue()) {
            runMigration(profile, e.getKey(), m);
         }
      }
   }

   private static void runMigration(UserProfile profile, Class<?> newOwner,
                                    UserProfileMigration migration) {
      if (((DefaultUserProfile) profile).getSettingsWithoutFallback(newOwner)
            .containsKey(migration.name())) {
         return;
      }
      migration.migrate(((DefaultUserProfile) profile).toPropertyMap(),
            profile.getSettings(newOwner));
   }
}