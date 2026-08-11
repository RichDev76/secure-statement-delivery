FROM quay.io/keycloak/keycloak:26.0

COPY realms/*.json /opt/keycloak/data/import/

ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start-dev", "--features=import-realm", "--import-realm"]