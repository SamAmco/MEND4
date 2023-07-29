#!/bin/sh

dbus-monitor --session "type='signal',interface='org.gnome.ScreenSaver'" |
  while read x; do
    case "$x" in 
      *"boolean true"*) java -jar /opt/sam/MEND4/MEND4.jar lock;;
      *"boolean false"*) echo "";;
    esac
  done
