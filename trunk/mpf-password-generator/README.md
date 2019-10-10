# Overview

This program uses bcrypt to create one or more secure hash encodings of a raw password.

# Build

Run this command in the directory with `pom.xml`:

`mvn package`

# Run

Run this command in the same directory:

`java -jar target/mpf-password-generator-0.0.1-SNAPSHOT-jar-with-dependencies.jar <raw-password> [encoder-strength=12] [num-hashes-to-generate=1]`