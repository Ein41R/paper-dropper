#!/bin/bash

rm -rf /tmp/deathdrop-classes
mkdir -p /tmp/deathdrop-classes
CP=$(find libraries -type f -name '*.jar' -printf '%p:')
javac -encoding UTF-8 -source 25 -target 25 \
  -cp "$CP" \
  -d /tmp/deathdrop-classes \
  src/main/java/com/sibyl/deathdrop/DeathDropPlugin.java
cp src/main/resources/config.yml src/main/resources/plugin.yml /tmp/deathdrop-classes/
jar --create --file plugins/DeathDrop.jar -C /tmp/deathdrop-classes .