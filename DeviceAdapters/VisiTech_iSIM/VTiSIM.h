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

   virtual int Initialize();
   virtual int Shutdown();
   virtual void GetName(char* name) const;
   virtual bool Busy();

   virtual int DetectInstalledDevices();

public:
   HANDLE GetAOTFHandle() { return hAotfControl_; }
   HANDLE GetScanAndMotorHandle() { return hScanAndMotorControl_; }

private:
   HANDLE hAotfControl_;
   HANDLE hScanAndMotorControl_;
};


class VTiSIMLaserShutter : public CShutterBase<VTiSIMLaserShutter>
{
public:
   VTiSIMLaserShutter();
   virtual ~VTiSIMLaserShutter();

   virtual int Initialize();
   virtual int Shutdown();
   virtual void GetName(char* name) const;
   virtual bool Busy();

   virtual int GetOpen(bool& open);
   virtual int SetOpen(bool open);
   virtual int Fire(double) { return DEVICE_UNSUPPORTED_COMMAND; }

private:
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   VTiSIMHub* VTiHub();
   int DoSetOpen(bool open);

private:
   bool isOpen_;
};


class VTiSIMLasers : public CStateDeviceBase<VTiSIMLasers>
{
public:
   static const int nChannels = 8;

public:
   VTiSIMLasers();
   virtual ~VTiSIMLasers();

   virtual int Initialize();
   virtual int Shutdown();
   virtual void GetName(char* name) const;
   virtual bool Busy();
   virtual unsigned long GetNumberOfPositions() const;

private:
   int OnState(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnIntensity(MM::PropertyBase* pProp, MM::ActionType eAct, long chan);

private:
   VTiSIMHub* VTiHub();
   int DoSetChannel(int chan);
   int DoSetIntensity(int chan, int percentage);

private:
   int curChan_;
   int intensities_[nChannels];
};


class VTiSIMScanner : public CGenericBase<VTiSIMScanner>
{
public:
   VTiSIMScanner();
   virtual ~VTiSIMScanner();

   virtual int Initialize();
   virtual int Shutdown();
   virtual void GetName(char* name) const;
   virtual bool Busy();

private:
   int OnScanRate(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanWidth(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnScanOffset(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnStartStop(MM::PropertyBase* pProp, MM::ActionType eAct);
   int OnActualScanRate(MM::PropertyBase* pProp, MM::ActionType eAct);

private:
   VTiSIMHub* VTiHub();
   int DoSetScanRate(int rateHz);
   int DoSetScanWidth(int width);
   int DoSetScanOffset(int offset);
   int DoStartStopScan(bool shouldScan);
   int DoGetScanning(bool& scanning);

   int GetMaxOffset() const
   { return (maxWidth_ - scanWidth_) / 2; }

private:
   LONG minRate_, maxRate_;
   LONG minWidth_, maxWidth_;

   int scanRate_;
   int scanWidth_;
   int scanOffset_;
   float actualRate_;
};