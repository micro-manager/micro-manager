/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.profile.internal;

import org.micromanager.UserProfile;

/** @author Mark A. Tsuchida */
public interface UserProfileMigrationsRegisteredEvent {
  void runMigrations(UserProfile profile);
}
