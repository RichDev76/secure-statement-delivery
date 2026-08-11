# infra/vault/Dockerfile
FROM hashicorp/vault:1.21.1

USER root

RUN sed -i 's/https/http/g' /etc/apk/repositories

# 2. Run apk update and install packages
RUN apk update && \
    apk add --no-cache ca-certificates gettext curl && \
    update-ca-certificates

# 3. (Optional) Switch back to HTTPS now that certificates are trusted
RUN sed -i 's/http/https/g' /etc/apk/repositories

# Ensure envsubst (gettext) and curl are available for template rendering + healthchecks
RUN apk add --no-cache gettext curl

# Copy entrypoint script (mounted file in compose will override during dev; still keep copy)
COPY docker-entrypoint.sh /usr/local/bin/vault-docker-entrypoint.sh
RUN chmod +x /usr/local/bin/vault-docker-entrypoint.sh

# Ensure directory is present for config and init artifacts
RUN mkdir -p /vault/config /vault/data /vault/init && chown -R vault:vault /vault

# Use the entrypoint which renders template and starts vault
ENTRYPOINT ["/usr/local/bin/vault-docker-entrypoint.sh"]
CMD ["server", "-config=/vault/config/vault.hcl"]
