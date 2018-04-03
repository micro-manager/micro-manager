///////////////////////////////////////////////////////////////////////////////
// FILE:          ErrorCodes.h
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   List of error IDs
//
// AUTHOR:        Nenad Amodaj, nenad@amodaj.com, 08/08/2005
//
// COPYRIGHT:     University of California, San Francisco, 2006,
//                All Rights reserved
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
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
// CVS:           $Id$
//
#ifndef _ERRORCODES_H_
#define _ERRORCODES_H_

#define MMERR_OK                       0
#define MMERR_GENERIC                  1 // unspecified error
#define MMERR_NoDevice                 2
#define MMERR_SetPropertyFailed        3
#define MMERR_LibraryFunctionNotFound  4
#define MMERR_ModuleVersionMismatch    5
#define MMERR_DeviceVersionMismatch    6
#define MMERR_UnknownModule            7
#define MMERR_LoadLibraryFailed        8
#define MMERR_CreateFailed             9
#define MMERR_CreateNotFound           10
#define MMERR_DeleteNotFound           11
#define MMERR_DeleteFailed             12
#define MMERR_UnexpectedDevice         13
#define MMERR_DeviceUnloadFailed       14
#define MMERR_CameraNotAvailable       15
#define MMERR_DuplicateLabel           16
#define MMERR_InvalidLabel             17
#define MMERR_InvalidStateDevice       19
#define MMERR_NoConfiguration          20
#define MMERR_InvalidConfigurationIndex 21
#define MMERR_DEVICE_GENERIC           22
#define MMERR_InvalidPropertyBlock     23
#define MMERR_UnhandledException       24
#define MMERR_DevicePollingTimeout     25
#define MMERR_InvalidShutterDevice     26
#define MMERR_InvalidSerialDevice      27
#define MMERR_InvalidStageDevice       28
#define MMERR_InvalidSpecificDevice    29
#define MMERR_InvalidXYStageDevice     30
#define MMERR_FileOpenFailed           31
#define MMERR_InvalidCFGEntry          32
#define MMERR_InvalidContents          33
#define MMERR_InvalidCoreProperty      34
#define MMERR_InvalidCoreValue         35
#define MMERR_NoConfigGroup            36
#define MMERR_CameraBufferReadFailed   37
#define MMERR_DuplicateConfigGroup     38
#define MMERR_InvalidConfigurationFile 39
#define MMERR_CircularBufferFailedToInitialize 40
#define MMERR_CircularBufferEmpty      41
#define MMERR_ContFocusNotAvailable    42
#define MMERR_AutoFocusNotAvailable    43
#define MMERR_BadConfigName            44
#define MMERR_CircularBufferIncompatibleImage  45
#define MMERR_NotAllowedDuringSequenceAcquisition  46
#define MMERR_OutOfMemory					47
#define MMERR_InvalidImageSequence     48
#define MMERR_NullPointerException     49
#define MMERR_CreatePeripheralFailed   50
#define MMERR_PropertyNotInCache       51
#define MMERR_BadAffineTransform       52
#endif //_ERRORCODES_H_
