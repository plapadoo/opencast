SelectTracksWorkflowOperationHandler
====================================


Description
-----------

The SelectTracksWorkflowOperationHandler can be used in case not all source tracks should be processed. For example,
given a recording with a presenter and presentation track, the final recording to be publish may should only include
the video stream of the presenter track and the audio stream of the presentation track.

The workflow operation will use workflow properties set by the Opencast video editor to determine which tracks should be
selected for further processing and add them to the media package based on `target-flavor` and `target-tags`.


Parameter Table
---------------

Configuration Key | Example   | Description
:-----------------|:----------|:-----------
source-flavor\*   | */source  | The flavor of the track(s) to use as a source input
target-flavor\*   | */work    | The flavor of the target track(s)
target-tags       | download  | The tags applied to all target tracks
audio-muxing      | force     | Move single-audio media packages to a specific track (see below)
force-target      | presenter     | Target track for the `force` setting for `audio-muxing`

\* mandatory configuration key

Audio Muxing
-----------------

The optional `audio-muxing` parameter has three possible values: `none` (same as omitting the option), `force` and 
`duplicate`.

### `force` ###

The parameter value `force` only applies to media packages that have exactly one non-hidden audio track. For media packages 
without an audio track or with more than one audio track, the behavior is the same as if the parameter were omitted. The 
same applies to media packages for which there is only one audio track, and it already belongs to the track with flavor type 
given by `force-target` (or `presenter` if that parameter is omitted).

If, however, there is only one non-hidden audio track and it does *not* belong to the track given by `force-target`, then the 
WOH will “move” the audio track to this target track. Specifically, it will mux the video track of `force-target` with the 
audio track it found. Then, it removes the audio track from the original track. 

For example, if there is a media package with two tracks, “presenter” and “presentation”, and the audio track of “presenter” 
is hidden. Then the WOH will mux presenter’s video track and presentations audio track and store it in presenter’s place. It 
will also remove the audio track from “presentation”.

### `duplicate` ###

The parameter value `duplicate` only applies to media packages that have exactly one non-hidden audio track. For media packages 
without an audio track or with more than one audio track, the behavior is the same as if the parameter were omitted. For these 
media packages, the WOH will mux the audio track it found to all video tracks in the media package.

Operation Example
-----------------

    <operation
      id="select-tracks"
      fail-on-error="true"
      exception-handler-workflow="partial-error"
      description="Select tracks for further processing">
      <configurations>
        <configuration key="source-flavor">*/source</configuration>
        <configuration key="target-flavor">*/source</configuration>
        <configuration key="audio-muxing">force</configuration>
      </configurations>
    </operation>

