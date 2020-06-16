///////////////////////////////////////////////////////////////////////////////
// FILE:       NoShutterCommandLegacyFix.h
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

#ifndef __COBOLT__NO_SHUTTER_COMMAND_LEGACY_FIX_H
#define __COBOLT__NO_SHUTTER_COMMAND_LEGACY_FIX_H

#include "EnumerationProperty.h"
#include "NumericProperty.h"
#include "Laser.h"

NAMESPACE_COBOLT_BEGIN

namespace legacy
{
    namespace no_shutter_command
    {
        class PersistedLaserState
        { 
        public:

            PersistedLaserState( LaserDriver* laserDriver ) :
                laserDriver_( laserDriver )
            {}

            bool PersistedStateExists() const
            {
                std::string persistedValue;
                laserDriver_->SendCommand( "gdsn?", &persistedValue );
                return IsValidPersistedState( persistedValue );
            }
            
            int PersistRunmode( const std::string& runmode )
            {
                std::string isShutterOpenStr, currentSetpoint;
                Fetch( &isShutterOpenStr, NULL, &currentSetpoint );

                char valueToSave[ 32 ];
                sprintf( valueToSave, "MM[%s;%s;%s]", isShutterOpenStr.c_str(), runmode.c_str(), currentSetpoint.c_str() );
                const std::string saveCommand = "sdsn " + std::string( valueToSave );

                return laserDriver_->SendCommand( saveCommand );
            }

            int PersistCurrentSetpoint( const std::string& currentSetpoint )
            {
                std::string isShutterOpenStr, runmode;
                Fetch( &isShutterOpenStr, &runmode, NULL );

                char valueToSave[ 32 ];
                sprintf( valueToSave, "MM[%s;%s;%s]", isShutterOpenStr.c_str(), runmode.c_str(), currentSetpoint.c_str() );
                const std::string saveCommand = "sdsn " + std::string( valueToSave );

                return laserDriver_->SendCommand( saveCommand );
            }

            int PersistState( const bool isShutterOpen, const std::string& runmode, const std::string& currentSetpoint )
            {
                char valueToSave[ 32 ];
                sprintf( valueToSave, "MM[%s;%s;%s]", ( isShutterOpen ? "1" : "0" ), runmode.c_str(), currentSetpoint.c_str() );
                const std::string saveCommand = "sdsn " + std::string( valueToSave );

                return laserDriver_->SendCommand( saveCommand );
            }

            int GetIsShutterOpen( bool& isShutterOpen ) const
            {
                std::string isShutterOpenStr;
                int returnCode = Fetch( &isShutterOpenStr, NULL, NULL );

                if ( returnCode != return_code::ok ) {
                    return returnCode;
                }

                isShutterOpen = ( isShutterOpenStr == "1" ? true : false );

                return returnCode;
            }

            int GetRunmode( std::string& runmode ) const
            {
                return Fetch( NULL, &runmode, NULL );
            }

            int GetCurrentSetpoint( std::string& currentSetpoint ) const
            {
                return Fetch( NULL, NULL, &currentSetpoint );
            }

        private:

            int Fetch( std::string* isShutterOpen, std::string* runmode, std::string* currentSetpoint ) const
            {
                std::string persistedValue;
                laserDriver_->SendCommand( "gdsn?", &persistedValue );

                if ( !IsValidPersistedState( persistedValue ) ) {
                    return return_code::error;
                }
                
                int separatorsFound = 0;
                for ( int i = 2; i < persistedValue.length(); i++ ) {

                    if ( persistedValue[ i ] == '[' || persistedValue[ i ] == ']' ) {
                        continue;
                    }
                    
                    if ( persistedValue[ i ] == ';' ) {
                        separatorsFound++;
                        continue;
                    }

                    switch ( separatorsFound ) {

                        case 0: if ( isShutterOpen != NULL )    { isShutterOpen->append( 1, persistedValue[ i ] ); }    break;
                        case 1: if ( runmode != NULL )          { runmode->append( 1, persistedValue[ i ] ); }          break;
                        case 2: if ( currentSetpoint != NULL )  { currentSetpoint->append( 1, persistedValue[ i ] ); }  break; 
                    }
                }

                if ( isShutterOpen != NULL && *isShutterOpen == "" )     { return return_code::error; }
                if ( runmode != NULL && *runmode == "" )                 { return return_code::error; }
                if ( currentSetpoint != NULL && *currentSetpoint == "" ) { return return_code::error; }

                return return_code::ok;
            }

            bool IsValidPersistedState( const std::string& stateString ) const
            {
                return ( stateString.substr( 0, 2 ) == "MM" );
            }

            LaserDriver* laserDriver_;
        };

        class LaserCurrentProperty : public NumericProperty<double>
        {
            typedef NumericProperty<double> Parent;

        public:

