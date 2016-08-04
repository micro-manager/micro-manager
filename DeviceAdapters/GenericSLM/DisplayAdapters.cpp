#include "DisplayAdapters.h"
#include <windows.h>

RECT getViewingMonitorsBounds(vector<MonitorDevice> displays)
{
   RECT result;
   result.left = LONG_MAX;
   result.top = LONG_MAX;
   result.bottom = LONG_MIN;
   result.right = LONG_MIN;

   MonitorDevice disp;

   for (unsigned i=0;i<displays.size();++i)
   {
      disp = displays[i];
      if (! disp.isSLM && ! disp.isDisabled)
      {
         result.left = min(result.left, disp.x);
         result.top = min(result.top, disp.y);
         result.right = max(result.right, disp.x + disp.width);
         result.bottom = max(result.bottom, disp.y + disp.height);
      }
   }

   return result;
}


POINT getNextDisplayPosition(vector<MonitorDevice> displays)
{
   POINT result;
   result.x = LONG_MIN;
   result.y = LONG_MIN;

   MonitorDevice disp;

   for (unsigned i=0;i<displays.size();++i)
   {
      disp = displays[i];
      if (! disp.isDisabled)
      {
         if ((disp.x + disp.width) > result.x)
            result.x = disp.x + disp.width;
         result.y = disp.y;
      }
   }

   return result;
}


void updateMonitorRect(MonitorDevice * display)
{
   DEVMODE dm;
   ZeroMemory(&dm, sizeof(dm));
   dm.dmSize = sizeof(dm);

   LPCSTR cardName = display->cardName.c_str();

   if (EnumDisplaySettingsEx(cardName, ENUM_CURRENT_SETTINGS, &dm, 0) == FALSE)
      EnumDisplaySettingsEx(cardName, ENUM_REGISTRY_SETTINGS, &dm, 0);
   display->x = dm.dmPosition.x;
   display->y = dm.dmPosition.y;
   display->width = dm.dmPelsWidth;
   display->height = dm.dmPelsHeight;
}


void updateMonitorRects(vector<MonitorDevice> * displays)
{
   for(unsigned int i=0;i<displays->size();++i)
   {
      updateMonitorRect(&(displays->at(i)));
   }
}


vector<MonitorDevice> getMonitorInfo()
{
   // collect system and monitor information, and display it using a message box

   TCHAR msg[10000] = _T("");
   vector<MonitorDevice> displays;
   DISPLAY_DEVICE dd;
   dd.cb = sizeof(dd);
   DWORD dev = 0; // device index
   int id = 1; // monitor number, as used by Display Properties > Settings

   while (EnumDisplayDevices(0, dev, &dd, 0))
   {
      MonitorDevice thisDev;
      if (!(dd.StateFlags & DISPLAY_DEVICE_MIRRORING_DRIVER))
      {
         // ignore virtual mirror displays

         // get information about the monitor attached to this display adapter. dualhead cards
         // and laptop video cards can have multiple monitors attached

         DISPLAY_DEVICE ddMon;
         ZeroMemory(&ddMon, sizeof(ddMon));
         ddMon.cb = sizeof(ddMon);
         DWORD devMon = 0;

         // please note that this enumeration may not return the correct monitor if multiple monitors
         // are attached. this is because not all display drivers return the ACTIVE flag for the monitor
         // that is actually active
         while (EnumDisplayDevices(dd.DeviceName, devMon, &ddMon, 0))
         {
            if (ddMon.StateFlags & DISPLAY_DEVICE_ACTIVE)
               break;

            devMon++;
         }

         if (!*ddMon.DeviceString)
         {
            EnumDisplayDevices(dd.DeviceName, 0, &ddMon, 0);
            if (!*ddMon.DeviceString)
               lstrcpy(ddMon.DeviceString, _T("Default Monitor"));
         }

         // get information about the display's position and the current display mode
         DEVMODE dm;
         ZeroMemory(&dm, sizeof(dm));
         dm.dmSize = sizeof(dm);
         if (EnumDisplaySettingsEx(dd.DeviceName, ENUM_CURRENT_SETTINGS, &dm, 0) == FALSE)
            EnumDisplaySettingsEx(dd.DeviceName, ENUM_REGISTRY_SETTINGS, &dm, 0);

         // get the monitor handle and workspace
         HMONITOR hm = 0;
         MONITORINFO mi;
         ZeroMemory(&mi, sizeof(mi));
         mi.cbSize = sizeof(mi);
         if (dd.StateFlags & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP)
         {
            // display is enabled. only enabled displays have a monitor handle
            POINT pt = { dm.dmPosition.x, dm.dmPosition.y };
            hm = MonitorFromPoint(pt, MONITOR_DEFAULTTONULL);
            if (hm)
               GetMonitorInfo(hm, &mi);
         }

         // format information about this monitor
         TCHAR buf[1000];

         // 1. MyMonitor on MyVideoCard
         wsprintf(buf, _T("%d. %s on %s\r\n"), id, ddMon.DeviceName, dd.DeviceName);
         lstrcat(msg, buf);

         // status flags: primary, disabled, removable
         buf[0] = _T('\0');
         if (!(dd.StateFlags & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP))
            lstrcat(buf, _T("disabled, "));
         else if (dd.StateFlags & DISPLAY_DEVICE_PRIMARY_DEVICE)
            lstrcat(buf, _T("primary, "));
         if (dd.StateFlags & DISPLAY_DEVICE_REMOVABLE)
            lstrcat(buf, _T("removable, "));

         if (*buf)
         {
            buf[lstrlen(buf) - 2] = _T('\0');
            lstrcat(buf, _T("\r\n"));
            lstrcat(msg, buf);
         }

         // width x height @ x,y - bpp - refresh rate
         // note that refresh rate information is not available on Win9x
         wsprintf(buf, _T("%d x %d @ %d,%d - %d-bit - %d Hz\r\n"), dm.dmPelsWidth, dm.dmPelsHeight,
               dm.dmPosition.x, dm.dmPosition.y, dm.dmBitsPerPel, dm.dmDisplayFrequency);
         lstrcat(msg, buf);

         if (hm)
         {
            // workspace and monitor handle

            // workspace: x,y - x,y HMONITOR: handle
            wsprintf(buf, _T("workspace: %d,%d - %d,%d HMONITOR: 0x%X\r\n"), mi.rcWork.left,
                  mi.rcWork.top, mi.rcWork.right, mi.rcWork.bottom, hm);
            lstrcat(msg, buf);
         }

         // device name
         wsprintf(buf, _T("Device: %s\r\n\r\n"), *ddMon.DeviceName ? ddMon.DeviceName : dd.DeviceName);
         lstrcat(msg, buf);

         id++;

         thisDev.deviceType = string(ddMon.DeviceString);
         thisDev.cardType = string(dd.DeviceString);
         thisDev.deviceName = string(ddMon.DeviceName);
         thisDev.cardName = string(dd.DeviceName);
         thisDev.x = dm.dmPosition.x;
         thisDev.y = dm.dmPosition.y;
         thisDev.width = dm.dmPelsWidth;
         thisDev.height = dm.dmPelsHeight;
         thisDev.isPrimary = (dd.StateFlags & DISPLAY_DEVICE_PRIMARY_DEVICE) != 0;
         thisDev.isDisabled = !(dd.StateFlags & DISPLAY_DEVICE_ATTACHED_TO_DESKTOP);
         thisDev.isSLM = false;
      }

      if(!(thisDev.deviceType.length()==0)) // Don't list a nonexistent device.
         displays.push_back(thisDev);

      dev++;
   }

   return displays;
}
