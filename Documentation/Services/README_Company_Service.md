# StudHire -- Company Service

## 1. Présentation

Le **Company Service** est un microservice de StudHire chargé de gérer
les profils des entreprises qui souhaitent publier des offres de stage
ou d'emploi.

## 2. Responsabilités

-   Créer et consulter un profil d'entreprise.
-   Modifier les informations du profil.
-   Gérer le statut de vérification.
-   Permettre à l'administrateur de vérifier ou rejeter une entreprise.
-   Fournir le statut de vérification aux autres microservices.

## 3. Technologies

  Technologie          Utilisation
  -------------------- ----------------------------------
  Java / Spring Boot   Développement du microservice
  PostgreSQL           Base de données
  REST API             Communication synchrone
  RabbitMQ             Communication asynchrone
  Swagger / OpenAPI    Documentation des API
  JWT                  Authentification et autorisation

*Adapter cette liste aux technologies réellement utilisées dans votre
implémentation.*

## 4. Entités principales

### Company

  Attribut             Type                 Description
  -------------------- -------------------- --------------------------------------
  id                   Long                 Identifiant
  userId               Long                 Référence vers le compte utilisateur
  companyName          String               Nom de l'entreprise
  description          String               Description
  industry             String               Secteur d'activité
  address              String               Adresse
  website              String               Site web
  logoUrl              String               URL du logo
  email                String               E-mail professionnel
  phone                String               Téléphone
  verificationStatus   VerificationStatus   Statut de vérification
  createdAt            LocalDateTime        Date de création

### VerificationStatus

-   `PENDING` : en attente.
-   `VERIFIED` : vérifiée.
-   `REJECTED` : rejetée.
-   `SUSPENDED` : suspendue.

### CompanyVerificationHistory

Entité optionnelle pour conserver l'historique des changements de statut
: administrateur, ancien et nouveau statut, commentaire et date.

## 5. Diagramme de classes UML

``` mermaid
classDiagram
    class Company {
        +Long id
        +Long userId
        +String companyName
        +String description
        +String industry
        +String address
        +String website
        +String logoUrl
        +String email
        +String phone
        +VerificationStatus verificationStatus
        +LocalDateTime createdAt
    }
    class VerificationStatus {
        <<enumeration>>
        PENDING
        VERIFIED
        REJECTED
        SUSPENDED
    }
    class CompanyVerificationHistory {
        +Long id
        +Long companyId
        +Long adminId
        +VerificationStatus oldStatus
        +VerificationStatus newStatus
        +String comment
        +LocalDateTime changedAt
    }
    class CompanyController {
        +createCompany()
        +getCompany()
        +updateCompany()
        +verifyCompany()
    }
    class CompanyService {
        +createProfile()
        +getProfile()
        +updateProfile()
        +verifyCompany()
        +recordHistory()
    }
    class CompanyRepository {
        +save()
        +findById()
        +findByUserId()
        +existsByUserId()
    }
    class CompanyEventPublisher {
        +publishCompanyVerifiedEvent()
        +publishCompanyUpdatedEvent()
    }
    Company --> VerificationStatus
    Company "1" --> "0..*" CompanyVerificationHistory : history
    CompanyController --> CompanyService
    CompanyService --> CompanyRepository
    CompanyService --> CompanyEventPublisher
```

## 6. API REST proposée

  --------------------------------------------------------------------------------------
  Méthode           Endpoint                         Rôle              Description
  ----------------- -------------------------------- ----------------- -----------------
  POST              `/companies`                     COMPANY           Créer un profil

  GET               `/companies/{id}`                PUBLIC / COMPANY  Consulter un
                                                     / ADMIN           profil

  PUT               `/companies/{id}`                COMPANY           Modifier son
                                                                       profil

  PUT               `/companies/{id}/verification`   ADMIN             Vérifier ou
                                                                       rejeter

  GET               `/companies`                     PUBLIC / ADMIN    Lister les
                                                                       entreprises

  GET               `/companies/{id}/verification`   Services internes Consulter le
                                                                       statut
  --------------------------------------------------------------------------------------

*À aligner avec les routes réellement implémentées dans le code.*

## 7. Communication interservices

### Synchrone -- REST

Avant de publier une offre, l'Offer Service peut appeler
`GET /companies/{id}/verification`. Le Company Service retourne le
statut. L'Offer Service autorise la publication uniquement si le statut
est `VERIFIED`.

### Asynchrone -- RabbitMQ

Après validation par l'administrateur, le Company Service peut publier
`CompanyVerifiedEvent`. L'Offer Service consomme cet événement sans
bloquer le Company Service.

## 8. Sécurité

-   Les requêtes protégées utilisent un token JWT.
-   Une entreprise peut modifier uniquement son propre profil.
-   Une entreprise ne peut pas valider elle-même son statut.
-   Seul l'administrateur peut vérifier ou rejeter une entreprise.
-   Les services internes doivent être autorisés à consulter le statut.

## 9. Structure indicative

``` text
company-service/
├── src/main/java/.../
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── entity/
│   ├── dto/
│   ├── security/
│   └── messaging/
├── src/main/resources/
├── README.md
└── pom.xml
```

Cette structure est indicative : adapte-la à l'organisation réelle du
dépôt.

## 10. Projet

-   **Projet :** StudHire -- Plateforme de stages et d'emploi pour les
    étudiants
-   **Module :** Architecture Web
-   **Microservice :** Company Service
