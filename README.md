# Imaging Kit Usage Example

A Spring Boot Web Service using [imaging-kit](https://github.com/giraone/imaging-kit).

## URLs (web service endpoints) and Scripts

The example project contains web service endpoint and corresponding scripts to show the functionality of [imaging-kit](https://github.com/giraone/imaging-kit).

### Fetch file info

*Detect file information*. Output is a JSON structure, e.g.

```json
{
  "mimeType": "image/png",
  "compressionFormat": 4,
  "bitsPerPixel": 24,
  "width": 4096,
  "height": 3072,
  "providerFormat": "PNG"
}
```

URL: `POST http://localhost:8080/fetch-file-info <file>`
Script: [./fetch-file-info.sh](./fetch-file-info.sh) `<file>`

### Detect type

*Detect file type*. Output is a plain string, e.g. `PNG`

URL: `POST http://localhost:8080/detect-type <file>`
Script: [./detect-type.sh](./detect-type.sh) `<file>`

### Detect size

*Detect file size*

URL: `POST http://localhost:8080/detect-size <file>`
Script: [./detect-size.sh](./detect-size.sh) `<file>`

### List types

URL: `POST http://localhost:8080/list-types`
Script: [./list-types.sh](./list-types.sh)

## Release Notes

- V1.3.0 (2024-12-28)
  - Initial version using version 1.3.0

mvn archetype:generate \
-DinteractiveMode=false \
-DarchetypeGroupId=org.openjdk.jmh \
-DarchetypeArtifactId=jmh-java-benchmark-archetype \
-DgroupId=com.giraone.imaging \
-DartifactId=imaging-kit-jmh \
-Dversion=1.3.0