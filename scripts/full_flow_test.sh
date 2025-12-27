#!/usr/bin/env bash
set -euo pipefail

BASE_URL="http://localhost:8080"

# Función para limpiar después de pruebas
cleanup() {
  echo "Limpio datos de prueba..."
  # Aquí se podrían añadir llamadas para borrar los datos creados si la API lo permite
}

echo "=== 1) Crear usuario ==="

USER_PAYLOAD=$(cat <<EOF
{
  "username": "flowuser",
  "email": "flowuser@example.com",
  "password": "password123",
  "fullName": "Flow User"
}
EOF
)

USER_JSON=$(curl -sS -X POST "$BASE_URL/api/users" \
  -H "Content-Type: application/json" \
  -d "$USER_PAYLOAD")

echo "Usuario creado: $USER_JSON"

USER_ID=$(echo "$USER_JSON" | jq -r '.id')
echo "USER_ID = $USER_ID"

echo
echo "=== PRUEBA DEDUPLICACIÓN: Intentar crear el mismo usuario ==="
DUPLICATED_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/users" \
  -H "Content-Type: application/json" \
  -d "$USER_PAYLOAD")

if [ "$DUPLICATED_STATUS" -eq 409 ]; then
  echo "Deduplicación OK (409 Conflict recibido)"
else
  echo "Deduplicación FALLÓ (Esperaba 409, recibí $DUPLICATED_STATUS)"
  exit 1
fi

echo
echo "=== 2) Crear proyecto para ese usuario ==="

PROJECT_PAYLOAD=$(cat <<EOF
{
  "name": "Flow Project",
  "owner": {
    "id": $USER_ID,
    "username": "flowuser",
    "email": "flowuser@example.com",
    "fullName": "Flow User",
    "active": true
  }
}
EOF
)

PROJECT_JSON=$(curl -sS -X POST "$BASE_URL/projects" \
  -H "Content-Type: application/json" \
  -d "$PROJECT_PAYLOAD")

echo "Proyecto creado: $PROJECT_JSON"

PROJECT_ID=$(echo "$PROJECT_JSON" | jq -r '.id')
echo "PROJECT_ID = $PROJECT_ID"

echo
echo "=== 3) Crear tres tasks en el proyecto ==="

# Task 1
TASK1_PAYLOAD=$(cat <<EOF
{
  "title": "Flow Task 1",
  "description": "Primera task del flujo",
  "status": "TODO",
  "projectId": $PROJECT_ID,
  "assigneeId": $USER_ID
}
EOF
)

TASK1_JSON=$(curl -sS -X POST "$BASE_URL/tasks/newtask" \
  -H "Content-Type: application/json" \
  -d "$TASK1_PAYLOAD")

echo "Task1 creada: $TASK1_JSON"
TASK1_ID=$(echo "$TASK1_JSON" | jq -r '.id')

# Task 2
TASK2_PAYLOAD=$(cat <<EOF
{
  "title": "Flow Task 2",
  "description": "Segunda task del flujo",
  "status": "TODO",
  "projectId": $PROJECT_ID,
  "assigneeId": $USER_ID
}
EOF
)

TASK2_JSON=$(curl -sS -X POST "$BASE_URL/tasks/newtask" \
  -H "Content-Type: application/json" \
  -d "$TASK2_PAYLOAD")

echo "Task2 creada: $TASK2_JSON"
TASK2_ID=$(echo "$TASK2_JSON" | jq -r '.id')

# Task 3
TASK3_PAYLOAD=$(cat <<EOF
{
  "title": "Flow Task 3",
  "description": "Tercera task del flujo",
  "status": "TODO",
  "projectId": $PROJECT_ID,
  "assigneeId": $USER_ID
}
EOF
)

TASK3_JSON=$(curl -sS -X POST "$BASE_URL/tasks/newtask" \
  -H "Content-Type: application/json" \
  -d "$TASK3_PAYLOAD")

echo "Task3 creada: $TASK3_JSON"
TASK3_ID=$(echo "$TASK3_JSON" | jq -r '.id')

echo
echo "TASK1_ID = $TASK1_ID"
echo "TASK2_ID = $TASK2_ID"
echo "TASK3_ID = $TASK3_ID"

echo
echo "=== 4) Transicionar estados de algunas tasks ==="
echo "Task1: TODO -> IN_PROGRESS -> DONE"
curl -sS -X PATCH "$BASE_URL/tasks/$TASK1_ID/status?status=IN_PROGRESS" | jq '.status'
curl -sS -X PATCH "$BASE_URL/tasks/$TASK1_ID/status?status=DONE" | jq '.status'

echo
echo "Task2: TODO -> IN_PROGRESS"
curl -sS -X PATCH "$BASE_URL/tasks/$TASK2_ID/status?status=IN_PROGRESS" | jq '.status'

echo
echo "Task3 queda en TODO (sin cambios de estado)"

echo
echo "=== 5) Listar tasks del proyecto para verificar ==="
curl -sS "$BASE_URL/tasks/project/$PROJECT_ID" | jq '.'

echo
echo "=== EXTRAS: Probar fallos esperados ==="
echo "Crear task en proyecto inexistente (9999)"
INVALID_PROJECT_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/tasks/newtask" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"Invalid\", \"projectId\": 9999, \"status\": \"TODO\"}")

if [ "$INVALID_PROJECT_STATUS" -eq 404 ]; then
  echo "Validación de proyecto inexistente OK (404 recibido)"
else
  echo "Validación de proyecto inexistente FALLÓ (Esperaba 404, recibí $INVALID_PROJECT_STATUS)"
  exit 1
fi

echo
echo "Flujo completo ejecutado correctamente."
