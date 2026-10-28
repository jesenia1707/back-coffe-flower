#  Coffee Flower — Backend BFF

Backend de la plataforma **Coffee Flower**, desarrollado con Spring Boot. Centraliza la lógica de integración entre el frontend y los servicios de la aplicación, gestiona información de pedidos y productos, y permite generar informes mensuales de ventas mediante Amazon SNS.

##  Descripción del proyecto

El backend BFF (Backend for Frontend) actúa como intermediario entre el frontend de Coffee Flower y los servicios y recursos de backend.

Sus responsabilidades incluyen:

- Gestionar las solicitudes relacionadas con pedidos y productos.
- Consultar y procesar información almacenada en PostgreSQL.
- Calcular informes mensuales de ventas.
- Publicar notificaciones del informe mediante Amazon SNS.
- Exponer endpoints REST para el consumo del frontend.

##  Tecnologías utilizadas

| Tecnología | Función |
|---|---|
| Java 21 | Lenguaje de programación |
| Spring Boot 3.4.4 | Framework del backend |
| Spring Data JPA | Acceso y persistencia de datos |
| PostgreSQL | Base de datos |
| Amazon SNS | Publicación de mensajes y notificaciones |
| Docker | Contenerización de la aplicación |
| Git y GitHub | Control de versiones |
| GitHub Container Registry (GHCR) | Almacenamiento de imágenes Docker |
| AWS EC2 | Despliegue del backend |

## Arquitectura general

El frontend consume los endpoints REST expuestos por el BFF. Este procesa las solicitudes, consulta PostgreSQL y utiliza Amazon SNS para publicar el informe mensual.

```text
Frontend Coffee Flower
        |
        v
Backend BFF - Spring Boot
        |
        +------> PostgreSQL
        |
        +------> Amazon SNS
                     |
                     v
              Flujo de notificaciones
              SNS / SQS / Lambda
              (integración por verificar)
```

**Nota:** la integración final de SQS y Lambda para procesar y enviar el informe en HTML se encuentra pendiente de validación.

## Informe mensual de ventas

El servicio `InformeComprasService` genera un resumen mensual utilizando los pedidos registrados en PostgreSQL.

El informe incluye:

- Período mensual del informe.
- Cantidad de pedidos registrados.
- Cantidad total de unidades vendidas.
- Total de ventas expresado en pesos chilenos (CLP).
- Identificador del mensaje publicado en Amazon SNS.

El cálculo considera las fechas según la zona horaria `America/Santiago` y excluye los pedidos con estado `CANCELADO` o `CANCELLED`.

El servicio prepara una plantilla HTML con una presentación visual en tonos café y crema. Para que el correo se muestre con ese diseño, el componente encargado del envío debe procesar el contenido como HTML; la publicación directa en una suscripción estándar de correo SNS no garantiza ese resultado.

## 🔌 Endpoint del informe

| Propiedad | Valor |
|---|---|
| Método | `POST` |
| Ruta | `/api/reportes/compras` |
| Puerto local | `8080` |
| Función | Generar y publicar el informe mensual |

### Ejemplo de solicitud

```http
POST /api/reportes/compras
```

No requiere un cuerpo JSON para la prueba realizada.

### Ejemplo de respuesta

La respuesta observada actualmente en el endpoint es:

```json
{
  "cantidadPedidos": 59,
  "mensaje": "Informe mensual generado y enviado correctamente."
}
```

La respuesta exacta depende de cómo el controlador construya el resultado del servicio.

## Requisitos previos

Para ejecutar el backend localmente necesitas:

- JDK 21.
- Docker, si deseas ejecutarlo en un contenedor.
- Acceso a una instancia PostgreSQL.
- Credenciales y permisos AWS para publicar mensajes en Amazon SNS.
- Git para descargar el repositorio.

## Ejecución local

### 1. Clonar el repositorio

```bash
git clone https://github.com/jesenia1707/back-coffe-flower.git
cd back-coffe-flower
```

Si el proyecto Spring Boot está dentro de la carpeta `backend`, entra en ella:

```bash
cd backend
```

### 2. Configurar las variables de entorno

Configura las variables que utiliza la aplicación:

