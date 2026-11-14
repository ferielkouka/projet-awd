# application-service – Documentation technique

## 1. Responsabilité

Le microservice **application-service** gère le **dépôt**, le **traitement** et le **suivi des candidatures** des étudiants aux offres de stage et d'emploi.

Il ne gère ni les offres, ni les CV, ni les entretiens : il s'appuie sur les microservices correspondants pour valider ces informations, et notifie les autres services lorsqu'une candidature change d'état.

- **Bounded Context** : Candidature
- **Technologie** : Spring Boot
- **Port** : 8083
- **Nom Eureka** : `APPLICATION-SERVICE`

## 2. Modèle de données

![Diagramme de classe](../Diagrams/diagramme_classe_application.png)

### Entités

**Application**

| Attribut | Type | Description |
|---|---|---|
| id | Long | Identifiant |
| offerId | Long | Référence de l'offre (offer-service) |
| studentId | String | Identifiant de l'étudiant (Keycloak, UUID) |
| cvId | Long | Référence du CV (cv-service) |
| dateCandidature | LocalDateTime | Date de dépôt |
| statut | ApplicationStatus | PENDING / UNDER_REVIEW / ACCEPTED / REJECTED / WITHDRAWN |
| lettreMotivation | String | Lettre de motivation (optionnelle) |

**ApplicationStatusHistory**

| Attribut | Type | Description |
|---|---|---|
| id | Long | Identifiant |
| applicationId | Long | Référence de la candidature |
| ancienStatut | ApplicationStatus | Statut avant changement |
| nouveauStatut | ApplicationStatus | Statut après changement |
| dateChangement | LocalDateTime | Date du changement |
| modifiePar | String | Identifiant de l'auteur du changement |

**ApplicationNote**

