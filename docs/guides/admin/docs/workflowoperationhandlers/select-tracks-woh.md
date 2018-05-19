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

\* mandatory configuration key


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
      </configurations>
    </operation>

