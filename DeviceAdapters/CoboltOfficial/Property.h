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

#ifndef __COBOLT__PROPERTY_H
#define __COBOLT__PROPERTY_H

#include "base.h"
#include "LaserDriver.h"

NAMESPACE_COBOLT_BEGIN

/**
 * \brief The interface  the property hierarchy sees when receiving GUI events
 *        about property get/set.
 */
class GuiProperty
{
public:

    virtual bool Set( const std::string& ) = 0;
    virtual bool Get( std::string& ) const = 0;
};

/**
 * \brief A GUI environment interface to provide functionality to properly setup a cobolt::Property's
 *        corresponding GUI property.
 */
class GuiEnvironment
{
public:

    virtual int RegisterAllowedGuiPropertyValue( const std::string& propertyName, const std::string& value ) = 0;
    virtual int RegisterAllowedGuiPropertyRange( const std::string& propertyName, double min, double max ) = 0;
};

class Property
{
public:

    enum Stereotype { String, Float, Integer };
    
    static void ResetIdGenerator()
    {
        NextPropertyId_ = 1;
    }

    Property( const Stereotype stereotype, const std::string& name );
    
    virtual int IntroduceToGuiEnvironment( GuiEnvironment* );

    const std::string& GetName() const;

    std::string GetValue() const;
    virtual int GetValue( std::string& string ) const = 0;

    Stereotype GetStereotype() const;

    virtual bool IsMutable() const;
    virtual int OnGuiSetAction( GuiProperty& );
    virtual int OnGuiGetAction( GuiProperty& guiProperty );

    /**
     * \brief The property object represented in a string. For logging/debug purposes.
     */
    virtual std::string ObjectString() const;

protected:

    void SetToUnknownValue( std::string& string ) const;
    void SetToUnknownValue( GuiProperty& guiProperty ) const;
    
private:

    static int NextPropertyId_;

    Stereotype stereotype_;
    std::string name_;
};

NAMESPACE_COBOLT_END

#endif // #ifndef __COBOLT__PROPERTY_H
