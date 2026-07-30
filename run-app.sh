#!/usr/bin/env bash
# Run coresystembackend with the Bank Mega internal SSL trust store (§B-006 compliant — NOT trust-all).
# Expects credentials to be provided via environment variables (export them before running this script).
#
# Required env vars:
#   SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD
#   JWT_SECRET (Base64, >=64 bytes for HS512)
#   LDAP_URL_TOKEN, LDAP_URL_VERIFY_PASSWORD, LDAP_CLIENT_ID, LDAP_CLIENT_SECRET,
#   LDAP_USERNAME, LDAP_PASSWORD, LDAP_PARTNER_ID, LDAP_CHANNEL_ID, LDAP_HOST, LDAP_AES_KEY
#
# Usage:
#   source .env 2>/dev/null  # if you keep creds in .env (gitignored)
#   ./run-app.sh
set -euo pipefail

TRUSTSTORE="/home/ikbalgazalba/.ssl-truststores/bankmega-truststore.p12"

if [ ! -f "$TRUSTSTORE" ]; then
  echo "ERROR: trust store not found at $TRUSTSTORE" >&2
  exit 1
fi

# Auto-load .env if present (gitignored, §D-002). Values are exported into the
# environment so Spring's ${VAR} placeholders resolve. If .env is absent, the
# caller must have exported the vars themselves (the fail-fast check below
# still enforces they are set).
if [ -f .env ]; then
  set -a
  . ./.env
  set +a
fi

# Fail fast if any required var is missing
for var in SPRING_DATASOURCE_USERNAME SPRING_DATASOURCE_PASSWORD JWT_SECRET \
           LDAP_URL_TOKEN LDAP_URL_VERIFY_PASSWORD LDAP_CLIENT_ID LDAP_CLIENT_SECRET \
           LDAP_USERNAME LDAP_PASSWORD LDAP_PARTNER_ID LDAP_CHANNEL_ID LDAP_HOST LDAP_AES_KEY; do
  if [ -z "${!var:-}" ]; then
    echo "ERROR: env var $var is not set. Export it before running this script." >&2
    exit 1
  fi
done

exec mvn spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Djavax.net.ssl.trustStore=$TRUSTSTORE -Djavax.net.ssl.trustStorePassword=changeit -Djavax.net.ssl.trustStoreType=PKCS12"
