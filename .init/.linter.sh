#!/bin/bash
cd /home/kavia/workspace/code-generation/browser-audio-recorder-8196-8268/voice_record_and_play_frontend
./gradlew lint
LINT_EXIT_CODE=$?
if [ $LINT_EXIT_CODE -ne 0 ]; then
   exit 1
fi

