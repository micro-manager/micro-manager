#include <AccelStepper.h>


#define SERIAL_BAUD_RATE 115200

#include "commandrouting.h"

CommandRouter cmd;

// Define a stepper and the pins it will use
AccelStepper stepper(AccelStepper::DRIVER, 4, 3);


// This command runs once at power-on
void setup() {

  // Initialize serial interface
  Serial.begin(SERIAL_BAUD_RATE);

  stepper.setMaxSpeed(3000);
  stepper.setAcceleration(1000);
  //set to a really bug number so all steps are positive
  stepper.setCurrentPosition(0);

  cmd.setStepper(&stepper);

}

// This command runs continuously after setup() runs once
void loop()
{
//   Loop until we recieve a command, then parse it.
  if (Serial.available())
    cmd.processSerialStream();
  stepper.run();



  
}
