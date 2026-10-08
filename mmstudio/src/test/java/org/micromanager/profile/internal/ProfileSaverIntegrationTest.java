package org.micromanager.profile.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;
import org.micromanager.PropertyMaps;

public class ProfileSaverIntegrationTest {
   @Test
   public void profileCloseSavesLatestSettingsThroughRealEvents() throws Exception {
      File file = Files.createTempFile("profile-saver", ".json").toFile();
      ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
      DefaultUserProfile profile = DefaultUserProfile.create(null,
            UUID.randomUUID(), PropertyMaps.emptyPropertyMap());
      ProfileSaver saver = ProfileSaver.create(profile, () -> {
         try {
            profile.toPropertyMap().saveJSON(file, true, false);
         } catch (IOException e) {
            throw new AssertionError(e);
         }
      }, executor);
      profile.setSaver(saver);
      boolean closed = false;
      try {
         profile.getSettings(getClass()).putInteger("value", 1);
         profile.getSettings(getClass()).putInteger("value", 2);
         profile.close();
         closed = true;
         executor.shutdown();
         Assert.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
         Assert.assertEquals(2, PropertyMaps.loadJSON(file)
               .getPropertyMap(getClass().getCanonicalName(), PropertyMaps.emptyPropertyMap())
               .getInteger("value", -1));
         profile.getSettings(getClass()).putInteger("value", 3);
         Assert.assertTrue(executor.getQueue().isEmpty());
         Assert.assertEquals(2, PropertyMaps.loadJSON(file)
               .getPropertyMap(getClass().getCanonicalName(), PropertyMaps.emptyPropertyMap())
               .getInteger("value", -1));
      } finally {
         executor.shutdownNow();
         if (!closed) {
            UserProfileMigratorImpl.unregisterForEvents(profile);
         }
         Files.deleteIfExists(file.toPath());
      }
   }
}
