///////////////////////////////////////////////////////////////////////////////
// FILE:       ImmutableEnumerationProperty.cpp
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

#include "ImmutableEnumerationProperty.h"
#include "Laser.h"

NAMESPACE_COBOLT_BEGIN
 
ImmutableEnumerationProperty::ImmutableEnumerationProperty( const std::string& name, LaserDriver* laserDriver, const std::string& getCommand ) :
    DeviceProperty( Property::String, name, laserDriver, getCommand )
{}

void ImmutableEnumerationProperty::RegisterEnumerationItem( const std::string& deviceValue, const std::string& name )
{
    Logger::Instance()->LogMessage( "ImmutableEnumerationProperty[ " + GetName() + " ]::RegisterEnumerationItem( { '" + 
        deviceValue + "', '" + name + "' } )", true );

    EnumerationItem enumerationItem = { deviceValue, name };

    enumerationItems_.push_back( enumerationItem );
}

int ImmutableEnumerationProperty::GetValue( std::string& string ) const
{
    std::string deviceValue;
    Parent::GetValue( deviceValue );

    string = ResolveEnumerationItem( deviceValue );
    
    if ( string == "" ) {

        SetToUnknownValue( string );
        Logger::Instance()->LogError( "ImmutableEnumerationProperty[" + GetName() + "]::GetValue( ... ): No matching GUI value found for command value '" + deviceValue + "'" );
        return return_code::error;
    }

    return return_code::ok;
}

/**
 * \brief Translates value on device to value in MM GUI. Returns empty string if resolving failed.
 */
std::string ImmutableEnumerationProperty::ResolveEnumerationItem( const std::string& deviceValue ) const
{
    std::string enumerationItemName;

    for ( enumeration_items_t::const_iterator enumerationItem = enumerationItems_.begin();
        enumerationItem != enumerationItems_.end();
        enumerationItem++ ) {

        if ( enumerationItem->deviceValue == deviceValue ) {
            enumerationItemName = enumerationItem->name;
            break;
        }
    }

    return enumerationItemName;
}

NAMESPACE_COBOLT_END
