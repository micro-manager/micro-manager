See also: DeviceAdapters/SequenceTester, which contains a virtual camera that
will return images that encode the state and history of all devices in the
SequenceTester adapter.

This can be used to test correct functionality of MMCore, utility device
adapters such as Multi Camera, and acquisition engines (sequencers).

The general idea of the system tests using SequenceTester is this:
- Load SequenceTester devices and configure as appropriate (freshly load for
  each test).
- Run the test sequence. Store the resulting images.
- Decode the images using ImageDecoder.
- Analyze the result and verify that it is correct.

Since the images contain the full history of set-operations to the devices, it
is possible to recover information about the state of the virtual hardware at
any point. For example, it is possible to determine that autofocus was
triggered between given image frames and whether other components were in an
appropriate state at that moment.

The devices in SequenceTester are decidedly _not_ intended for demoing the
application, so they do not incorporate any time delay and a large number of
tests can be run very quickly.
