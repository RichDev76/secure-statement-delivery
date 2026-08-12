#!/bin/sh
# Floci "ready" init hook: creates the configured bucket once the S3 service is up, so it exists
# before statement-service's first request instead of relying on the app to lazily create it
# (which would otherwise be a race on cold start). Uses S3_BUCKET (set on the floci container in
# docker-compose.yml) rather than a hardcoded name so it can never drift from what
# statement-service is actually configured to use.
set -e

aws s3 mb "s3://${S3_BUCKET:-statements}"
