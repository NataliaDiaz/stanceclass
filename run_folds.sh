#!/bin/sh

MODEL=$1
DIR=$2

echo "Compiling..."
mvn compile > /dev/null
mvn dependency:build-classpath -Dmdep.outputFile=classpath.out > /dev/null

echo "Running on fold 0..."
java -Xmx16g -cp ./target/classes:`cat classpath.out` edu.ucsc.cs.$MODEL data $DIR 0

echo "Running on fold 1..."
java -Xmx16g -cp ./target/classes:`cat classpath.out` edu.ucsc.cs.$MODEL data $DIR 1

echo "Running on fold 2..."
java -Xmx16g -cp ./target/classes:`cat classpath.out` edu.ucsc.cs.$MODEL data $DIR 2

echo "Running on fold 3..."
java -Xmx16g -cp ./target/classes:`cat classpath.out` edu.ucsc.cs.$MODEL data $DIR 3

echo "Running on fold 4..."
java -Xmx16g -cp ./target/classes:`cat classpath.out` edu.ucsc.cs.$MODEL data $DIR 4

echo "Running on fold 5..."
java -Xmx16g -cp ./target/classes:`cat classpath.out` edu.ucsc.cs.$MODEL data $DIR 5

echo "Running on fold 6�"
java -Xmx16g -cp ./target/classes:`cat classpath.out` edu.ucsc.cs.$MODEL data $DIR 6

echo "Running on fold 7..."
java -Xmx16g -cp ./target/classes:`cat classpath.out` edu.ucsc.cs.$MODEL data $DIR 7

echo "Running on fold 8..."
java -Xmx16g -cp ./target/classes:`cat classpath.out` edu.ucsc.cs.$MODEL data $DIR 8

echo "Running on fold 9..."
java -Xmx16g -cp ./target/classes:`cat classpath.out` edu.ucsc.cs.$MODEL data $DIR 9