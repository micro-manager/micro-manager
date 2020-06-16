///////////////////////////////////////////////////////////////////////////////
// FILE:       Laser.h
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

#ifndef __COBOLT__LASER_H
#define __COBOLT__LASER_H

#include <string>
#include <set>
#include <map>
#include <vector>

#include "base.h"
#include "Property.h"

NAMESPACE_COBOLT_BEGIN

class LaserDriver;
class LaserStateProperty;
class MutableDeviceProperty;

class Laser
{
public:

    typedef std::map<std::string, cobolt::Property*>::iterator PropertyIterator;

    static Laser* Create( LaserDriver* driver );

    virtual ~Laser();

    const std::string& GetId() const;
    const std::string& GetName() const;
    const std::string& GetWavelength() const;

    void SetOn( const bool );
    void SetShutterOpen( const bool );

    bool IsShutterEnabled() const;
    
    bool IsShutterOpen() const;

    Property* GetProperty( const std::string& name ) const;
    Property* GetProperty( const std::string& name );

    PropertyIterator GetPropertyIteratorBegin();
    PropertyIterator GetPropertyIteratorEnd();

private:

    enum Stereotype {

        ST_06_DPL,
        ST_06_MLD,
        ST_05_Series
    };

    static int NextId__;

    Laser( const std::string& name, const std::string& wavelength, LaserDriver* device );

    void RegisterState( const std::string& state );


    /// ###
    /// Property Factories

    void CreateNameProperty();
    void CreateModelProperty();
    void CreateWavelengthProperty();
    void CreateKeyswitchProperty();
    void CreateSerialNumberProperty();
    void CreateFirmwareVersionProperty();
    void CreateDriverVersionProperty();

    void CreateOperatingHoursProperty();
    void CreateCurrentSetpointProperty();
    void CreateCurrentReadingProperty();
    void CreatePowerSetpointProperty();
    void CreatePowerReadingProperty();

    template<Stereotype T> void CreateLaserStateProperty();
    template<> void CreateLaserStateProperty<ST_05_Series>();
    template<> void CreateLaserStateProperty<ST_06_DPL>();
    template<> void CreateLaserStateProperty<ST_06_MLD>();

    void CreateLaserOnOffProperty();
    void CreateShutterProperty();
    template <Stereotype T> void CreateRunModeProperty() {}
    template <> void CreateRunModeProperty<ST_05_Series>();
    template <> void CreateRunModeProperty<ST_06_DPL>();
    template <> void CreateRunModeProperty<ST_06_MLD>();
    void CreateDigitalModulationProperty();
    void CreateAnalogModulationFlagProperty();

    void CreateModulationPowerSetpointProperty();
    void CreateAnalogImpedanceProperty();
    
    static const std::string Milliamperes;
    static const std::string Amperes;
    static const std::string Milliwatts;
    static const std::string Watts;

    static const std::string EnumerationItem_On;
    static const std::string EnumerationItem_Off;
    static const std::string EnumerationItem_Enabled;
    static const std::string EnumerationItem_Disabled;

    static const std::string EnumerationItem_RunMode_ConstantCurrent;
    static const std::string EnumerationItem_RunMode_ConstantPower;
    static const std::string EnumerationItem_RunMode_Modulation;

    static void DecomposeModelString( std::string modelString, std::vector<std::string>& modelTokens );

    bool IsShutterCommandSupported() const;
    bool IsInCdrhMode() const;

    void CreateGenericProperties();

    void RegisterPublicProperty( Property* );
    
    std::map<std::string, cobolt::Property*> properties_;
    
    std::string id_;
    std::string name_;
    std::string wavelength_;
    LaserDriver* laserDriver_;

    std::string currentUnit_;
    std::string powerUnit_;

    LaserStateProperty* laserStateProperty_;
    MutableDeviceProperty* laserOnOffProperty_;
    MutableDeviceProperty* shutter_;
};

NAMESPACE_COBOLT_END

#endif // #ifndef __COBOLT__LASER_H