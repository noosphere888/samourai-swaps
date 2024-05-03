#!/bin/bash

export LD_LIBRARY_PATH="$(pwd)/target/debug/"
echo "LD_LIBRARY_PATH=$LD_LIBRARY_PATH"
cd src/ 
javac -h . DLEQTest.java
javac DLEQTest.java 
java DLEQTest -Djava.library.path="LD_LIBRARY_PATH"
cd ../
