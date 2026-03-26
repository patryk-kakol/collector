#!/bin/bash

echo "Generating local development RSA keys..."
mkdir -p local-env/keys
ssh-keygen -t rsa -b 2048 -f local-env/keys/local_rsa -q -N ""
echo "Keys generated successfully!"
