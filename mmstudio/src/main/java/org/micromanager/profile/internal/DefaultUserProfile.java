/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.profile.internal;

import static org.micromanager.PropertyMaps.emptyPropertyMap;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.io.IOException;
import java.util.UUID;
import org.micromanager.EventPublisher;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.UserProfile;
import org.micromanager.internal.propertymap.DefaultPropertyMap;
import org.micromanager.internal.utils.EventBusExceptionLogger;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Implementation of UserProfile.
 *
 * @author Mark A. Tsuchida
 */
public class DefaultUserProfile implements UserProfile, EventPublisher {

  /*
   * TODO [Performance]
   *
   * It should be determined whether it is worth optimizing by using a mutable
   * internal map. However, note that although we currently "copy" the entire
   * map, each of the per-owner property maps are not copied unless modified.
   * Let's see how many owners we end up seeing. If we do need to optimize,
   * the mutable property map should also be thread safe, perhaps by using
   * ConcurrentHashMap.
   */

  // TODO currently we store admin and uuid for the sole purpose of getting
  // the up-to-date profile name. Is there a cleaner way?
  private final UserProfileAdmin admin_;
  private final UUID uuid_;

  private PropertyMap ownersAndProperties_;
  private DefaultUserProfile fallbackProfile_;

  private ProfileSaver saver_;

  private final EventBus bus_ = new EventBus(EventBusExceptionLogger.getInstance());

  static interface Editor {
    PropertyMap edit(PropertyMap input);
  }

  public static DefaultUserProfile create(
      UserProfileAdmin admin, UUID profileUUID, PropertyMap settings) {
    DefaultUserProfile ret = new DefaultUserProfile(admin, profileUUID, settings);

    UserProfileMigratorImpl.registerForEvents(ret);
    UserProfileMigratorImpl.runAllMigrations(ret);

    return ret;
  }

  private DefaultUserProfile(UserProfileAdmin admin, UUID profileUUID, PropertyMap settings) {
    admin_ = admin;
    uuid_ = profileUUID;
    ownersAndProperties_ = settings;
  }

  void setSaver(ProfileSaver saver) {
    saver_ = saver;
  }

  /**
   * Stop autosaving and stop performing migrations.
   *
   * @throws InterruptedException
   */
  public void close() throws InterruptedException {
    if (saver_ != null) {
      saver_.stop(); // This will cause a final save to file.
    }
    UserProfileMigratorImpl.unregisterForEvents(this);
  }

  void setFallbackProfile(UserProfile fallback) {
    fallbackProfile_ = (DefaultUserProfile) fallback;
  }

  @Override
  public void registerForEvents(Object recipient) {
    bus_.register(recipient);
  }

  @Override
  public void unregisterForEvents(Object recipient) {
    bus_.unregister(recipient);
  }

  public PropertyMap getSettingsWithoutFallback(Class<?> owner) {
    return ownersAndProperties_.getPropertyMap(owner.getCanonicalName(), emptyPropertyMap());
  }

  // ALL reads are via this method
  synchronized PropertyMap getProperties(Class<?> owner) {
    // Chain the per-owner maps at the time of read access, to obtain an
    // up-to-date chained map.
    return fallbackProfile_ == null
        ? getSettingsWithoutFallback(owner)
        : ((DefaultPropertyMap) getSettingsWithoutFallback(owner))
            .createChainedView(fallbackProfile_.getSettingsWithoutFallback(owner));
  }

  // ALL modifications are via this method.
  // Note we do NOT include the fallback items!
  synchronized void editProperty(Class<?> owner, Editor editor) {
    String ownerKey = owner.getCanonicalName();
    ownersAndProperties_ =
        ownersAndProperties_
            .copyBuilder()
            .putPropertyMap(
                ownerKey,
                editor.edit(ownersAndProperties_.getPropertyMap(ownerKey, emptyPropertyMap())))
            .build();
    bus_.post(UserProfileChangedEvent.create());
  }

  // This does not include the fallback preferences. Anybody who needs the
  // combination will have to merge the property maps for each owner.
  public synchronized PropertyMap toPropertyMap() {
    return ownersAndProperties_;
  }

  @Subscribe
  public void onEvent(UserProfileMigrationsRegisteredEvent e) {
    e.runMigrations(this);
  }

  //
  // UserProfile interface implementation
  //

