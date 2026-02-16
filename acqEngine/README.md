# AcqEngine Golden-File Tests

The `SequenceGeneratorGoldenTest` is a parameterized JUnit 4 test that
validates both the Java and Clojure sequence generator implementations
against stored known-correct output (golden files).

## Structure

Each JSON file in `src/test/resources/org/micromanager/internal/jacque/golden/`
represents one test case:

```json
{
  "description": "Human-readable description",
  "settings": { ... },
  "expectedEvents": [ ... ]
}
```

Both `testJavaMatchesGolden` and `testClojureMatchesGolden` run for every
JSON file, comparing the implementation's output against `expectedEvents`.

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

## Adding a new test case

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
