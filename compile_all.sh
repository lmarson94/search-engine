#!/bin/sh
if ! [ -d classes ];
then
   mkdir classes
fi
javac -cp pdfbox -d classes ir/*.java
