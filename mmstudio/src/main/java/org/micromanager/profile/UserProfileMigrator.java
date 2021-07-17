/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.profile;

import org.micromanager.profile.internal.UserProfileMigratorImpl;

/**
 * Automatically migrate user profile settings.
 *
 * <p>Registering a {@code UserProfileMigration} with this class will allow
 * automatic migration of profile setting keys from their previous location.
 *
 * <p>See {@link UserProfileMigration} for details.
 *
 * @author Mark A. Tsuchida
 */
public class UserProfileMigrator {
   public static void registerMigrations(Class<?> newOwner,
         UserProfileMigration... migrations) {
      UserProfileMigratorImpl.registerMigrations(newOwner, migrations);
   }
}