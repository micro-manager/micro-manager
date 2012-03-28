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

#ifndef USBCommAdapter_h
#define USBCommAdapter_h

#include <windows.h>
#include <string>

typedef unsigned long (*pfnFunc0)();
typedef unsigned char (*pfnCalculateChecksum)(const unsigned char*, unsigned long);
typedef HANDLE (*pfnOpenFirstDevice)(const unsigned short, const unsigned short);
typedef HANDLE (*pfnOpenDevice)(const unsigned short, const unsigned short, const char* const);
typedef HANDLE (*pfnOpenDevicePath)(const char* const);
typedef int (*pfnInitDeviceEnumeration)(const unsigned short, const unsigned short);
typedef HANDLE (*pfnEnumerateDevices)();
typedef void (*pfnClose)(HANDLE);
typedef int (*pfnWrite)(HANDLE, const unsigned char*, unsigned long*);
typedef int (*pfnRead)(HANDLE, unsigned char*, unsigned long*, unsigned long, HANDLE);
typedef int (*pfnFlush)(HANDLE);
typedef int (*pfnGetString)(HANDLE, char*, unsigned long*);
typedef int (*pfnGetDeviceIDs)(HANDLE, unsigned short*, unsigned short*, unsigned short*);

class USBCommAdapter
{
    HMODULE hCommAdapter;
    std::string path;

    void Clear();
    void InitializePath();
    static bool file_exists (const char* filename);

public:

    pfnFunc0                GetVersion;
    pfnCalculateChecksum    CalculateChecksum;
    pfnFunc0                GetLastError;
    pfnOpenFirstDevice      OpenFirstDevice;
    pfnOpenDevice           OpenDevice;
    pfnOpenDevicePath       OpenDevicePath;
    pfnInitDeviceEnumeration InitDeviceEnumeration;
    pfnEnumerateDevices     EnumerateDevices;
    pfnClose                Close;
    pfnWrite                Write;
    pfnRead                 Read;
    pfnFlush                Flush;
    pfnGetString            GetSerialNumber;
    pfnGetString            GetManufacturer;
    pfnGetString            GetDevicePath;
    pfnGetDeviceIDs         GetDeviceIDs;

    USBCommAdapter();
    ~USBCommAdapter();
    
    bool Ready() const;

    bool InitializeLibrary();
    void Deinit();
};

extern USBCommAdapter g_USBCommAdapter;

#endif

