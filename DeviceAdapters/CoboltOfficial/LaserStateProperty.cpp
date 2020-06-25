///////////////////////////////////////////////////////////////////////////////
// FILE:       LaserStateProperty.cpp
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

#include "LaserStateProperty.h"

NAMESPACE_COBOLT_BEGIN

LaserStateProperty::LaserStateProperty( Property::Stereotype stereotype, const std::string& name, LaserDriver* laserDriver, const std::string& getCommand ) :
    DeviceProperty( stereotype, name, laserDriver, getCommand )
{}

void LaserStateProperty::RegisterState( const std::string& deviceValue, const std::string& guiValue, const bool allowsShutter )
{
    stateMap_[ deviceValue ] = guiValue;

    if ( allowsShutter ) {
        shutterAllowedStates_.insert( deviceValue );
    }
}

int LaserStateProperty::GetValue( std::string& string ) const
{
    Parent::GetValue( string );

    if ( stateMap_.find( string ) == stateMap_.end() ) {
        return return_code::unsupported_device_property_value;
    }
    
    string = stateMap_.at( string );
    return return_code::ok;
}

bool LaserStateProperty::AllowsShutter() const
{
    std::string deviceValue;
    Parent::GetValue( deviceValue ); // Do not use local overload as it would translate deviceValue to guiValue.

    return ( shutterAllowedStates_.find( deviceValue ) != shutterAllowedStates_.end() );
}

bool LaserStateProperty::IsCacheEnabled() const
{
    return false;
}

NAMESPACE_COBOLT_END
