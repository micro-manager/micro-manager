///////////////////////////////////////////////////////////////////////////////
// FILE:       Mld06Laser.cpp
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

#include "Mld06Laser.h"

#include "LaserDriver.h"
#include "LaserStateProperty.h"
#include "EnumerationProperty.h"
#include "NoShutterCommandLegacyFix.h"

using namespace std;
using namespace cobolt;

Mld06Laser::Mld06Laser( const std::string& wavelength, LaserDriver* driver ) :
    Laser( "06-MLD", driver )
{
    currentUnit_ = Milliamperes;
    powerUnit_ = Milliwatts;

    CreateNameProperty();
    CreateModelProperty();
    CreateSerialNumberProperty();
    CreateFirmwareVersionProperty();
    CreateAdapterVersionProperty();
    CreateOperatingHoursProperty();
    CreateWavelengthProperty( wavelength );

    CreateKeyswitchProperty();
    CreateLaserStateProperty();
    //CreateLaserOnOffProperty();
    CreateShutterProperty();
    CreateRunModeProperty();
    CreatePowerSetpointProperty();
    CreatePowerReadingProperty();
    CreateCurrentSetpointProperty();
    CreateCurrentReadingProperty();
    CreateDigitalModulationProperty();
    CreateAnalogModulationFlagProperty();
    CreateAnalogImpedanceProperty();
    CreateModulationPowerSetpointProperty();
}

void Mld06Laser::CreateLaserStateProperty()
{
    if ( IsInCdrhMode() ) {

        laserStateProperty_ = new LaserStateProperty( Property::String, "Mld06Laser State", laserDriver_, "gom?" );
    
        laserStateProperty_->RegisterState( "0", "Off", false );
        laserStateProperty_->RegisterState( "1", "Waiting for Key", false );
        laserStateProperty_->RegisterState( "2", "Completed", true );
        laserStateProperty_->RegisterState( "3", "Completed (On/Off Modulation)", false );
        laserStateProperty_->RegisterState( "4", "Completed (Modulation)", false );
        laserStateProperty_->RegisterState( "5", "Fault", false );
        laserStateProperty_->RegisterState( "6", "Aborted", false );

    } else {

        laserStateProperty_ = new LaserStateProperty( Property::String, "Mld06Laser State", laserDriver_, "l?" );

        laserStateProperty_->RegisterState( "0", "Off", true );
        laserStateProperty_->RegisterState( "1", "On", true );
    }

    RegisterPublicProperty( laserStateProperty_ );
}

void Mld06Laser::CreateRunModeProperty()
{
    EnumerationProperty* property;

    if ( IsShutterCommandSupported() || !IsInCdrhMode() ) {
        property = new EnumerationProperty( "Run Mode", laserDriver_, "gam?" );
    } else {
        property = new legacy::no_shutter_command::LaserRunModeProperty( "Run Mode", laserDriver_, "gam?", this );
    }
    
    property->SetCaching( false );

    property->RegisterEnumerationItem( "0", "ecc", EnumerationItem_RunMode_ConstantCurrent );
    property->RegisterEnumerationItem( "1", "ecp", EnumerationItem_RunMode_ConstantPower );
    property->RegisterEnumerationItem( "2", "em", EnumerationItem_RunMode_Modulation );

    RegisterPublicProperty( property );
}
