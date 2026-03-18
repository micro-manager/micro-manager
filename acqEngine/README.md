# AcqEngine Golden-File Tests

Two parameterized JUnit 4 test suites validate the Java translation against
the original Clojure implementation using golden files.
`SequenceGeneratorGoldenTest` covers event generation;
`EventExecutionGoldenTest` covers execution (core method calls).

Both suites run `testJavaMatchesGolden` and `testClojureMatchesGolden` for
every JSON file, and share the same record-mode and `record.source`
mechanisms described below.

## Sequence generator golden tests

Each JSON file in `src/test/resources/.../golden/` represents one test case:

```json
{
  "description": "Human-readable description",
  "settings": { ... },
  "expectedEvents": [ ... ]
}
```

Test class: `SequenceGeneratorGoldenTest`

## Execution golden tests

Each JSON file in `src/test/resources/.../execution-golden/` represents one
test case:

```json
{
  "description": "Human-readable description",
  "mockCore": { ... },
  "initialState": { ... },
  "settings": { ... },
  "events": [ ... ],
  "expectedCalls": [ { "method": "...", "args": [...] }, ... ]
}
```

- `mockCore` — configures `HelperRecordingMockCore` (devices, properties,
  positions, sequencing support, etc.)
- `initialState` — pre-populates engine state (default drives, reference Z,
  initial shutter/exposure/autofocus state)
- `settings` — acquisition settings (currently just `cameraTimeout`)
- `events` — the sequence of `AcqEvent`s to execute
- `expectedCalls` — the expected ordered list of MMCore method calls

Test class: `EventExecutionGoldenTest`

### Adding a new execution test case

1. Create a JSON file under `src/test/resources/.../execution-golden/`
   (subdirectories are fine for organization). Populate `mockCore`,
   `initialState`, `settings`, and `events`. Set `"expectedCalls": null`.
2. Run in record mode to populate `expectedCalls` from the Clojure impl:
   ```sh
   cd acqEngine && ant -Drecord=true test-only
   ```
3. Inspect the generated calls in the JSON file.
4. Commit the file.

## Running tests

```sh
cd acqEngine
make        # build
make check  # run all tests
```

To run only the golden tests:

```sh
make check testclassarg="-Dtest.class=org.micromanager.internal.jacque.SequenceGeneratorGoldenTest"
```

## Adding a new sequence generator test case

1. Create a JSON file in `src/test/resources/.../golden/` with `settings`
   and `description`. Set `"expectedEvents": null`.
2. Run in record mode to populate `expectedEvents` from the Clojure impl:
   ```sh
   cd acqEngine && ant -Drecord=true test-only
   ```
3. Inspect the generated events in the JSON file.
4. Commit the file.

## Regenerating golden data

If the sequence generator intentionally changes behavior, re-record:

```sh
cd acqEngine && ant -Drecord=true test-only
```

This writes output from the Clojure implementation by default. To record
from the Java implementation instead:

```sh
cd acqEngine && ant -Drecord=true -Drecord.source=java test-only
```
