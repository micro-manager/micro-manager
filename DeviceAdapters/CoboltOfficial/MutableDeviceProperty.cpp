///////////////////////////////////////////////////////////////////////////////
// FILE:       MutableDeviceProperty.cpp
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
//                specified in owner’s manual may result in exposure to hazardous radiation and
//                violation of the CE / CDRH laser safety compliance.
//
// AUTHORS:       Lukas Kalinski / lukas.kalinski@coboltlasers.com (2020)
//

#include "MutableDeviceProperty.h"
#include "Laser.h"

NAMESPACE_COBOLT_BEGIN

MutableDeviceProperty::MutableDeviceProperty( const Property::Stereotype stereotype, const std::string& name, LaserDriver* laserDriver, const std::string& getCommand ) :
    DeviceProperty( stereotype, name, laserDriver, getCommand )
{}

int MutableDeviceProperty::IntroduceToGuiEnvironment( GuiEnvironment* )
{
    return return_code::ok;
}

bool MutableDeviceProperty::IsMutable() const
{
    return true;
}

int MutableDeviceProperty::OnGuiSetAction( GuiProperty& guiProperty )
{
    std::string value;
    guiProperty.Get( value );

    // Protect against unnecessary eeprom writes:
    if ( value == GetCachedValue() ) {
        return return_code::ok;
    }

    const int returnCode = SetValue( value );

    if ( returnCode != return_code::ok ) {

        Logger::Instance()->LogError( "MutableDeviceProperty[" + GetName() + "]::OnGuiSetAction( GuiProperty( '" + value + "' ) ): Failed" );
        SetToUnknownValue( guiProperty );
        return returnCode;
    }

    ClearCache();

    Logger::Instance()->LogMessage( "MutableDeviceProperty[" + GetName() + "]::OnGuiSetAction( GuiProperty( '" + value + "' ) ): Succeeded", true );

    guiProperty.Set( value );

    return return_code::ok;
}

NAMESPACE_COBOLT_END
