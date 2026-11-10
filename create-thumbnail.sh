#!/bin/bash

file="${1:-/Users/hans/testfiles/Koala.png}"

curl -X PUT \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@${file}" \
  --output thumbnail.png \
  http://localhost:8080/create-thumbnail
