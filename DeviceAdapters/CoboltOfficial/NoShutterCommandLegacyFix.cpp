///////////////////////////////////////////////////////////////////////////////
// FILE:       NoShutterCommandLegacyFix.cpp
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

#include "NoShutterCommandLegacyFix.h"

NAMESPACE_COBOLT_BEGIN

using namespace legacy::no_shutter_command;

const std::string LaserShutterPropertyCdrh::Value_Open = "open";
const std::string LaserShutterPropertyCdrh::Value_Closed = "closed";

LaserShutterPropertyCdrh::LaserShutterPropertyCdrh( const std::string& name, LaserDriver* laserDriver, Laser* laser ) :
    MutableDeviceProperty( Property::String, name, laserDriver, "N/A" ),
    laser_( laser ),
    isOpen_( false ),
    laserStatePersistence_( laserDriver )
{
    if ( laserStatePersistence_.PersistedStateExists() ) { // Without this GetIsShutterOpen() may return false negatives.

        bool wasShutterOpen;
        laserStatePersistence_.GetIsShutterOpen( wasShutterOpen );
        bool wasShutterClosed = !wasShutterOpen;

        // Restore runmode and current if laser was previously disconnected while shutter was closed:
        if ( wasShutterClosed ) {

            if ( RestoreState() != return_code::ok ) {
                Logger::Instance()->LogError( "LaserShutterPropertyCdrh::LaserShutterPropertyCdrh(...): Initialization failed" );
                return;
            }
        }
    }

    // Save current state as is if no state was previously saved:
    if ( !laserStatePersistence_.PersistedStateExists() ) {
        
        SaveState();
    }
}

int LaserShutterPropertyCdrh::IntroduceToGuiEnvironment( GuiEnvironment* environment )
{
    environment->RegisterAllowedGuiPropertyValue( GetName(), Value_Open.c_str() );
    environment->RegisterAllowedGuiPropertyValue( GetName(), Value_Closed.c_str() );

    return return_code::ok;
}

int LaserShutterPropertyCdrh::GetValue( std::string& string ) const
{
    if ( isOpen_ ) {
        string = Value_Open;
    } else {
        string = Value_Closed;
    }

    return return_code::ok;
}

int LaserShutterPropertyCdrh::SetValue( const std::string& value )
{
    if ( !laser_->IsShutterEnabled() && value == Value_Open ) { // The Laser object will call with value == closed, and we have to allow that even if IsShutterEnabled() is false.
        return return_code::property_not_settable_in_current_state;
    }
    
    int returnCode = return_code::ok;

    if ( value == Value_Closed ) { // Shutter 'closed' requested.

        if ( isOpen_ ) { // Only do this if we're really open, otherwise we will save the 'closed' state.

            isOpen_ = false;
            SaveState();
        }

        returnCode = laserDriver_->SendCommand( "slc 0" );
        if ( returnCode != return_code::ok ) { return returnCode; }

        returnCode = laserDriver_->SendCommand( "ecc" );
        if ( returnCode != return_code::ok ) { return returnCode; }
        
    } else if ( value == Value_Open ) { // Shutter 'open' requested.
        
        // Only if not already open:
        if ( !isOpen_ ) {

            isOpen_ = true;
            RestoreState();
        }

    } else {

        Logger::Instance()->LogMessage( "LaserShutterPropertyCdrh[" + GetName() + "]::SetValue( '" + value + "' ): Ignored request as requested state is already set", true );
    }

    return returnCode;
}

int LaserShutterPropertyCdrh::SaveState()
{
    int returnCode = return_code::ok;

    std::string runmode, currentSetpoint;
    
    returnCode = laserDriver_->SendCommand( "gam?", &runmode );
    if ( returnCode != return_code::ok ) { return returnCode; }

    laserDriver_->SendCommand( "glc?", &currentSetpoint );
    if ( returnCode != return_code::ok ) { return returnCode; }

    laserStatePersistence_.PersistState( isOpen_, runmode, currentSetpoint );

    return returnCode;
}

int LaserShutterPropertyCdrh::RestoreState()
{
    int returnCode = return_code::ok;

    std::string runmode, currentSetpoint;
    
    returnCode = laserStatePersistence_.GetRunmode( runmode );
    if ( returnCode != return_code::ok ) { return returnCode;  }
    
    returnCode = laserStatePersistence_.GetCurrentSetpoint( currentSetpoint );
    if ( returnCode != return_code::ok ) { return returnCode; }

    std::string enterRunmodeCommand, setCurrentSetpointCommand;

    if ( runmode == "0" ) {
        enterRunmodeCommand = "ecc";
    } else if ( runmode == "1" ) {
        enterRunmodeCommand = "ecp";
    } else if ( runmode == "2" ) {
        enterRunmodeCommand = "em";
    } else {

        Logger::Instance()->LogError( "LaserShutterPropertyCdrh[" + GetName() + "]::SaveState(): Unhandled runmode" );
        return return_code::error;
    }

    setCurrentSetpointCommand = "slc " + currentSetpoint;
    
    returnCode = laserDriver_->SendCommand( enterRunmodeCommand );
    if ( returnCode != return_code::ok ) { return returnCode; }

    returnCode = laserDriver_->SendCommand( setCurrentSetpointCommand );
    if ( returnCode != return_code::ok ) { return returnCode; }

    return returnCode;
}

const std::string LaserShutterPropertyOem::Value_Open = "open";
const std::string LaserShutterPropertyOem::Value_Closed = "closed";

NAMESPACE_COBOLT_END
