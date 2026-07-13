FROM postgres:18.1-alpine

# Copy initialization scripts
COPY init-scripts/*.sql /docker-entrypoint-initdb.d/
