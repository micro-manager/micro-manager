///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.quickaccess;

import javax.swing.Icon;

import org.micromanager.PropertyMap;

/**
 * The QuickAccessManager provides API access to the Quick Access Panel(s).
 * It can accessed via Studio.quick() or Studio.getQuickAccessManager().
 */
public interface QuickAccessManager {
   /**
    * Show the Quick Access Panels. This loads all saved information in the
    * user's profile relating to Quick Access Panels and ensures all such
    * panels are visible. If there is no saved information in the profile, then
    * a new, blank panel will be created.
    */
   public void showPanels();

   /**
    * Generate an Icon based on information contained in the provided
    * PropertyMap. This method should be used by WidgetPlugins that support
    * customizable icons (i.e. their getCanCustomizeIcon() method returns true)
    * to insert the custom icon into their UI. Note that just because the UI
    * *can* have a custom icon does not necessarily mean that it *will* have
    * a custom icon; in such situations this method will return the
    * defaultIcon value instead.
    * @param config A PropertyMap potentially containing information about
    *        user-customized icons.
    * @param defaultIcon Icon to use if no custom icon information is set.
    * @return A custom Icon derived from the PropertyMap, or the defaultIcon
    *         if no icon information is available.
    */
   public Icon getCustomIcon(PropertyMap config, Icon defaultIcon);
}
