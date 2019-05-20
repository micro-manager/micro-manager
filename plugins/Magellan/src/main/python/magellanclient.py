import socket
import sys
import numpy as np
import time

def initialize(host='localhost', port=4827):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.connect((host, port))
    return sock

def sendMessage(sock, data):
    if type(data) == str:
        length = len(data).to_bytes(4, sys.byteorder, signed=True)
        message = length + bytes(data, 'utf-8')
    elif type(data) == np.ndarray:
        length = data.nbytes.to_bytes(4, sys.byteorder, signed=True)
        message = length + data.tobytes()
    sock.sendall(message)

def recieveMessage(sock):
    message_length_bytes = sock.recv(4)
    message_length = int.from_bytes(message_length_bytes, sys.byteorder, signed=True)
    if message_length == -1:
        #decode the message and take action
        pass

def communicate(sock, message):
    sendMessage(sock, message)
    return recieveMessage(sock)

sock = initialize()


test_img = np.random.randint(0, 65535, (1024, 1024, 50), dtype=np.uint16)



start = time.time()
communicate(sock, test_img)
print(time.time() - start)

start = time.time()
communicate(sock, 'hello')
print(time.time() - start)
