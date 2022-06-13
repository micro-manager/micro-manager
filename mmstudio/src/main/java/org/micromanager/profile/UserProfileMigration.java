package org.micromanager.profile;

import org.micromanager.PropertyMap;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Interface for object providing user profile setting migration.
 *
 * <p>As code evolves and gets refactored, the need sometimes arises to change how
 * a particular setting is stored in the user profiles.
 *
 * <p>Objects implementing this interface can be registered with the user profile
 * system, so that migration of settings is performed automatically.
 *
 * <p>Typically, in a class that uses the user profile, you should define the
 * profile keys as a static internal enum, like this:</p>
 * <pre><code>
 * class MyClass {
 *    private static enum ProfileKey {
 *       MY_SETTING_1,
 *       MY_SETTING_2,
 *    }
 *
 *    private static final int MY_SETTING_1_DEFAULT = 42;
 *
 *    // ...
 * }
 * </code></pre>
 *
 * <p>Although this practice is not strictly required (you could use plain string
 * constants for the keys), it avoids the mess of having string constants with
 * more-or-less the same name and content. You can access the settings like
 * this in your code:</p>
 * <pre><code>
 *    int myInteger = profile.getSettings(getClass()).getInteger(
 *          ProfileKey.MY_SETTING_1.name(),
 *          MY_SETTING_1_DEFAULT);
 * </code></pre>
 *
 * <p>Suppose the setting that is now represented by MY_SETTING_1 used to live in
 * a different class, com.foo.Bar, under the key "BarSetting". If this is a
 * nontrivial setting, you would like the old setting to be automatically
 * copied to MY_SETTING_1. To make that happen, extend the enum like this,
 * with an added static initialization to register the migration:</p>
 * <pre><code>
 *    private static enum ProfileKey implements UserProfileMigration {
 *       MY_SETTING_1 {
 *          {@literal @}Override
 *          public void migrate(PropertyMap legacy, MutablePropertyMapView modern) {
 *             // Perform the migration:
 *             PropertyMap fooBarSettings = legacy.getPropertyMap("com.foo.Bar",
 *                   PropertyMaps.emptyPropertyMap());
 *             if (fooBarSettings.containsInteger("BarSetting")) {
 *                modern.putInteger(name(), fooBarSettings.getInteger("BarSetting"));
 *             }
 *          }
 *       },
 *       MY_SETTING_2, // No migration for MY_SETTING_2
 *       ;
 *       {@literal @}Override
 *       public void migrate(PropertyMap legacy, MutablePropertyMapView modern) { }
 *    }
 *
 *    static {
 *       UserProfileMigrator.registerMigrations(MyClass.class, ProfileKey.values());
 *    }
 * </code></pre>
 *
 * <p>That's all you need to do. The user profile system will do the rest of the
 * work, so that by the time you access the setting, it will have been
 * migrated to the new owner class and key.
 *
 * <p>The above example showed a simple 1-to-1 migration (from
 * com.foo.Bar:BarSetting to MyClass:MY_SETTING_1), but it is possibly to
 * derive the new setting value from more than one legacy setting. This can be
 * used to transform the representation of the setting, or to check multiple
 * historical places where the setting was stored.
 *
 * <p>The {@code migrate} method is only ever called if the current setting
 * (defined by the class passed to
 * {@code UserProfileMigration.registerMigration} and the key returned by
 * {@code name()}) is missing in the user profile. Thus, once the migration has
 * happened on a given profile, it will not repeat. (The legacy settings
 * remain in the profile.)
 *
 * <p>All of the migrations for a given owner class must be registered before any
 * of the settings for the class are accessed. Thus, migrations should always
 * be registered in a static initializer, as in the example above.
 *
 * <p>The {@code legacy} property map passed to the {@code migrate} method
 * contains only the settings saved in the specific user profile, and excludes
 * settings in the global or other fallback profile, so if the user was using
 * the global default, the migration will be skipped in the above example code.
 *
 * @author Mark A. Tsuchida
 */
public interface UserProfileMigration {
   /**
    * The current (modern) key under which the relevant setting is stored.
    *
    * <p>If you are using an enum class to implement {@code ProfileMigration},
    * this method will be automatically available (see {@link Enum#name()}).
    *
    * <p>If you are implementing a migration as a plain object (not recommended),
    * you will need to implement this method to return the key string.
    *
    * @return the profile setting key
    */
   String name();

   /**
    * Perform a migration from old settings storage.
    *
    * @param legacy a property map containing existing settings from which to
    *               migrate, whose keys are the canonical names of the owner classes and
    *               whose values are nested property maps containing the settings.
    * @param modern the user profile settings for the class under which this
    *               migration was registered, to which the migrated settings should be saved
    */
   void migrate(PropertyMap legacy, MutablePropertyMapView modern);
}