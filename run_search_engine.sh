#!/bin/sh
rm index/*
java -cp classes:pdfbox -Xmx1g ir.Engine -d davisWiki -l ir18.jpg -p patterns.txt