  @Override
  public String getProfileName() {
    if (uuid_ == null) {
      return "<name unavailable>";
    }
    try {
      return admin_.getProfileName(uuid_);
    } catch (IOException e) {
      return "<name unavailable>";
    }
  }

  @Override
  public MutablePropertyMapView getSettings(Class<?> owner) {
    return ProfilePropertyMapView.create(this, owner);
  }

  @Override
  public synchronized void clearSettingsForAllClasses() {
    ownersAndProperties_ = PropertyMaps.emptyPropertyMap();
  }

  //
  // Deprecated UserProfile methods
  //

  @Override
  @Deprecated
  public String getString(Class<?> c, String key, String fallback) {
    return getProperties(c).getString(key, fallback);
  }

  @Override
  @Deprecated
  public String[] getStringArray(Class<?> c, String key, String[] fallback) {
    return getProperties(c).getStringArray(key, fallback);
  }

  @Override
  @Deprecated
  public void setString(Class<?> c, final String key, final String value) {
    editProperty(
        c,
        new Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putString(key, value).build();
          }
        });
  }

  @Override
  @Deprecated
  public void setStringArray(Class<?> c, final String key, final String[] value) {
    editProperty(
        c,
        new Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putStringArray(key, value).build();
          }
        });
  }

  @Override
  @Deprecated
  public Integer getInt(Class<?> c, String key, Integer fallback) {
    return getProperties(c).getInt(key, fallback);
  }

  @Override
  @Deprecated
  public Integer[] getIntArray(Class<?> c, String key, Integer[] fallback) {
    return getProperties(c).getIntArray(key, fallback);
  }

  @Override
  @Deprecated
  public void setInt(Class<?> c, final String key, final Integer value) {
    editProperty(
        c,
        new Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putInt(key, value).build();
          }
        });
  }

  @Override
  @Deprecated
  public void setIntArray(Class<?> c, final String key, final Integer[] value) {
    editProperty(
        c,
        new Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putIntArray(key, value).build();
          }
        });
  }

  @Override
  @Deprecated
  public Long getLong(Class<?> c, String key, Long fallback) {
    return getProperties(c).getLong(key, fallback);
  }

  @Override
  @Deprecated
  public Long[] getLongArray(Class<?> c, String key, Long[] fallback) {
    return getProperties(c).getLongArray(key, fallback);
  }

  @Override
  @Deprecated
  public void setLong(Class<?> c, final String key, final Long value) {
    editProperty(
        c,
        new Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putLong(key, value).build();
          }
        });
  }

  @Override
  @Deprecated
  public void setLongArray(Class<?> c, final String key, final Long[] value) {
    editProperty(
        c,
        new Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putLongArray(key, value).build();
          }
        });
  }

  @Override
  @Deprecated
  public Double getDouble(Class<?> c, String key, Double fallback) {
    return getProperties(c).getDouble(key, fallback);
  }

  @Override
  @Deprecated
  public Double[] getDoubleArray(Class<?> c, String key, Double[] fallback) {
    return getProperties(c).getDoubleArray(key, fallback);
  }

  @Override
  @Deprecated
  public void setDouble(Class<?> c, final String key, final Double value) {
    editProperty(
        c,
        new Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putDouble(key, value).build();
          }
        });
  }

  @Override
  @Deprecated
  public void setDoubleArray(Class<?> c, final String key, final Double[] value) {
    editProperty(
        c,
        new Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putDoubleArray(key, value).build();
          }
        });
  }

  @Override
  @Deprecated
  public Boolean getBoolean(Class<?> c, String key, Boolean fallback) {
    return getProperties(c).getBoolean(key, fallback);
  }

  @Override
  @Deprecated
  public Boolean[] getBooleanArray(Class<?> c, String key, Boolean[] fallback) {
    return getProperties(c).getBooleanArray(key, fallback);
  }

  @Override
  @Deprecated
  public void setBoolean(Class<?> c, final String key, final Boolean value) {
    editProperty(
        c,
        new Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putBoolean(key, value).build();
          }
        });
  }

  @Override
  @Deprecated
  public void setBooleanArray(Class<?> c, final String key, final Boolean[] value) {
    editProperty(
        c,
        new Editor() {
          @Override
          public PropertyMap edit(PropertyMap input) {
            return input.copyBuilder().putBooleanArray(key, value).build();
          }
        });
  }

  @Deprecated
  public <T> T getLegacySerializedObject(Class<?> c, String key, T fallback) {
    return ((DefaultPropertyMap) getProperties(c)).getLegacySerializedObject(key, fallback);
  }
}
