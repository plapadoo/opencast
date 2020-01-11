# AmberScript Start Transcription

## Description

AmberScript Start Transcription invokes the AmberScript Transcription Service by submitting
an audio or video file to be transcribed and captioned.

## Parameter Table

| configuration keys    | description                                                              | default      | example            |
|-----------------------|--------------------------------------------------------------------------|--------------|--------------------|
| source-tag            | A tag selecting the audio or video file to be sent for translation.      | transcript   | engage-download    |
| language-code         | The target language for transcription.                                   | en           | nl                 |
| skip-if-flavor-exists | If this flavor already exists in the media package, skip this operation. | captions/vtt | captions/timedtext |
