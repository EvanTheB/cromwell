cwlVersion: v1.0
class: Workflow
# Workflow-level DockerRequirement
requirements:
  DockerRequirement:
    dockerPull: "ubuntu:latest"
inputs:
- id: radius
  type: float
- id: pi
  type: float
outputs:
- id: area
  outputSource: "#two/area"
  type: float
steps:
- id: one
  in:
  - id: radius
    source: "#radius"
  out:
  - id: rSquared
  - id: rCopy
  run:
    inputs:
    - id: radius
      type: float
    outputs:
    - id: rSquared
      outputBinding:
        glob: stdout.txt
        loadContents: true
        outputEval: $(parseFloat(self[0].contents))
      type: float
    - id: rCopy
      outputBinding:
        outputEval: $(inputs.radius)
      type: float
    class: CommandLineTool
    requirements:
    - class: ShellCommandRequirement
    arguments:
    - valueFrom: "perl -e 'print $(inputs.radius) * $(inputs.radius)'"
      shellQuote: false
    stdout: stdout.txt
- id: two
  in:
  - id: rSquared
    source: "#one/rSquared"
  - id: pi
    source: "#pi"
  out:
  - id: area
  run:
    inputs:
    - id: rSquared
      type: float
    - id: pi
      type: float
    outputs:
    - id: area
      outputBinding:
        glob: stdout.txt
        loadContents: true
        outputEval: $(parseFloat(self[0].contents))
      type: float
    class: CommandLineTool
    requirements:
    - class: ShellCommandRequirement
    arguments:
    - valueFrom: "perl -e 'print $(inputs.rSquared) * $(inputs.pi)'"
      shellQuote: false
    stdout: stdout.txt
