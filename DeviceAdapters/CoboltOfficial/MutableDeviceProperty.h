///////////////////////////////////////////////////////////////////////////////
// FILE:       MutableDeviceProperty.h
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:
// Cobolt Lasers Controller Adapter
//
// COPYRIGHT:     Cobolt AB, Stockholm, 2020
//                All rights reserved
//
// LICENSE:       MIT
//                Permission is hereby granted, free of charge, to any person obtaining a
//                copy of this software and associated documentation files( the "Software" ),
//                to deal in the Software without restriction, including without limitation the
//                rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
//                sell copies of the Software, and to permit persons to whom the Software is
//                furnished to do so, subject to the following conditions:
//                
//                The above copyright notice and this permission notice shall be included in all
//                copies or substantial portions of the Software.
//
//                THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
//                INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
//                PARTICULAR PURPOSE AND NONINFRINGEMENT.IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
//                HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
//                OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
//                SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
// CAUTION:       Use of controls or adjustments or performance of any procedures other than those
//                specified in owner's manual may result in exposure to hazardous radiation and
//                violation of the CE / CDRH laser safety compliance.
//
// AUTHORS:       Lukas Kalinski / lukas.kalinski@coboltlasers.com (2020)
//

#ifndef __COBOLT__MUTABLE_DEVICE_PROPERTY_H
#define __COBOLT__MUTABLE_DEVICE_PROPERTY_H

#include "DeviceProperty.h"

NAMESPACE_COBOLT_BEGIN

class MutableDeviceProperty : public DeviceProperty
{
    typedef Property Parent;

public:

    MutableDeviceProperty( const Property::Stereotype stereotype, const std::string& name, LaserDriver* laserDriver, const std::string& getCommand );

    virtual int IntroduceToGuiEnvironment( GuiEnvironment* );
    virtual bool IsMutable() const;
    virtual int SetValue( const std::string& ) = 0;
    virtual int OnGuiSetAction( GuiProperty& guiProperty );
};

NAMESPACE_COBOLT_END

#endif // #ifndef __COBOLT__MUTABLE_DEVICE_PROPERTY_H
