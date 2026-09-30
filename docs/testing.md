# Testing

The test suite checks planning, provisioning, registry access, store detection, provenance, bounding boxes, and the Web UI workflow.

## Prerequisites

Install `portolan-java` before you test the module:

```bash
cd /path/to/portolan-java
mvn install
```

Use a GeoServer checkout whose version matches the parent version in `pom.xml`.
The current module uses GeoServer `3.1.0-SNAPSHOT`.

## Run the tests

From this repository, provide the GeoServer source directory:

```bash
GEOSERVER_SRC=/path/to/geoserver make test
```

The Make target copies the module under `src/community` temporarily. Maven can then resolve the GeoServer community parent.

To run the module after it is already linked under `src/community/portolan`, use the GeoServer reactor:

```bash
mvn -f src/community/pom.xml -Pportolan -pl portolan test
```

## Measure coverage

Run:

```bash
GEOSERVER_SRC=/path/to/geoserver make coverage
```

JaCoCo writes HTML and XML reports. The Make target prints the HTML report path after the test run.

The Maven build enforces at least 85% line coverage for the module. A lower result fails the `test` phase.

## Test boundaries

Unit tests use local catalog fixtures and do not contact the public registry. They cover these behaviors:

- parsing a downloaded catalog through `portolan-java`;
- choosing data assets and mapping their formats;
- reading collection bounding boxes;
- creating workspaces, stores, and provenance metadata;
- handling absent plugins and failed layer publication;
- reporting store readiness and blocking unsafe provisioning;
- rendering and submitting the authenticated Wicket page;
- reading a registry and catalog from local file URIs.

The tests replace store readers when the behavior under test does not require real data. This keeps GeoParquet, COG, and PMTiles unit tests deterministic.

A running GeoServer remains the integration boundary for opening remote assets through their real store implementations.

## Continuous integration

The `test.yml` workflow runs the suite for pushes to `main` and pull requests. It uploads the JaCoCo report as a workflow artifact.

The release workflow runs the same Maven test phase before it assembles and
validates the `slim` and `full` extension ZIPs.
