///////////////////////////////////////////////////////////////////////////////
// FILE:       Property.h
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

#ifndef __COBOLT__NUMERIC_PROPERTY_H
#define __COBOLT__NUMERIC_PROPERTY_H

#include "MutableDeviceProperty.h"

NAMESPACE_COBOLT_BEGIN

template <typename T>
class NumericProperty : public MutableDeviceProperty
{
public:

    NumericProperty( const std::string& name, LaserDriver* laserDriver, const std::string& getCommand, const std::string& setCommandBase, const T min, const T max ) :
        MutableDeviceProperty( ResolveStereotype<T>(), name, laserDriver, getCommand ),
        setCommandBase_( setCommandBase ),
        min_( min ),
        max_( max )
    {}

    virtual int IntroduceToGuiEnvironment( GuiEnvironment* environment )
    {
        return environment->RegisterAllowedGuiPropertyRange( GetName(), min_, max_ );
    }

    virtual int SetValue( const std::string& value )
    {
        if ( !IsValidValue( value ) ) {

            Logger::Instance()->LogError( "NumericProperty[" + GetName() + "]::SetValue( ... ): Invalid value '" + value + "'" );
            return return_code::invalid_value;
        }

        return laserDriver_->SendCommand( setCommandBase_ + " " + value );
    }
    
protected:

    bool IsValidValue( const std::string& value ) const
    {
        T numericValue = (T) atof( value.c_str() );
        return ( min_ <= numericValue && numericValue <= max_ );
    }

private:

    template <typename S>   static Property::Stereotype ResolveStereotype();
    template <>             static Property::Stereotype ResolveStereotype<int>() { return Property::Integer; }
    template <>             static Property::Stereotype ResolveStereotype<double>() { return Property::Float; }
    
    std::string setCommandBase_;

    T min_;
    T max_;
}; 

NAMESPACE_COBOLT_END

#endif // #ifndef __COBOLT__NUMERIC_PROPERTY_H