| Attribut | Type | Description |
|---|---|---|
| id | Long | Identifiant |
| applicationId | Long | Référence de la candidature |
| auteurId | String | Identifiant du recruteur auteur de la note |
| contenu | String | Note interne (visible uniquement par l'entreprise) |
| dateCreation | LocalDateTime | Date de création |

> Permet au recruteur de garder des remarques internes sur un candidat (ex. points forts, réserves) sans les exposer à l'étudiant.

**OutboxEvent**

| Attribut | Type | Description |
|---|---|---|
| id | Long | Identifiant |
| aggregateId | Long | Identifiant de la candidature concernée |
| typeEvenement | String | ex. `application.accepted` |
| payload | String (JSON) | Contenu de l'événement |
| statut | OutboxStatus | PENDING / SENT / FAILED |
| dateCreation | LocalDateTime | Date de création |
| dateEnvoi | LocalDateTime | Date d'envoi effectif vers RabbitMQ |

> Implémente le **pattern Outbox** : chaque changement de statut est écrit en base **dans la même transaction** que l'événement correspondant. Un planificateur relit les événements `PENDING` et les publie vers RabbitMQ, garantissant qu'aucun événement n'est perdu même en cas de panne entre l'écriture en base et la publication.

### Enums

- `ApplicationStatus` : PENDING, UNDER_REVIEW, ACCEPTED, REJECTED, WITHDRAWN
- `OutboxStatus` : PENDING, SENT, FAILED
- `WithdrawalReason` (optionnel, si un étudiant retire sa candidature) : OFFER_NO_LONGER_RELEVANT, FOUND_ANOTHER_OPPORTUNITY, OTHER

### Relations

- `Application` 1 —— 0..* `ApplicationStatusHistory` (composition, `@OneToMany`)
- `Application` 1 —— 0..* `ApplicationNote` (composition, `@OneToMany`)
- `Application` 1 —— 0..* `OutboxEvent` (association via `aggregateId`, pas de clé étrangère JPA)

> `offerId`, `studentId` et `cvId` sont de **simples identifiants**, pas des relations JPA ni des clés étrangères : ces données appartiennent à d'autres microservices (**Database-per-Service**). La cohérence est vérifiée par appel API, pas par la base.

## 3. Base de données

- **SGBD** : PostgreSQL
- **Base** : `application_db` (privée, aucun autre service n'y accède)
- **Tables** : `application`, `application_status_history`, `application_note`, `outbox_event`
- **Justification** : données structurées et relationnelles (statuts, historique daté, notes), besoin de cohérence transactionnelle locale lors de l'enregistrement d'un changement de statut et de son événement associé (pattern Outbox).

## 4. API REST

Base URL (via API Gateway) : `http://localhost:8080/api/applications`

### Ressource Application

| Méthode | URI | Body | Succès | Erreurs |
|---|---|---|---|---|
| POST | `/api/applications` | ApplicationRequest | 201 Created | 400, 403, 404, 409, 503 |
| GET | `/api/applications/{id}` | – | 200 OK | 403, 404 |
| GET | `/api/applications/student/{studentId}` | – | 200 OK | 403 |
| GET | `/api/applications/offer/{offerId}` | – | 200 OK | 403, 404 |
| PATCH | `/api/applications/{id}/status` | `{ "statut": "UNDER_REVIEW" }` | 200 OK | 400, 403, 404 |
| DELETE | `/api/applications/{id}` | `{ "raison": "OTHER" }` | 204 No Content | 403, 404 |

### Sous-ressource Note

| Méthode | URI | Body | Succès | Erreurs |
|---|---|---|---|---|
| POST | `/api/applications/{id}/notes` | NoteRequest | 201 Created | 400, 403, 404 |
| GET | `/api/applications/{id}/notes` | – | 200 OK | 403, 404 |

### Codes d'erreur

| Code | Cas |
|---|---|
| 400 Bad Request | Transition de statut invalide |
| 403 Forbidden | L'appelant n'est pas le propriétaire de la ressource (étudiant ou entreprise non concernés) |
| 404 Not Found | Candidature, offre ou CV introuvable |
| 409 Conflict | Candidature déjà déposée pour cette offre par cet étudiant |
| 503 Service Unavailable | offer-service ou cv-service indisponible (circuit breaker) |

### Exemples

```json
// POST /api/applications  (ApplicationRequest)
{
  "offerId": 7,
  "cvId": 4,
  "lettreMotivation": "Je suis très motivé par ce stage en Data Science..."
}

// 201 Created  (ApplicationResponse)
{
  "id": 12,
  "offerId": 7,
  "studentId": "a1b2c3d4-...",
  "cvId": 4,
  "dateCandidature": "2026-11-14T09:30:00",
  "statut": "PENDING"
}

// POST /api/applications/12/notes  (NoteRequest)
{
  "contenu": "Bon profil technique, à prioriser pour l'entretien"
}
```

### Règles métier

- Une candidature est créée **uniquement si l'offre est active** (vérification par OpenFeign sur offer-service) et **si le CV appartient à l'étudiant** (vérification par OpenFeign sur cv-service). `studentId` est extrait du token JWT, jamais du body.
- Un étudiant ne peut pas postuler deux fois à la même offre (`existsByStudentIdAndOfferId`).
- Transitions de statut autorisées : `PENDING → UNDER_REVIEW`, `UNDER_REVIEW → ACCEPTED/REJECTED`, `PENDING/UNDER_REVIEW → WITHDRAWN`. `ACCEPTED`, `REJECTED` et `WITHDRAWN` sont des statuts finaux.
- Chaque changement de statut crée une entrée dans `ApplicationStatusHistory` **et** un `OutboxEvent` correspondant, dans la même transaction.
- Les notes internes (`ApplicationNote`) ne sont jamais exposées à l'étudiant.
- Seul le recruteur propriétaire de l'offre peut modifier le statut ou ajouter une note sur une candidature la concernant.

### Documentation Swagger

Générée automatiquement avec `springdoc-openapi` : `http://localhost:8083/swagger-ui.html`

## 5. Interactions avec les autres microservices

| Sens | Service | Mode | Données échangées | Justification |
|---|---|---|---|---|
| Appelle | offer-service | **Synchrone** (OpenFeign, REST) | `GET /api/offers/{id}` → id, statut, companyId | Besoin de la réponse avant d'enregistrer la candidature |
| Appelle | cv-service | **Synchrone** (OpenFeign, REST) | `GET /api/cvs/{id}` → id, studentId | Vérifier que le CV appartient bien à l'étudiant |
| Publie | interview-service | **Asynchrone** (RabbitMQ, via Outbox) | événement `application.accepted` (applicationId, studentId, companyId) | Annoncer qu'une candidature peut donner lieu à un entretien, sans attendre de réponse |
| Consomme | interview-service | **Asynchrone** (RabbitMQ) | événement `interview.scheduled` (interviewId, applicationId, dateHeure) | Mettre à jour le statut en interne sans appel bloquant |
| Appelé par | Frontend (via API Gateway) | Synchrone (REST) | Application, ApplicationStatusHistory, ApplicationNote | Dépôt, consultation et suivi des candidatures |

Règle appliquée : **synchrone** si l'appelant a besoin de la réponse pour continuer, **asynchrone** pour annoncer un fait.

### Cas de panne

- **offer-service ou cv-service indisponible** : l'appel OpenFeign est protégé par un **circuit breaker (Resilience4j)** avec un fallback. Le service répond **503** « Service offre/CV indisponible, réessayez plus tard » et la candidature n'est pas créée. La panne ne se propage pas au reste du système.
- **RabbitMQ indisponible au moment du changement de statut** : grâce au pattern Outbox, l'événement est tout de même enregistré en base (`OutboxEvent` en statut `PENDING`). Un planificateur retente l'envoi périodiquement : aucun événement n'est perdu.

## 6. Sécurité (Keycloak)

| Rôle | Droits |
|---|---|
| ETUDIANT | Déposer une candidature, consulter et retirer ses propres candidatures |
| ENTREPRISE | Consulter les candidatures de ses offres, modifier leur statut, ajouter des notes internes |
| ADMIN | Tout consulter |

Le jeton JWT est vérifié par l'API Gateway puis par le service (Resource Server).

## 7. Configuration et architecture

### Dépendances

Spring Web, Spring Data JPA, PostgreSQL Driver, Eureka Discovery Client, Config Client, OpenFeign, Resilience4j, Spring AMQP (RabbitMQ), Spring Scheduling (relais Outbox), OAuth2 Resource Server, Validation, Lombok, `springdoc-openapi-starter-webmvc-ui`.

### application.properties (exemple)

```properties
spring.application.name=application-service
server.port=8083
spring.config.import=optional:configserver:http://localhost:8888

spring.datasource.url=jdbc:postgresql://localhost:5432/application_db
spring.datasource.username=postgres
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update

eureka.client.service-url.defaultZone=http://localhost:8761/eureka

spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672

spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/stages
```

### Route dans l'API Gateway

```properties
spring.cloud.gateway.routes[0].id=application-service
spring.cloud.gateway.routes[0].uri=lb://APPLICATION-SERVICE
spring.cloud.gateway.routes[0].predicates[0]=Path=/api/applications/**
```

### Structure du projet

```
Backend/application-service/
└── src/main/java/.../applicationservice/
    ├── entity/        Application, ApplicationStatusHistory, ApplicationNote, OutboxEvent, enums
    ├── repository/    ApplicationRepository, ApplicationStatusHistoryRepository,
    │                  ApplicationNoteRepository, OutboxEventRepository
    ├── service/       ApplicationService, OutboxRelayScheduler
    ├── controller/    ApplicationController
    ├── client/        OfferClient, CVClient (OpenFeign) + fallback
    ├── messaging/      publisher / listener RabbitMQ
    ├── dto/           ApplicationRequest, ApplicationResponse, NoteRequest
    └── exception/     GlobalExceptionHandler, ResourceNotFoundException
```
