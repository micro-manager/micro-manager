///////////////////////////////////////////////////////////////////////////////
// FILE:          PVCAMUtils.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   PVCAM universal camera module
// COPYRIGHT:     University of California, San Francisco, 2006
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
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 06/30/2006
//
// CVS:           $Id: PVCAMUtils.h 2 2007-02-27 23:33:17Z nenad $
//
bool SetLongParam_PvCam(int16 handle, uns32 pvcam_cmd, long value);
bool GetLongParam_PvCam(int16 handle, uns32 pvcam_cmd, long *value);
