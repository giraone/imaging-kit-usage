# Imaging Kit Usage Example

A Spring Boot Web Service using [imaging-kit](https://github.com/giraone/imaging-kit).

## URLs (web service endpoints) and Scripts

The example project contains web service endpoint and corresponding scripts to show the functionality of [imaging-kit](https://github.com/giraone/imaging-kit).

Start the Spring Boot application first:

```bash
mvn spring-boot:run
``` 
The web service is then available at `http://localhost:8080`.

### Create thumbnail

*Create thumbnail* for an image file. Output is the thumbnail image file.

URL: `POST http://localhost:8080/detect-type <file>`
Script: [./create-thumbnail.sh](./detect-type.sh) `<file>`

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

URL: `PUT http://localhost:8080/fetch-file-info <file>`
Script: [./fetch-file-info.sh](./fetch-file-info.sh) `<file>`

### Detect type

*Detect file type*. Output is a plain string, e.g. `PNG`

URL: `PUT http://localhost:8080/detect-type <file>`
Script: [./detect-type.sh](./detect-type.sh) `<file>`

### Detect size

*Detect file size*

URL: `PUT http://localhost:8080/detect-size <file>`
Script: [./detect-size.sh](./detect-size.sh) `<file>`

### List types

URL: `GET http://localhost:8080/list-types`
Script: [./list-types.sh](./list-types.sh)

## Release Notes / Changes

See [CHANGELOG.md](CHANGELOG.md).