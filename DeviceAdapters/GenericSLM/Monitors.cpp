// COPYRIGHT:     (c) 2009-2015 Regents of the University of California
//                (c) 2016 Open Imaging, Inc.
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Mark Tsuchida, 2016
//                Based on older code by Arthur Edelstein, 2009

#include "Monitors.h"


std::vector<std::string> GetMonitorNames(bool excludePrimary,
      bool attachedOnly)
{
   std::vector<std::string> result;

   DWORD adapterNum = 0;
   DISPLAY_DEVICE displayAdapter;
   ::ZeroMemory(&displayAdapter, sizeof(displayAdapter));
   displayAdapter.cb = sizeof(displayAdapter);
   while (::EnumDisplayDevicesA(0, adapterNum++, &displayAdapter, 0))
   {
      // Ignore virtual mirroring devices
      if (displayAdapter.StateFlags & DISPLAY_DEVICE_MIRRORING_DRIVER)
         continue;

      if (excludePrimary &&
            (displayAdapter.StateFlags & DISPLAY_DEVICE_PRIMARY_DEVICE))
         continue;

      if (attachedOnly &&
            !(displayAdapter.StateFlags & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP))
         continue;

      // Note: We could have an inner loop here, calling EnumDisplayDevices()
      // with the first parameter set to displayAdapter. This will enumerate
      // monitors (e.g. '\\.\DISPLAY1\monitor0'). It is not clear if that adds
      // anything, though - in my test, an SLM plugged into the same graphics
      // board as the main monitor got a separate DISPLAY number.

      // In theory, we ought to use EnumDisplayDevicesW() and convert the
      // device name to UTF-8. But in practice the device name is a string
      // like '\\.\DISPLAY1', so let's assume we're safe to rely on it being
      // ASCII.
      result.push_back(displayAdapter.DeviceName);
   }

   return result;
}


// Wrapper, since ChangeDisplaySettingsEx() has to be called in a specific way
static bool CallChangeDisplaySettingsExA(const std::string& monitorName,
   DEVMODE* deviceMode)
{
   // Don't ask me why, but it only works if two calls are made, first to set
   // in the registry and then to apply the settings.
   // Thanks to http://stackoverflow.com/a/19665843

   LONG result = ::ChangeDisplaySettingsExA(monitorName.c_str(), deviceMode, 0,
         CDS_UPDATEREGISTRY | CDS_NORESET, 0);
   if (result != DISP_CHANGE_SUCCESSFUL)
      return false;

   result = ::ChangeDisplaySettingsExA(0, 0, 0, 0, 0);
   return (result == DISP_CHANGE_SUCCESSFUL);
}


bool DetachMonitorFromDesktop(const std::string& monitorName)
{
   DEVMODE deviceMode;
   ::ZeroMemory(&deviceMode, sizeof(deviceMode));
   deviceMode.dmSize = sizeof(deviceMode);
   // Though poorly documented, setting these 3 fields to zero will detach
   // the display.
   deviceMode.dmFields = DM_PELSWIDTH | DM_PELSHEIGHT | DM_POSITION;

   return CallChangeDisplaySettingsExA(monitorName, &deviceMode);
}


bool AttachMonitorToDesktop(const std::string& monitorName, LONG posX, LONG posY)
{
   DEVMODE deviceMode;
   ::ZeroMemory(&deviceMode, sizeof(deviceMode));
   deviceMode.dmSize = sizeof(deviceMode);
   deviceMode.dmFields = DM_POSITION;
   deviceMode.dmPosition.x = posX;
   deviceMode.dmPosition.y = posY;

   return CallChangeDisplaySettingsExA(monitorName, &deviceMode);
}


bool GetMonitorRect(const std::string& monitorName, LONG& x, LONG& y, LONG& w, LONG& h)
{
   DEVMODE deviceMode;
   ::ZeroMemory(&deviceMode, sizeof(deviceMode));
   deviceMode.dmSize = sizeof(deviceMode);
   if (!EnumDisplaySettingsExA(monitorName.c_str(), ENUM_CURRENT_SETTINGS, &deviceMode, 0))
      if (!EnumDisplaySettingsExA(monitorName.c_str(), ENUM_REGISTRY_SETTINGS, &deviceMode, 0))
         return false;

   x = deviceMode.dmPosition.x;
   y = deviceMode.dmPosition.y;
   w = deviceMode.dmPelsWidth;
   h = deviceMode.dmPelsHeight;
   return true;
}


// Find the top-right corner of the right-most monitor, which is a good place
// to position the SLM monitor.
void GetRightmostMonitorTopRight(const std::vector<std::string>& monitorNames,
      LONG& x, LONG& y)
{
   LONG rightMostX = LONG_MIN;
   LONG rightX = 0, topY = 0;
   for (std::vector<std::string>::const_iterator it = monitorNames.begin(),
         end = monitorNames.end(); it != end; ++it)
   {
      LONG x, y, w, h;
      if (!GetMonitorRect(*it, x, y, w, h))
         continue; // TODO Report?
      if (x + w > rightMostX)
      {
         rightMostX = x + w;
         rightX = rightMostX;
         topY = y;
      }
   }
   x = rightX;
   y = topY;
}


bool GetBoundingRect(const std::vector<std::string>& monitorNames,
      RECT& rect)
{
   rect.left = rect.top = LONG_MAX;
   rect.right = rect.bottom = LONG_MIN;

   for (std::vector<std::string>::const_iterator it = monitorNames.begin(),
         end = monitorNames.end(); it != end; ++it)
   {
      LONG x, y, w, h;
      if (!GetMonitorRect(*it, x, y, w, h))
         continue; // TODO Report?

      rect.left = min(rect.left, x);
      rect.top = min(rect.top, y);
      rect.right = max(rect.right, x + w);
      rect.bottom = max(rect.bottom, y + h);
   }

   return rect.left < LONG_MAX;
}
