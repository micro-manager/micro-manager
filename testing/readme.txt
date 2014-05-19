How to run the tests
--------------------

1. Download and extract `gtest-1.7.0` (Google Test), and place it at

       testing/gtest

   This can be done with the following commands:

       cd testing
       wget https://googletest.googlecode.com/files/gtest-1.7.0.zip
       unzip gtest-1.7.0.zip
       mv gtest-1.7.0 gtest

2. Run

       make check
