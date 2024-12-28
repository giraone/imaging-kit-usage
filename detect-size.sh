#!/bin/bash

file="${1:-/c/Home/testfiles/Koala.png}"

curl -X PUT \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@${file}" \
  http://localhost:8080/detect-size