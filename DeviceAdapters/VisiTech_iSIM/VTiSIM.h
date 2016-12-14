// Micro-Manager device adapter for VisiTech iSIM
//
// Copyright (C) 2016 Open Imaging, Inc.
//
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published by the
// Free Software Foundation; version 2.1.
//
// This library is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
// for more details.
//
// IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
// DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// You should have received a copy of the GNU Lesser General Public License
// along with this library; if not, write to the Free Software Foundation,
// Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
//
//
// Author: Mark Tsuchida <mark@open-imaging.com>

#pragma once

#include "DeviceBase.h"


class VTiSIMHub : public HubBase<VTiSIMHub>
{
public:
   VTiSIMHub();
   virtual ~VTiSIMHub();

   int Initialize();
   int Shutdown();
   void GetName(char* name) const;
   bool Busy();

   int DetectInstalledDevices();

private:
   HANDLE hAotfControl;
   HANDLE hScanAndMotorControl;
};

