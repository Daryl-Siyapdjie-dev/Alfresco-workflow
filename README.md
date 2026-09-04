# GED Alfresco - Projet de développement personnalisable

Ce dépôt n'est plus un simple assemblage d'images Docker officielles : `alfresco` et `content-app` sont
désormais **construits localement** (`build:`) à partir de code source que vous pouvez modifier :

* `backend/repo-amp` : module Maven "Simple Module" Alfresco (modèle de contenu, behaviors Java, web scripts, workflow BPMN).
* `frontend/alfresco-content-app` : fork de l'Alfresco Content App (Angular/ADF), personnalisable.

Voir [backend/repo-amp/README.md](backend/repo-amp/README.md) et [extensions/README.md](extensions/README.md) pour le détail du workflow de développement.

## Configuration Requise
* **Docker** & **Docker Compose** installés.
* **Mémoire RAM minimale allouée à Docker** : Au moins **6 Go à 8 Go** (idéalement 8 Go+). Vous pouvez configurer cela dans les paramètres de Docker Desktop (Settings > Resources > Advanced).
* **Java 21** et **Maven** (pour compiler `backend/repo-amp` en local si vous ne passez pas uniquement par `docker compose build`). Java 21 car c'est la version utilisee par l'image `alfresco-content-repository-community:26.1.0`.
* **Node.js** (version alignée sur `frontend/alfresco-content-app/.nvmrc`) pour développer/tester le frontend en dehors de Docker.

---

## Démarrage et Commandes

### 1. Démarrer Alfresco
Le build du frontend (Angular) se fait sur l'hôte (comme dans le pipeline officiel de l'Alfresco Content App) :
compiler ~1000 paquets npm à l'intérieur de Docker s'est avéré peu fiable (coupures réseau du sandbox de build).
`docker compose build alfresco`, lui, compile bien le module Maven `backend/repo-amp` à l'intérieur d'un conteneur
(pas besoin de Java/Maven en local pour ça, seulement si vous voulez compiler/tester en dehors de Docker).

```bash
# 1. Build du frontend (une fois, puis a chaque modification de frontend/alfresco-content-app)
cd frontend/alfresco-content-app && npm install && npm run build.release && cd ../..

# 2. Build des images + demarrage de tous les conteneurs
docker compose up -d --build
```
Le premier démarrage/build peut prendre plusieurs minutes : compilation Maven du module repository (gros arbre
de dépendances Alfresco/Camel, plus rapide une fois le cache `~/.m2` chaud), puis initialisation de PostgreSQL,
Solr et du dépôt Alfresco.

### 2. Rebuild après une modification
```bash
# Après avoir modifié backend/repo-amp (modèle, behavior, workflow, webscript)
docker compose up -d --build alfresco

# Après avoir modifié frontend/alfresco-content-app
cd frontend/alfresco-content-app && npm run build.release && cd ../..
docker compose up -d --build content-app
```

### 3. Consulter les Logs
Pour suivre l'état de démarrage du serveur de dépôt principal (Repository) :
```bash
docker compose logs -f alfresco
```
Pour suivre l'ensemble des services :
```bash
docker compose logs -f
```

### 4. Arrêter Alfresco
Pour arrêter les services en conservant les données (les bases de données et documents restent persistés dans les volumes) :
```bash
docker compose stop
```
Pour arrêter et supprimer les conteneurs :
```bash
docker compose down
```
Pour supprimer complètement les conteneurs ET les volumes de données (Remise à zéro complète) :
```bash
docker compose down -v
```

---

## Adresses et Identifiants par défaut

Une fois que tous les conteneurs affichent un statut **healthy** (que vous pouvez vérifier via `docker compose ps`), vous pouvez accéder aux services suivants :

| Service / Interface | URL Locale | Identifiants par défaut |
| :--- | :--- | :--- |
| **Alfresco Share UI (Classique)** | [http://localhost:9090/share](http://localhost:9090/share) | Identifiant : `admin`<br>Mot de passe : `admin` |
| **Alfresco Content App (Moderne)** | [http://localhost:9090/content-app](http://localhost:9090/content-app) | Identifiant : `admin`<br>Mot de passe : `admin` |
| **Alfresco API Repository** | [http://localhost:9090/alfresco](http://localhost:9090/alfresco) | Identifiant : `admin`<br>Mot de passe : `admin` |
| **ActiveMQ Web Console** | [http://localhost:8161](http://localhost:8161) | Identifiant : `admin`<br>Mot de passe : `admin` |
| **Solr 6 Search Console** | [http://localhost:8083/solr/](http://localhost:8083/solr/) | Accès direct (sans authentification par défaut en dev) |
| **Transform Core AIO Status** | [http://localhost:8090/ready](http://localhost:8090/ready) | Accès direct |

---

## Vérifier que vos personnalisations sont bien déployées

```bash
# Web script custom (backend/repo-amp)
curl -u admin:admin "http://localhost:9090/alfresco/service/ged/hello?name=Daryl"

# Workflow BPMN custom deploye
curl -u admin:admin "http://localhost:9090/alfresco/api/-default-/public/workflow/versions/1/process-definitions" | grep gedApproval
```
Vous devriez aussi voir le titre **"GED Documentaire"** dans le bandeau de la Content App (preuve que le frontend custom est bien servi).

---

## Structure de l'espace de travail

* `docker-compose.yaml` : Définition des conteneurs, configurations d'environnement, allocation mémoire et persistance.
* `Dockerfile.alfresco` : build multi-stage (Maven) qui compile `backend/repo-amp` et l'installe dans l'image Alfresco.
* `Dockerfile.content-app` : build multi-stage (Node/Nginx) qui compile `frontend/alfresco-content-app`.
* `backend/repo-amp/` : module Maven "Simple Module" Alfresco (modèle de contenu, behaviors Java, web scripts, workflow BPMN).
* `frontend/alfresco-content-app/` : fork de l'Alfresco Content App (Angular/ADF).
* [commons/base.yaml](commons/base.yaml) : configuration d'avertissement et routage Traefik.
* [extensions/README.md](extensions/README.md) : guide détaillé pour créer, ajouter et personnaliser des modules et modèles de documents.
