#!/bin/bash

arg1=$1
arg2=$2
arg3=$3

if [[ -n "$arg" ]]; then
    /usr/bin/java -jar /opt/SubtitleSpeechSynchronizerHelper/SubtitleSpeechSynchronizerHelper.jar $arg1 $arg2 $arg3
else
    /usr/bin/java -jar /opt/SubtitleSpeechSynchronizerHelper/SubtitleSpeechSynchronizerHelper.jar $arg1 $arg2 $arg3
fi