///////////////////////////////////////////////////////////////////////////////
// FILE:       EnumerationProperty.h
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

#ifndef __COBOLT__ENUMERATION_PROPERTY_H
#define __COBOLT__ENUMERATION_PROPERTY_H

#include "MutableDeviceProperty.h"

NAMESPACE_COBOLT_BEGIN

/**
 * Any (mutable) property that only can be set to one of a pre-defined set of values.
 */
class EnumerationProperty : public MutableDeviceProperty
{
    typedef MutableDeviceProperty Parent;

public:
    
    EnumerationProperty( const std::string& name, LaserDriver* laserDriver, const std::string& getCommand );

    virtual int IntroduceToGuiEnvironment( GuiEnvironment* environment );

    /**
     * \param deviceValue The response of the getCommand that corresponds to the enumeration item (e.g. 1 might be matched to 'enabled').
     * \param setCommand The set command (with argument, if applicable) to send when intending to set the property to the particular enumeration item.
     * \param name The name of the value (e.g. 'on' or 'enabled' or 'constant current'). Use it when presenting the property in the GUI.
     */
    void RegisterEnumerationItem( const std::string& deviceValue, const std::string& setCommand, const std::string& name );

    virtual int GetValue( std::string& string ) const;
    virtual int SetValue( const std::string& guiValue );

protected:

    bool IsValidValue( const std::string& guiValue );

    std::string ResolveDeviceValue( const std::string& guiValue ) const;
    std::string ResolveEnumerationItem( const std::string& deviceValue ) const;

private:

    struct EnumerationItem
    {
        std::string deviceValue;
        std::string setCommand;
        std::string name;
    };

    typedef std::vector<EnumerationItem> enumeration_items_t;

    enumeration_items_t enumerationItems_;
};

NAMESPACE_COBOLT_END

#endif // #ifndef __COBOLT__ENUMERATION_PROPERTY_H