| Variable | Descripción |
|---|---|
| `DB_URL` | URL JDBC de PostgreSQL |
| `DB_USER` | Usuario de PostgreSQL |
| `DB_PASSWORD` | Contraseña de PostgreSQL |
| `AWS_REGION` o `APP_AWS_REGION` | Región AWS, según la configuración utilizada |
| `AWS_SNS_TOPIC_ARN` | ARN del tema SNS del informe |
| `SNS_TOPIC_ARN` | Variable alternativa para el tema SNS |
| `FRONTEND_ORIGIN` | Origen permitido del frontend |
| `GATEWAY_SECRET` | Secreto del gateway, si la aplicación lo requiere |

El servicio de informes busca primero `AWS_SNS_TOPIC_ARN` y, si no está definida, utiliza `SNS_TOPIC_ARN`.

**Seguridad:** no publiques contraseñas, tokens, secretos ni archivos `.env` en GitHub.

### 3. Compilar y ejecutar pruebas

Si el proyecto incluye Gradle Wrapper:

En Windows:

```cmd
gradlew.bat clean build
```

En Linux o macOS:

```bash
./gradlew clean build
```

### 4. Ejecutar la aplicación

```cmd
gradlew.bat bootRun
```

El backend queda disponible en:

```text
http://localhost:8080
```

## Docker

La imagen publicada del backend es:

```text
ghcr.io/jesenia1707/back-coffe-flower:latest
```

### Descargar la imagen

```bash
docker pull ghcr.io/jesenia1707/back-coffe-flower:latest
```

### Ejecutar el contenedor

Configura primero las variables de entorno necesarias y ejecuta:

```bash
docker run -d \
  --name coffee-flower-bff \
  --restart unless-stopped \
  -p 8080:8080 \
  --env-file /ruta/al/archivo.env \
  ghcr.io/jesenia1707/back-coffe-flower:latest
```

En Windows, adapta la ruta del archivo de variables a tu sistema operativo.

### Consultar los registros

```bash
docker logs --tail 100 coffee-flower-bff
```

### Probar el endpoint desde el contenedor

```bash
curl -i -X POST http://localhost:8080/api/reportes/compras
```

##  Despliegue en AWS

El backend está desplegado en una instancia Amazon EC2 mediante Docker.

El procedimiento de actualización consiste en:

1. Construir una nueva imagen Docker.
2. Publicar la imagen en GHCR.
3. Descargar la imagen actualizada en EC2.
4. Recrear el contenedor conservando sus variables de entorno.
5. Verificar los registros de Spring Boot.
6. Probar los endpoints desplegados.

La aplicación utiliza la región AWS `us-east-1` para la configuración de SNS mostrada en el proyecto.

## Verificación del informe

Para comprobar el funcionamiento del informe:

1. Ejecutar el endpoint `POST /api/reportes/compras`.
2. Verificar la respuesta HTTP.
3. Revisar los registros del contenedor.
4. Comprobar la publicación en el tema SNS.
5. Verificar la entrega del correo.
6. Cuando se complete la integración, comprobar el procesamiento mediante SQS y Lambda y la representación HTML del correo.

Una respuesta HTTP 200 confirma que la solicitud fue procesada según el controlador, pero por sí sola no garantiza que el correo haya llegado ni que se haya renderizado como HTML.

## Componentes principales

```text
src/main/java/com/coffeeflower/bff/
├── config/
│   └── SnsConfig.java
├── controller/
│   ├── PedidosController.java
│   └── ProductController.java
├── model/
│   ├── PedidoEntity.java
│   ├── PedidoItemEntity.java
│   └── ProductEntity.java
├── repository/
│   ├── PedidoRepository.java
│   └── PedidoItemRepository.java
└── service/
    └── InformeComprasService.java
```

Esta estructura representa los componentes identificados en el código compartido; pueden existir otros controladores, modelos, repositorios y servicios en el proyecto.

## Seguridad

- Mantener las credenciales fuera del repositorio.
- Utilizar roles IAM y permisos mínimos necesarios.
- Restringir el acceso a los puertos y endpoints.
- No incluir secretos en las imágenes Docker.
- No registrar contraseñas ni tokens en los logs.

##  Repositorio

Código fuente: [back-coffe-flower en GitHub](https://github.com/jesenia1707/back-coffe-flower)

---

**Coffee Flower** — Backend de integración para la plataforma de pedidos y ventas.
