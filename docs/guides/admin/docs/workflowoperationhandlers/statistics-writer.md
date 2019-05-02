Statistics Writer Workflow Operation
====================================

ID: `statistics-writer`

Description
-----------

The statistics writer operation can be used to publish statistics about a video to a statistics backend such as InfluxDB. Currently, it only writes the length of the video in seconds to the data base. It can be configured to write the negative length and can thus be used for retract workflows, too.


Parameter Table
---------------

|configuration keys            |required|description                                                        |
|------------------------------|--------|-------------------------------------------------------------------|
|flavor                        |yes     |The flavor of the track you want to publish statistics to          |
|measurement-name              |yes     |Measurement name of the statistics DB                              |
|organization-resource-id-name |yes     |Resource ID name for the organization                              |
|length-field-name             |yes     |Field name for the length of the video in seconds                  |
|retract                       |no      |Whether to publish positive or negative numbers (default: `false`) |

Operation Examples
------------------

```XML
<operation
  id="statistics-writer"
  fail-on-error="true"
  exception-handler-workflow="partial-error"
  description="Collect video statistics">
  <configurations>
    <configuration key="flavor">presenter/video</configuration>
    <configuration key="retract">false</configuration>
    <configuration key="measurement-name">published-seconds</configuration>
    <configuration key="organization-resource-id-name">organizationId</configuration>
    <configuration key="length-field-name">seconds</configuration>
  </configurations>
</operation>
```
