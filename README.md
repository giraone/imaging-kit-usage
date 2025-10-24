# Imaging Kit Usage Example

A Spring Boot Web Service using [imaging-kit](https://github.com/giraone/imaging-kit).

## URLs (web service endpoints) and Scripts

The example project contains web service endpoint and corresponding scripts to show the functionality of [imaging-kit](https://github.com/giraone/imaging-kit).

Start the Spring Boot application first:

```bash
mvn spring-boot:run
``` 
The web service is then available at `http://localhost:8080`.

## Running the Tests

### Run All Tests
```bash
mvn test
```

### Run Only Integration Tests
```bash
mvn test -Dtest=ImageControllerIT
```

### Run Specific Test Method
```bash
mvn test -Dtest=ImageControllerIT#listImageTypes_returns_ok_status
```
## Available Endpoints

### Create thumbnail

*Create thumbnail* for an image file. Output is the thumbnail image file.

URL: `POST http://localhost:8080/detect-type <file>`
Script: [./create-thumbnail.sh](./detect-type.sh) `<file>`

### Fetch file info

*Detect file information*. Output is a JSON structure.

URL: `PUT http://localhost:8080/fetch-file-info <file>`
Script: [./fetch-file-info.sh](./fetch-file-info.sh) `<file>`

**Sample Request:**
```bash
curl -X PUT \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@src/test/resources/image-01.jpg" \
  http://localhost:8080/fetch-file-info
```

**Expected Response:**
```json
{"mimeType":"image/jpeg","compressionFormat":2,"bitsPerPixel":24,"width":1024,"height":768,"providerFormat":"JPEG"}
```

### Detect type

*Detect file type*. Output is a plain string, e.g. `PNG`

URL: `PUT http://localhost:8080/detect-type <file>`
Script: [./detect-type.sh](./detect-type.sh) `<file>`

**Sample Request:**
```bash
curl -X PUT \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@src/test/resources/image-01.png" \
  http://localhost:8080/detect-type
```
**Expected Response:**
```
PNG
```

### Detect size

*Detect file size*

URL: `PUT http://localhost:8080/detect-size <file>`
Script: [./detect-size.sh](./detect-size.sh) `<file>`

**Sample Request:**
```bash
curl -X PUT \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@src/test/resources/image-01.png" \
  http://localhost:8080/detect-size
```
**Expected Response:**
```
22559
```

### List types

URL: `GET http://localhost:8080/list-types`
Script: [./list-types.sh](./list-types.sh)

**Sample Request:**
```bash
curl http://localhost:8080/list-types
```
**Expected Response:**
```
["UNKNOWN","JPEG","PNG","TIFF","GIF","BMP","PGM","DICOM","PDF"
```

## Test Data

### Test Files (from imaging-kit test resources)

Located in [src/test/resources](src/test/resources):

- **image-01.jpg** - 1024x768 JPEG, 24-bit RGB, 149KB
- **image-01.png** - 800x600 PNG, 24-bit RGB, 22KB
- **image-01.gif** - 100x75 GIF, 8-bit RGB, 22KB
- **document-01-PDF-1.3.pdf** - PDF 1.3 document, 4KB
- **text.txt** - Plain text file, 21 bytes

## Release Notes / Changes

See [CHANGELOG.md](CHANGELOG.md).