            LaserCurrentProperty( const std::string& name, LaserDriver* laserDriver, const std::string& getCommand,
                const std::string& setCommandBase, const double min, const double max, Laser* laser ) :
                NumericProperty<double>( name, laserDriver, getCommand, setCommandBase, min, max ),
                laser_( laser ),
                laserStatePersistence_( laserDriver )
            {}

            virtual bool IsCacheEnabled() const
            {
                return false;
            }

            virtual int GetValue( std::string& string ) const
            {
                if ( laser_->IsShutterOpen() ) {
                    return Parent::GetValue( string );
                } else {
                    laserStatePersistence_.GetCurrentSetpoint( string );
                    return return_code::ok;
                }
            }

            virtual int SetValue( const std::string& value )
            {
                int returnCode = return_code::ok;
                
                if ( laser_->IsShutterOpen() ) {

                    returnCode = Parent::SetValue( value );
                    if ( returnCode != return_code::ok ) { return returnCode; }

                    returnCode = laserStatePersistence_.PersistCurrentSetpoint( value );

                } else if ( Parent::IsValidValue( value ) ) { // Shutter closed.
                    
                    returnCode = laserStatePersistence_.PersistCurrentSetpoint( value );
                }

                return returnCode;
            }

        private:

            Laser* laser_;
            PersistedLaserState laserStatePersistence_;
        };
         
        class LaserRunModeProperty : public EnumerationProperty
        {
            typedef EnumerationProperty Parent;

        public:
            
            LaserRunModeProperty( const std::string& name, LaserDriver* laserDriver, const std::string& getCommand, Laser* laser ) :
                EnumerationProperty( name, laserDriver, getCommand ),
                laser_( laser ),
                laserStatePersistence_( laserDriver )
            {
                // We don't want caching as the value retrieval is more complex here:
                SetCaching( false );
            }

            virtual void SetCaching( const bool enabled )
            {
                // Prevent enabling of caching:
                Parent::SetCaching( false );
                if ( enabled ) {
                    Logger::Instance()->LogMessage( "LaserCurrentProperty::SetCaching(...): overriding request to enable caching - caching remains disabled", true );
                }
            }

            virtual int GetValue( std::string& string ) const
            {
                if ( laser_->IsShutterOpen() ) {

                    return Parent::GetValue( string );

                } else {

                    laserStatePersistence_.GetRunmode( string );
                    string = ResolveEnumerationItem( string );

                    return return_code::ok;
                }
            }

            virtual int SetValue( const std::string& guiValue )
            {
                int returnCode = return_code::ok;

                std::string deviceValue = ResolveDeviceValue( guiValue );

                if ( laser_->IsShutterOpen() ) {

                    returnCode = Parent::SetValue( guiValue );
                    if ( returnCode != return_code::ok ) { return returnCode; }
                    
                    returnCode = laserStatePersistence_.PersistRunmode( deviceValue );

                } else if ( Parent::IsValidValue( guiValue ) ) { // Shutter closed.

                    returnCode = laserStatePersistence_.PersistRunmode( deviceValue );
                }

                return returnCode;
            }

        private:

            Laser* laser_;
            PersistedLaserState laserStatePersistence_;
        };

        class LaserShutterPropertyCdrh : public MutableDeviceProperty
        {
        public:

            static const std::string Value_Open;
            static const std::string Value_Closed;

            LaserShutterPropertyCdrh( const std::string& name, LaserDriver* laserDriver, Laser* laser );
            
            virtual int IntroduceToGuiEnvironment( GuiEnvironment* environment );

            virtual int GetValue( std::string& string ) const;
            virtual int SetValue( const std::string& value );

        private:

            int SaveState();
            int RestoreState();

            Laser* laser_;
            bool isOpen_;
            PersistedLaserState laserStatePersistence_;
        };

        class LaserShutterPropertyOem : public EnumerationProperty
        {
            typedef EnumerationProperty Parent;

        public:

            static const std::string Value_Open;
            static const std::string Value_Closed;

            LaserShutterPropertyOem( const std::string& name, LaserDriver* laserDriver, Laser* laser ) :
                EnumerationProperty( name, laserDriver, "l?" ),
                laser_( laser )
            {
                RegisterEnumerationItem( "0", "l0", Value_Closed );
                RegisterEnumerationItem( "1", "l1", Value_Open );
            }

            virtual int SetValue( const std::string& value )
            {
                if ( !laser_->IsShutterEnabled() && value == Value_Open ) { // The Laser object will call with value == closed, and we have to allow that even if IsShutterEnabled() is false.
                    return return_code::property_not_settable_in_current_state;
                }
                
                return Parent::SetValue( value );
            }

        private:

            Laser* laser_;
        };
    }
}

NAMESPACE_COBOLT_END

#endif // #ifndef __COBOLT__NO_SHUTTER_COMMAND_LEGACY_FIX_H
