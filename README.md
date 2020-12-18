# Subtitle Speech Synchronizer Helper
Watches folders for new subtitles and executes Subtitle Speech Synchronizer (subsync) on video/subtitle.  Great addition to SickChill with auto subtitles turned on.
This was a quick and dirty program.  


## Ubuntu:
### subsync install (+ removable media)
sudo snap install subsync --classic
sudo snap install subsync:removable-media
sudo snap connect subsync:removable-media

## Windows:
https://subsync.online/en/download.html  (msi installer)

## Assumptions:
English subtitles (<File Name>.en.srt)
Windows install path: C:/Program Files/subsync/

If those assumptions are not met you will need to modify code.

## Execution
First execution will create a file (watch_folders.txt).
Add the folders you want to watch for subtitles to the file (watch_folders.txt).
Execute again to begin watching.


## Additional thoughts
You might want to start a service to kick this off when your computer restarts. You can follow this guide:
https://dzone.com/articles/run-your-java-application-as-a-service-on-ubuntu
I have included subsync.service and SubtitleSpeechSynchronizerHelper.sh to save some typing.




