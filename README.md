# FitConnect - Systeme de reservation et de paiement de cours de sport

Projet Efrei M2 - Microservices (Spring Boot 3.4.5, Spring Cloud 2024.0.1, Java 17).

FitConnect permet a des utilisateurs de reserver des cours dans des salles de sport partenaires.
La plateforme est composee de **sept modules Maven** : trois d'infrastructure (Eureka, Config Server,
API Gateway) et **quatre microservices metier** orchestres selon le pattern **Saga** avec paiement differe
("Reserve Now, Pay Later").

## Sommaire

1. [Architecture](#architecture)
2. [Demarrage rapide](#demarrage-rapide)
3. [Les microservices](#les-microservices)
4. [Workflow de reservation (Saga)](#workflow-de-reservation-saga)
5. [Verrouillage optimiste](#verrouillage-optimiste)
6. [Clients Feign et Circuit Breaker](#clients-feign-et-circuit-breaker)
7. [Scheduler](#scheduler)
8. [Tests](#tests)
9. [Collection Postman](#collection-postman)
10. [Choix techniques](#choix-techniques)
11. [Structure du depot](#structure-du-depot)

---

## Architecture

```
                              +------------------+
                              |  eureka-server   |  :8761  (annuaire de services)
                              +--------^---------+
                                       |  enregistrement / decouverte
        +------------------------------+--------------------------------------+
        |                              |                                      |
+-------+----------+     +-------------+-----------+     +--------------------+-----+
|   api-gateway    |     |      class-service      |     |     booking-service      |
|      :8080       +---->|          :8091          |<----+          :8092           |
| 4 routes /api/** |     |  CRUD cours, filtres,   |     |  Saga : reservation ->   |
+--------+---------+     |  @Version, H2 classdb   |     |  paiement -> annulation  |
         |               +-------------------------+     |  scheduler, H2 bookingdb |
         |                                               +-----+---------------+----+
         |                                                     |               |
         |                +-------------------------+          |   +-----------v-------------+
         |                |     payment-service     |<---------+   |  notification-service   |
         |                |          :8093          |              |          :8094          |
         |                |  simulation paiement,   |              |  emails/SMS simules,    |
         |                |  remboursement,         |              |  retry, H2              |
         |                |  H2 paymentdb           |              |  notificationdb         |
         |                +-------------------------+              +-------------------------+
         v
+------------------+
|  config-server   |  :8888  (profil native, config-repo/*.yml)
+------------------+
```

| Module | Port | Role |
|---|---|---|
| eureka-server | 8761 | Registre de services (Netflix Eureka) |
| config-server | 8888 | Configuration centralisee (`config-server/config-repo/`) |
| api-gateway | 8080 | Point d'entree unique, routage `lb://` vers les 4 services |
| class-service | 8091 | Catalogue des cours, gestion des places (verrouillage optimiste) |
| booking-service | 8092 | Reservations, orchestration de la saga, scheduler |
| payment-service | 8093 | Paiements et remboursements (simulation) |
| notification-service | 8094 | Notifications email/SMS (simulation), retry |

Chaque service metier possede sa propre base H2 en memoire (`classdb`, `bookingdb`, `paymentdb`,
`notificationdb`), sa console H2 (`/h2-console`) et son Swagger UI (`/swagger-ui.html`).

---

## Demarrage rapide

### Prerequis

- Java 17, Maven 3.9+
- Docker Desktop (optionnel, pour le mode Docker Compose)

### Option A : Docker Compose (recommande)

```bash
docker compose up --build
```

Les sept conteneurs demarrent dans l'ordre grace aux `healthcheck` (Eureka -> Config Server -> services
metier -> booking-service -> gateway). Comptez une a deux minutes la premiere fois (build Maven dans les images).

### Option B : en local, service par service

Lancer dans cet ordre, chacun dans un terminal :

```bash
mvn -pl eureka-server spring-boot:run
mvn -pl config-server spring-boot:run
mvn -pl class-service spring-boot:run
mvn -pl payment-service spring-boot:run
mvn -pl notification-service spring-boot:run
mvn -pl booking-service spring-boot:run
mvn -pl api-gateway spring-boot:run
```

> Le config-server doit etre lance depuis la racine du projet (il lit `file:./config-server/config-repo`).
> Les services fonctionnent aussi sans config-server (`optional:configserver:`) grace a leur
> `application.properties` local, mais la configuration de reference est celle du `config-repo`.

### Verifier

| URL | Attendu |
|---|---|
| http://localhost:8761 | Dashboard Eureka avec les 5 services enregistres |
| http://localhost:8888/booking-service/default | Configuration servie par le config-server |
| http://localhost:8080/api/classes | Liste paginee des 6 cours du jeu de donnees |
| http://localhost:8091/swagger-ui.html | Swagger du class-service (idem 8092, 8093, 8094) |
| http://localhost:8080/actuator/gateway/routes | Les 4 routes de la gateway |

Un fichier [requests/fitconnect.http](requests/fitconnect.http) (IntelliJ / VS Code REST Client) rejoue tout
le scenario, et la [collection Postman](postman/FitConnect.postman_collection.json) est decrite plus bas.

---

## Les microservices

### class-service (:8091)

Entite `FitnessClass` : `name` (min 3), `description`, `instructor`, `gymLocation`, `category`
(YOGA, CROSSFIT, ZUMBA, PILATES, SPINNING, BOXING), `level` (BEGINNER, INTERMEDIATE, ADVANCED),
`durationMinutes` (30/45/60/90 via une contrainte `@ValidDuration`), `maxParticipants` (5..30),
`currentParticipants`, `price` (>= 5.00), `dateTime` (`@FutureOrPresent`), `status`
(SCHEDULED, CANCELLED, COMPLETED) et **`@Version version`**.

| Methode | URL | Description |
|---|---|---|
| GET | `/api/classes` | Liste paginee + filtres `category`, `level`, `status`, `dateFrom`, `dateTo`, `location`, `instructor`, `page`, `size`, `sort` |
| GET | `/api/classes/search` | Recherche par `date` (jour), `dateFrom`/`dateTo`, `category`, `level`, `location`, `instructor` |
| GET | `/api/classes/{id}` | Detail d'un cours (avec `availableSpots` et `version`) |
| POST | `/api/classes` | Creer un cours (201) |
| PUT | `/api/classes/{id}` | Mettre a jour (409 si la nouvelle capacite < inscrits) |
| DELETE | `/api/classes/{id}` | Supprime un cours sans inscrit, sinon le passe en CANCELLED (204) |
| PATCH | `/api/classes/{id}/increment?spots=n` | Reserve n places (appele par booking-service) - 409 si plus de places |
| PATCH | `/api/classes/{id}/decrement?spots=n` | Libere n places (annulation / expiration) |

Les filtres sont combines dynamiquement avec des `Specification` JPA
([FitnessClassSpecifications.java](class-service/src/main/java/com/fitconnect/classservice/repository/FitnessClassSpecifications.java)).
Un jeu de donnees de 6 cours (dates relatives, toujours dans le futur) est charge par `data.sql`.

### booking-service (:8092)

Entite `Booking` : `bookingReference` (`BK-XXXXX`), `userId`, `userEmail`, `userName`, `classId`,
snapshots (`className`, `classDate`, `instructor`, `price`), `numberOfSpots` (1..4),
`totalAmount` = price x spots, `bookingDate`, `status` (PENDING_PAYMENT, CONFIRMED, CANCELLED, COMPLETED,
NO_SHOW), `paymentDeadline` = bookingDate + 1h, `cancellationDeadline` = classDate - 24h, plus le suivi du
paiement (`paymentId`, `paymentReference`, `confirmationDate`), `cancellationDate` et `reminderSent`.

| Methode | URL | Description |
|---|---|---|
| GET | `/api/bookings` | Toutes les reservations |
| GET | `/api/bookings/{id}` | Une reservation |
| GET | `/api/bookings/user/{userId}` | Reservations d'un utilisateur |
| GET | `/api/bookings/expired` | PENDING_PAYMENT dont la deadline est depassee (scheduler) |
| POST | `/api/bookings` | Reserver -> 201 PENDING_PAYMENT, 409 si plus de places, 404 si cours inconnu |
| PATCH | `/api/bookings/{id}/confirm` | Payer -> 200 CONFIRMED, 402 si paiement refuse, 409 si expire / pas en attente |
| PATCH | `/api/bookings/{id}/cancel` | Annuler -> 200 CANCELLED (+ remboursement si paye), 409 hors delai |
| PATCH | `/api/bookings/{id}/complete` | Marquer terminee (409 si non confirmee) |

### payment-service (:8093)

Entite `Payment` : `paymentReference` (`PAY-XXXXX`), `bookingId`, `bookingReference`, `userId`, `amount`,
`paymentMethod` (CREDIT_CARD, DEBIT_CARD, PAYPAL, STRIPE), `cardLastFour`, `transactionId` (genere `txn_...`
si absent), `paymentDate`, `status` (PENDING, SUCCESS, FAILED, REFUNDED), `failureReason`, `refundDate`.

| Methode | URL | Description |
|---|---|---|
| POST | `/api/payments` | Traiter un paiement -> 201 avec status SUCCESS ou FAILED ; 409 si deja paye |
| GET | `/api/payments/booking/{bookingId}` | Paiement d'une reservation (le SUCCESS/REFUNDED sinon le plus recent) |
| POST | `/api/payments/{id}/refund` | Rembourser (409 si le paiement n'est pas SUCCESS) |
| GET | `/api/payments/user/{userId}` | Historique d'un utilisateur |
| GET | `/api/payments`, `/api/payments/{id}` | Consultation |

**Simulation** : `amount < 100 EUR` -> SUCCESS, `amount >= 100 EUR` -> FAILED (seuil `payment.rejection-threshold`).
Un refus est un resultat metier persiste (201 + `status: FAILED`), pas une erreur HTTP : c'est le
booking-service qui decide de la suite.

### notification-service (:8094)

Entite `Notification` : `userId`, `email`, `type` (BOOKING_CONFIRMATION, PAYMENT_CONFIRMATION,
BOOKING_REMINDER, BOOKING_CANCELLED, CLASS_CANCELLED), `subject`, `content`, `createdDate`, `sentDate`,
`status` (PENDING, SENT, FAILED), `attempts`, `errorMessage`.

| Methode | URL | Description |
|---|---|---|
| POST | `/api/notifications` | Envoyer -> 201 (SENT ou FAILED) |
| GET | `/api/notifications/user/{userId}` | Historique |
| GET | `/api/notifications/pending` | PENDING + FAILED (a rejouer) |
| PATCH | `/api/notifications/{id}/retry` | Nouvelle tentative (409 si deja SENT) |

**Simulation** : l'envoi est journalise (`[EMAIL] to=... type=... subject=...`). Une adresse se terminant par
`@fail.test` echoue volontairement pour demontrer `FAILED` -> `retry`. Un scheduler interne rejoue les
notifications en attente toutes les 5 minutes.

---

## Workflow de reservation (Saga)

Le booking-service est l'**orchestrateur** : il appelle les autres services en cascade et applique les
compensations. Code : [BookingService.java](booking-service/src/main/java/com/fitconnect/bookingservice/service/BookingService.java).

### Cas 1 - Reservation reussie

```
POST /api/bookings {userId, userEmail, userName, classId, numberOfSpots}
  1. GET  class-service /api/classes/{classId}        -> existe ? SCHEDULED ? places suffisantes ? snapshot + totalAmount
  2. PATCH class-service /api/classes/{id}/increment   -> places reservees (verrouillage optimiste)
  3. Persistance Booking PENDING_PAYMENT               -> paymentDeadline = now + 1h, cancellationDeadline = classDate - 24h
  4. POST notification-service /api/notifications     -> BOOKING_CONFIRMATION "Payez avant {paymentDeadline}"
  5. 201 Created
```

Compensation : si la persistance echoue apres l'increment, les places sont immediatement liberees (`decrement`).

### Cas 2 - Plus de places

Si `increment` repond **409** (quelqu'un a reserve entre-temps), aucune reservation n'a ete creee : aucune
compensation necessaire, le client recoit **409 "Plus de places disponibles pour ce cours"**.

### Cas 3 - Paiement

```
PATCH /api/bookings/{id}/confirm {paymentMethod, cardLastFour, transactionId}
  1. status == PENDING_PAYMENT et paymentDeadline non depassee, sinon 409
  2. POST payment-service /api/payments {bookingId, amount = totalAmount, ...}
  3. SUCCESS -> Booking CONFIRMED (+ paymentId, paymentReference)
     FAILED  -> Booking reste PENDING_PAYMENT, reponse 402 Payment Required (le client peut retenter avant la deadline)
  4. POST notification-service -> PAYMENT_CONFIRMATION
  5. 200 OK
```

### Cas 4 - Annulation

```
PATCH /api/bookings/{id}/cancel
  1. status ni CANCELLED ni COMPLETED, cancellationDeadline non depassee, sinon 409
  2. si CONFIRMED : POST payment-service /api/payments/{paymentId}/refund
     PATCH class-service /api/classes/{id}/decrement?spots=n
  3. Booking CANCELLED
  4. POST notification-service -> BOOKING_CANCELLED
  5. 200 OK
```

Une reservation PENDING_PAYMENT peut aussi etre annulee (sans remboursement).

---

## Verrouillage optimiste

Le champ `@Version private Long version;` de `FitnessClass` protege contre la surreservation. La verification
de capacite est dans l'entite :

```java
public void incrementParticipants(int spots) {
    if (this.currentParticipants + spots > this.maxParticipants) {
        throw new NoSpotsAvailableException("Plus de places disponibles pour ce cours");
    }
    this.currentParticipants += spots;
}
```

Si deux transactions lisent la meme version puis ecrivent, Hibernate rejette la seconde
(`ObjectOptimisticLockingFailureException`), traduite en **409** par le `GlobalExceptionHandler` du
class-service. Le test [OptimisticLockingTest](class-service/src/test/java/com/fitconnect/classservice/repository/OptimisticLockingTest.java)
reproduit exactement ce scenario avec une vraie base H2. `saveAndFlush` est utilise dans `increment`/`decrement`
pour que le conflit soit detecte dans la transaction et que la `version` renvoyee soit a jour.

---

## Clients Feign et Circuit Breaker

Le booking-service declare trois clients OpenFeign resolus via Eureka (`@FeignClient(name = "class-service")`,
etc.) et proteges par **Resilience4j** (`spring.cloud.openfeign.circuitbreaker.enabled=true`). Chaque client a
une `FallbackFactory` :

| Client | Comportement en cas de panne |
|---|---|
| ClassClient | 503 `ServiceUnavailableException` : impossible de reserver sans le cours |
| PaymentClient | 503 : un paiement ne doit jamais etre "devine" ; la reservation reste PENDING_PAYMENT |
| NotificationClient | Degradation silencieuse (log) : la saga ne doit pas echouer pour une notification |

Regle commune ([FallbackSupport.java](booking-service/src/main/java/com/fitconnect/bookingservice/client/FallbackSupport.java)) :
les reponses **4xx** du service distant (404 cours inconnu, 409 plus de places, 409 deja rembourse...) sont des
erreurs *metier*, relayees telles quelles pour etre traduites par le service ; elles sont aussi exclues du calcul
du taux d'echec (`ignore-exceptions: feign.FeignException$FeignClientException`) afin de ne pas ouvrir le circuit
a cause d'un cours plein. Parametres : fenetre de 10 appels, ouverture a 50 % d'echecs, 20 s en etat ouvert,
timeout 6 s. Le client HTTP Apache 5 (`feign-hc5`) est utilise car le client JDK ne supporte pas `PATCH`.

---

## Scheduler

[BookingScheduler.java](booking-service/src/main/java/com/fitconnect/bookingservice/scheduler/BookingScheduler.java)
(activable via `booking.scheduler.enabled`) :

- **Expiration des paiements** (`fixedRate` 5 min, `booking.scheduler.expiration-rate`) : pour chaque booking
  `PENDING_PAYMENT` dont `paymentDeadline < now` -> liberation des places (`decrement`), passage en
  `CANCELLED`, notification `BOOKING_CANCELLED`. Chaque reservation est traitee isolement : une erreur n'empeche
  pas les autres.
- **Rappel des cours** (`cron` horaire, `booking.scheduler.reminder-cron`) : bookings `CONFIRMED` dont le cours a
  lieu dans les 24 prochaines heures et non encore rappeles -> `BOOKING_REMINDER`, puis `reminderSent = true`
  (un seul rappel par reservation).

Les delais sont configurables en `Duration` (`booking.payment-deadline=1h`, `booking.cancellation-deadline=24h`),
ce qui permet de rejouer le scenario "paiement expire" en quelques secondes avec `--booking.payment-deadline=1s`.

---

## Tests

```bash
mvn test
```

| Module | Classe | Contenu |
|---|---|---|
| class-service | `FitnessClassTest` | `shouldIncrementParticipants_whenSpotsAvailable` (10 places, 5 inscrits, +2 -> 7), `shouldThrowException_whenNoSpotsAvailable` (9 inscrits, +2 -> `NoSpotsAvailableException`), decrement, cours annule |
| class-service | `FitnessClassServiceTest` | Service avec repository mocke (creation, increment, 404, update, delete logique) |
| class-service | `OptimisticLockingTest` | Deux ecritures concurrentes : la seconde est rejetee (`@Version`) |
| class-service | `FitnessClassControllerIntegrationTest` | MockMvc + H2 : CRUD, filtres, pagination, `search`, increment -> 409, validation |
| booking-service | `BookingServiceTest` | **`shouldCreateBooking_whenSpotsAvailable`**, **`shouldThrowException_whenNoSpotsAvailable`**, **`shouldCancelBookingAndRefund_whenWithinDeadline`**, conflit a l'increment, compensation si la persistance echoue, paiement OK / refuse / expire, annulation hors delai, expiration, completion |
| booking-service | `BookingSagaIntegrationTest` | **`shouldCompleteFullBookingFlow`** (cours -> reservation -> paiement -> CONFIRMED, places, 2 notifications), **`shouldCancelExpiredBookings`** (scheduler -> CANCELLED, places liberees), 409 plus de places, 409 paiement expire, 402 paiement refuse, annulation + remboursement, 409 annulation hors delai, rappel envoye une seule fois, validation |
| payment-service | `PaymentServiceTest`, `PaymentControllerIntegrationTest` | Acceptation < 100, refus >= 100, remboursement, double paiement -> 409 |
| notification-service | `NotificationServiceTest`, `EmailSenderTest`, `NotificationControllerIntegrationTest` | SENT / FAILED, retry, `@fail.test` |

Les tests d'integration du booking-service tournent avec la vraie base H2 et le vrai controller ; les trois
services partenaires sont remplaces par des mocks (`@MockitoBean` sur les clients Feign), ce qui permet de
verifier precisement les appels sortants (`increment(101, 2)`, `refund(55)`, types de notifications).

---

## Collection Postman

Importer [postman/FitConnect.postman_collection.json](postman/FitConnect.postman_collection.json) et executer
les dossiers dans l'ordre (ou via le *Collection Runner*). Les dates sont calculees automatiquement et les
identifiants sont propages dans les variables de collection (`classId`, `bookingId`...). Chaque requete
contient des assertions.

1. **Gestion des cours** : creation, liste paginee, filtre `category=YOGA&level=INTERMEDIATE`, recherche, detail.
2. **Reservation** : 2 places -> `PENDING_PAYMENT`, verification des places prises (2/10) et de la notification.
3. **Paiement** : carte valide (50 EUR) -> `CONFIRMED`, paiement `SUCCESS`.
4. **Annulation** : -> `CANCELLED`, paiement `REFUNDED`, places liberees (0/10), historique des notifications.
5. **Scenarios d'erreur** : surreservation (409), paiement refuse 4 x 25 = 100 EUR (402), paiement sur une
   reservation annulee (409), paiement expire (409, voir la description de la requete), annulation hors delais
   sur un cours dans 3 h (409), validation (400), cours inexistant (404).
6. **Scheduler & notifications** : reservations expirees, notifications en attente, echec simule
   (`@fail.test`) puis `retry`.

---

## Choix techniques

- **Snapshot dans Booking** : nom, date, instructeur et prix du cours sont copies a la reservation ; une
  modification ulterieure du cours n'altere pas les reservations existantes, et le booking-service reste
  consultable meme si class-service est indisponible.
- **Paiement refuse = 402 et reservation conservee** : le sujet laisse le choix entre CANCELLED et
  PENDING_PAYMENT ; garder PENDING_PAYMENT permet a l'utilisateur de retenter avec un autre moyen de paiement
  jusqu'a la deadline, le scheduler faisant le menage ensuite.
- **Suppression d'un cours = annulation logique** s'il a des inscrits, pour conserver l'integrite des
  references `classId` cote booking-service.
- **Reponses d'erreur homogenes** (`ApiError {status, error, message, timestamp}`) dans les quatre services.
- **Configuration a deux niveaux** : `application.properties` local suffisant pour demarrer seul, et
  `config-repo/*.yml` comme source de verite centralisee (ports, bases, regles metier, resilience).
- **Docker Compose** : sept services, `healthcheck` + `depends_on: condition` pour un demarrage ordonne,
  variables `CONFIG_SERVER_URL` / `EUREKA_URL` injectees.

---

## Structure du depot

```
.
|-- pom.xml                         # POM parent (7 modules)
|-- docker-compose.yml
|-- README.md
|-- eureka-server/
|-- config-server/
|   `-- config-repo/                # application.yml, api-gateway.yml, class-service.yml,
|                                   # booking-service.yml, payment-service.yml, notification-service.yml
|-- api-gateway/
|-- class-service/
|-- booking-service/
|-- payment-service/
|-- notification-service/
|-- postman/FitConnect.postman_collection.json
`-- requests/fitconnect.http
```