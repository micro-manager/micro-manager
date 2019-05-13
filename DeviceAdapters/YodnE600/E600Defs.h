#pragma once
///////////////////////////////////////////////////////////////////////////////
// FILE:          E600Defs.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Yodn E600 light source controller adapter constant variable definitions.
// COPYRIGHT:     
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
// AUTHOR:        BJI MBQ (mbaoqi@outlook.com)
///////////////////////////////////////////////////////////////////////////////
#ifndef E600DEFS_H
#define E600DEFS_H

// E600 properties keyword name.
const char* g_ProductName = "YODN Hyper E600";
const char* g_ControllerName = "YodnE600";
const char* g_Keyword_Intensity = "Intensity";
const char* g_Keyword_ChannelLabel = "Channel";
const char* g_Keyword_ErrorCode = "Error";
const char* g_Keyword_Lamp = "Lamp";
const char* g_Keyword_MainVersion = "Main Version";
const char* g_Keyword_PanelVersion = "Panel Version";
const char* g_Keyword_Temperature = "Temperature";
const char* g_Keyword_Use = "Use";
const char* g_Keyword_UseTime = "Use Time";

const unsigned int num_channel = 0x03;
const unsigned int max_read_count = 100;

#endif