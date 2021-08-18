#!/usr/bin/env bash
mvn clean package && cp ./beercloak-ear/target/beercloak-1.0-SNAPSHOT.ear /home/syed/Documents/keycloak/keycloak-13.0.0/standalone/deployments/
