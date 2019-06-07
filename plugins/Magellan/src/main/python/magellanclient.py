from threading import Thread
import socket
import sys
import numpy as np
import time

class MagellanClient:
    """
    Socket-based bridge that allows for two way communication between Magellan
    server and python client. Used to passing string messages as well as data.

    """

    def __init__(self, host='localhost', port=4827):
        self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.socket.connect((host, port))
        self.thread = Thread(target=self.listen, name='Listening thread')
        self.thread.start()

    def listen(self):
        """
        Continuously ping server to check for new data (i.e., execution of a callback,
        resolution of a blocking function, etc)
        """
        while(True):
            self.communicate(b'ping')


    def communicate(self, data):
        """
        Send a message to java server and recieve response
        This method executes continuously in a pinging thread to check for updates from Java server.
        The lock means it can also be called from other threads in order to carry out an action from the
        python side
        :param data:
        """
        #TODO: is this locking syntax righ?
        with self.lock:
            if type(data) == str:
                length = len(data).to_bytes(4, sys.byteorder, signed=True)
                message = length + bytes(data, 'utf-8')
            elif type(data) == np.ndarray:
                length = data.nbytes.to_bytes(4, sys.byteorder, signed=True)
                message = length + data.tobytes()
            self.socket.sendall(message)
            #Get a response an take appropriat action
            message_length_bytes = self.socket.recv(4)
            message_length = int.from_bytes(message_length_bytes, sys.byteorder, signed=True)
            if message_length == -1:
                #decode the message and take action
                #TODO: should that action be on a new thread?
                pass




# magellan = MagellanBridge()
#
# test_img = np.random.randint(0, 65535, (1024, 1024, 50), dtype=np.uint16)
# start = time.time()
# magellan.communicate(test_img)
# print(time.time() - start)
#
# start = time.time()
# magellan.communicate( 'hello')
# print(time.time() - start)
