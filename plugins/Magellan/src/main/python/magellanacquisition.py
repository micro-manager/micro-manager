from magellanclient import MagellanClient

class AcquisitionManager:

    def __init__(self):
        self.bridge = MagellanClient()
        self._synchronize()

    def _synchronize(self):
        #TODO: query the java side to see whats up with everything
        #Check UUIDs of acquisitons to correctly reorder
        #Create new python objects where neccesary
        self.acquisitions = []

    def create_acquisition(self):
        #TODO: signal to java to create a new one an resynchronize
        pass

    def delete_acquisition(self, index):
        #TODO: check that index exists and if so signal to java to delete it
        pass

    def run_all(self):
        #TODO
        pass

    def run_aquisition(self):
        #TODO
        pass

    def abort_all(self):
        #TODO:
        pass

    def abort(self, index):
        #TODO:
        pass

class Acquisition:

    def __init__(self):
        pass

    def register_callback(self, fn, type):
        #TODO: register a callback function to run at a specific time
        pass


    #TODO: needs 3 features
    # 1) Checks (callbacks?) for finished
    # 2) Customization of acquistion events
    #       Can java and python iterators be mixed? Probablly no, so you'll want to recreate event
    #       generating code on the python side
    # 3) Hooks of python code to run at specific time in acquisiiton sequence