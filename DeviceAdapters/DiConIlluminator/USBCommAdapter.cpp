/*
ScopeLED Device Adapters for Micro-Manager. 
Copyright (C) 2011-2012 ScopeLED

This adapter is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as
published by the Free Software Foundation, either version 3 of the
License, or (at your option) any later version.

This software is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this software.  If not, see
<http://www.gnu.org/licenses/>.

*/

#include "USBCommAdapter.h"

void USBCommAdapter::Clear()
{
    hCommAdapter = NULL;

    GetVersion = NULL;
    CalculateChecksum = NULL;
    GetLastError = NULL;
    OpenFirstDevice = NULL;
    OpenDevice = NULL;
    OpenDevicePath = NULL;
    InitDeviceEnumeration = NULL;
    EnumerateDevices = NULL;
    Close = NULL;
    Write = NULL;
    Read = NULL;
    Flush = NULL;
    GetSerialNumber = NULL;
    GetManufacturer = NULL;
    GetDevicePath = NULL;
    GetDeviceIDs = NULL;
}

static bool QueryRegStringValue(HKEY key, const char* const name, char** ppBuffer)
{
    DWORD size = 0;
    DWORD type = REG_SZ;
    RegQueryValueEx(key, name, NULL, &type, NULL, &size);
    bool ok = size > 0;
    if (ok)
    {
        *ppBuffer = new char[size+1];            
        long result = RegQueryValueEx(key, name, NULL, &type, (LPBYTE) (*ppBuffer), &size);
        ok = (ERROR_SUCCESS == result) && (size > 0); 
        if (ok)
        {
            (*ppBuffer)[size] = '\0';
        }
        else
        {
            delete [] *ppBuffer;
            *ppBuffer = NULL;
        }
    }
    else
    {
        *ppBuffer = NULL;
    }
    return ok;
}

void USBCommAdapter::InitializePath()
{
    HKEY key(0);
    path.clear();
    const char* target = "\\C\\bin\\USBCommDLL.dll";
    long result = RegOpenKeyEx(HKEY_LOCAL_MACHINE, "Software\\DiCon\\USBCommAdapter", 0, KEY_READ, &key);
    if (ERROR_SUCCESS == result)
    {
        char* buffer = NULL;
        if (QueryRegStringValue(key, "Version", &buffer))
        {
            char majorVer = buffer[0] - '0';
            if (majorVer >= 2)
            {
#if defined(_WIN64)
                target = "\\native\\bin\\USBCommAdapter64.dll";
#else
                target = "\\native\\bin\\USBCommAdapter32.dll";
#endif
            }
            delete [] buffer;
            buffer = NULL;
        }
        
        if (QueryRegStringValue(key, "Path", &buffer))
        {
            path = buffer;
            path += target;
            delete [] buffer;
            buffer = NULL;
        }
    }
}

bool USBCommAdapter::file_exists (const char* filename)
{
    return 0xFFFFFFFF != GetFileAttributes(filename);
}

USBCommAdapter::USBCommAdapter()
{
    Clear();
    InitializePath();
}

USBCommAdapter::~USBCommAdapter()
{
    Deinit();
}

bool USBCommAdapter::Ready() const
{
    return (NULL != hCommAdapter);
}

bool USBCommAdapter::InitializeLibrary()
{
    bool ok = true; 
    if (!Ready())
    {
        ok = !path.empty() && file_exists(path.c_str());
        if (ok)
        {
            hCommAdapter = LoadLibrary(path.c_str());
            ok = NULL != hCommAdapter;
            if (ok)
            {
                //GetVersion = (pfnFunc0)GetProcAddress(hCommAdapter, "USBComm_GetVersion");
                CalculateChecksum = (pfnCalculateChecksum)GetProcAddress(hCommAdapter, "USBComm_CalculateChecksum");
                GetLastError = (pfnFunc0)GetProcAddress(hCommAdapter, "USBComm_GetLastError");
                OpenFirstDevice = (pfnOpenFirstDevice)GetProcAddress(hCommAdapter, "USBComm_OpenFirstDevice");
                //OpenDevice = (pfnOpenDevice)GetProcAddress(hCommAdapter, "USBComm_OpenDevice");
                //OpenDevicePath = (pfnOpenDevicePath)GetProcAddress(hCommAdapter, "USBComm_OpenDevicePath");
                //InitDeviceEnumeration = (pfnInitDeviceEnumeration)GetProcAddress(hCommAdapter, "USBComm_InitDeviceEnumeration");
                //EnumerateDevices = (pfnEnumerateDevices)GetProcAddress(hCommAdapter, "USBComm_EnumerateDevices");
                Close = (pfnClose)GetProcAddress(hCommAdapter, "USBComm_Close");
                Write = (pfnWrite)GetProcAddress(hCommAdapter, "USBComm_Write");
                Read = (pfnRead)GetProcAddress(hCommAdapter, "USBComm_Read");
                //Flush = (pfnFlush)GetProcAddress(hCommAdapter, "USBComm_Flush");
                //GetSerialNumber = (pfnGetString)GetProcAddress(hCommAdapter, "USBComm_GetSerialNumber");
                //GetManufacturer = (pfnGetString)GetProcAddress(hCommAdapter, "USBComm_GetManufacturer");
                //GetDevicePath = (pfnGetString)GetProcAddress(hCommAdapter, "USBComm_GetDevicePath");
                //GetDeviceIDs = (pfnGetDeviceIDs)GetProcAddress(hCommAdapter, "USBComm_GetDeviceIDs");

                ok = (NULL != CalculateChecksum) &&
                     (NULL != GetLastError) &&
                     (NULL != OpenFirstDevice)&&
                     (NULL != Close) &&
                     (NULL != Write) &&
                     (NULL != Read);
            }
        }
    }
    return ok;
}

void USBCommAdapter::Deinit()
{
    FreeLibrary(hCommAdapter);
    Clear();
}

