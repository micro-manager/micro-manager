///////////////////////////////////////////////////////////////////////////////
// FILE:       Property.cpp
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

#include "Property.h"
#include "Laser.h"

NAMESPACE_COBOLT_BEGIN

int Property::NextPropertyId_ = 1;

Property::Property( const Stereotype stereotype, const std::string& name ) :
    stereotype_( stereotype ),
    name_( name )
{
    const std::string propertyIdStr = std::to_string( (long double) NextPropertyId_++ );
    name_ = ( std::string( 2 - propertyIdStr.length(), '0' ) + propertyIdStr ) + "-" + name;
}

int Property::IntroduceToGuiEnvironment( GuiEnvironment* )
{
    return return_code::ok;
}

const std::string& Property::GetName() const
{
    return name_;
}

std::string Property::GetValue() const
{
    std::string value;
    GetValue( value );
    return value;
}

Property::Stereotype Property::GetStereotype() const
{
    return stereotype_;
}

bool Property::IsMutable() const
{
    return false;
}

int Property::OnGuiSetAction( GuiProperty& )
{
    Logger::Instance()->LogMessage( "Property[" + GetName() + "]::OnGuiSetAction(): Ignoring 'set' action on read-only property.", true );
    return return_code::ok;
}

int Property::OnGuiGetAction( GuiProperty& guiProperty )
{
    std::string string;
    int returnCode = GetValue( string );

    if ( returnCode != return_code::ok ) {

        SetToUnknownValue( guiProperty );
        return returnCode;
    }

    guiProperty.Set( string.c_str() );

    return returnCode;
}

/**
 * \brief The property object represented in a string. For logging/debug purposes.
 */
std::string Property::ObjectString() const
{
    return "stereotype = " + std::to_string( (long double) stereotype_ ) + "; name_ = " + name_ + "; ";
}

void Property::SetToUnknownValue( std::string& string ) const
{
    string = "Unknown";
}

void Property::SetToUnknownValue( GuiProperty& guiProperty ) const
{
    switch ( GetStereotype() ) {
        case Float:   guiProperty.Set( "0" ); break;
        case Integer: guiProperty.Set( "0" ); break;
        case String:  guiProperty.Set( "Unknown" ); break;
    }
}

NAMESPACE_COBOLT_END
