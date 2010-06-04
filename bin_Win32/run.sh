#!/bin/bash
cd /usr/local/ImageJ
export LD_LIBRARY_PATH=.:/usr/local/lib:/lib:/usr/lib
java -mx256m -Djava.library.path=/usr/local/ImageJ -Dplugins.dir=/usr/local/ImageJ -cp /usr/local/ImageJ/ij.jar:/usr/local/jdk1.5.0_04/lib/tools.jar ij.ImageJ
