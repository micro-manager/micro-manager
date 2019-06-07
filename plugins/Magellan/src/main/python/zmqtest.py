import zmq
import time
import json
import numpy as np


class TaggedImage:

    tags = None
    pix = None


class MMCore:

    CLASS_NAME_MAPPING = {'boolean': 'boolean', 'byte[]': 'uint8array',
                'double': 'float',   'double[]': 'double_array', 'float': 'float',
                'int': 'int', 'int[]': 'unint32_array', 'java.lang.String': 'string',
                'long': 'int', 'mmcorej.TaggedImage': 'tagged_image', 'short': 'int', 'void': 'void'}

    CLASS_TYPE_MAPPING = {'boolean': bool, 'byte[]': np.array,
                          'double': float, 'double[]': np.array, 'float': float,
                          'int': int, 'int[]': np.array, 'java.lang.String': str,
                          'long': int, 'mmcorej.TaggedImage': TaggedImage, 'short': int, 'void': None}


    def __init__(self):
        port = 4827
        context = zmq.Context()
        # request reply socket
        self.socket = context.socket(zmq.REQ)
        self.socket.connect("tcp://127.0.0.1:{}".format(port))
        #dyanmically get the API from JAva server
        self.socket.send(bytes(json.dumps({'command': 'send-core-api'}), 'utf-8'))
        reply = self.socket.recv()
        methods = json.loads(reply.decode('utf-8'))

        #parse method descriptions to make python stand ins
        for method in methods:
            arg_type_hints = [self.CLASS_NAME_MAPPING[t] for t in method['arguments']]
            unique_names = []
            for hint in arg_type_hints:
                if hint not in unique_names:
                    unique_names.append(hint)
                else:
                    i = 1
                    while hint + str(i) in unique_names:
                        i += 1
                    unique_names.append(hint + str(i))
            #use exec so we can give the argumetns default names
            exec('fn = lambda {}: MMCore._translate_call(self, {}, {})'.format(','.join(['self'] + unique_names),
                                                                         eval('method'),  ','.join(unique_names)))
            exec('MMCore.{} = fn'.format(method['name']))
            pass
            # setattr(MMCore, method_name, classmethod(fn))

    def _translate_call(self, *args):
        method_spec = args[0]

        # if ()
        valid_args = True
        if len(method_spec['arguments']) != len(args) - 1:
            valid_args = False
        for arg_type, arg_val in zip(method_spec['arguments'], args[1:]):
             correct_type = type(self.CLASS_TYPE_MAPPING[arg_type])
             if not isinstance(type(arg_val), correct_type ):
                 valid_args = False
        if not valid_args:
            raise Exception('Incorrect arguments. \nExpected {} \nGot {}'.format(
                     ','.join(method_spec['arguments'], ','.join([type(a) for a in args[1:]]) )))
        #args are good, make call through socket
        # message = {'name': method_spec['name'], 'arguments': [self._serialize_arg(a) for a in args[1:]]}
        #TODO: maybe some fancier stuff for arrays
        message = {'command': 'run-method', 'name': method_spec['name'], 'arguments':  args[1:]}
        self.socket.send(bytes(json.dumps(message), 'utf-8'))
        reply = self.socket.recv()
        #TODO: deserialize--send some json metadata saying what class type is
        return None if reply == b'void' else reply



    def _serialize_arg(self, arg):
        #TODO: maybe onyl need to translate numpy arrays and taggedImages (or just arrays really)
        pass





core = MMCore()

core.setAutoShutter(False)
epx = core.getProperty('Camera', 'Exposure')
pass

#Push pull socket

# socket = context.socket(zmq.PULL)
# socket.bind("tcp://127.0.0.1:{}".format(port))
#
# while True:
#     # socket.send(b"Hello")
#     #  Get the reply.
#     start = time.time()
#     message = socket.recv()
#     print(time.time() - start)
