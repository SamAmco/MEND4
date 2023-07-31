#!/bin/sh

dupe_script=$(ps -ef | grep "mend-locker.sh" | grep -v grep | wc -l | xargs)

if [ ${dupe_script} -gt 2 ]; then
    echo -e "The mend-locker.sh script was already running!"
    exit 0
fi

dbus-monitor --session "type='signal',interface='org.gnome.ScreenSaver'" |
  while read x; do
    case "$x" in 
      *"boolean true"*) java -jar /opt/sam/mend/mend.jar lock;;
      *"boolean false"*) echo "";;
    esac
  done
