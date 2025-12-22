#!/bin/bash
set -e

echo "Starting database..."
docker-compose up -d postgres

echo "Waiting for database to be ready..."
until docker exec smart_task_manager_db pg_isready -U postgres -d smart_task_manager > /dev/null 2>&1; do
  echo "Waiting for database..."
  sleep 1
done

echo "Database is ready!"
echo "Running migrations..."

if [ -n "$DB_USER" ] && [ -n "$DB_PASSWORD" ]; then
  echo "Using environment variables for database credentials"
  ./mvnw flyway:migrate -Dflyway.user="$DB_USER" -Dflyway.password="$DB_PASSWORD"
else
  echo "Using default database credentials from pom.xml"
  ./mvnw flyway:migrate
fi

echo "Migrations completed!"

