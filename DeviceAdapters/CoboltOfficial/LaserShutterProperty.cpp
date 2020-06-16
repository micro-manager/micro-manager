///////////////////////////////////////////////////////////////////////////////
// FILE:       LaserShutterProperty.cpp
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

#include "LaserShutterProperty.h"
#include "Laser.h"

NAMESPACE_COBOLT_BEGIN

const std::string LaserShutterProperty::Value_Open = "open";
const std::string LaserShutterProperty::Value_Closed = "closed";

LaserShutterProperty::LaserShutterProperty( const std::string& name, LaserDriver* laserDriver, Laser* laser ) :
    EnumerationProperty( name, laserDriver, "N/A" ),
    laser_( laser ),
    isOpen_( false )
{
    RegisterEnumerationItem( "N/A", "l1r", Value_Open );
    RegisterEnumerationItem( "N/A", "l0r", Value_Closed );
}

int LaserShutterProperty::GetValue( std::string& string ) const
{
    if ( isOpen_ ) {
        string = Value_Open;
    } else {
        string = Value_Closed;
    }
    
    return return_code::ok;
}

int LaserShutterProperty::SetValue( const std::string& value )
{
    if ( !laser_->IsShutterEnabled() ) {
        return return_code::property_not_settable_in_current_state;
    }

    int returnCode = EnumerationProperty::SetValue( value );

    if ( returnCode == return_code::ok ) {
        isOpen_ = ( value == Value_Open );
        return return_code::ok;
    }

    return returnCode;
}

NAMESPACE_COBOLT_END
