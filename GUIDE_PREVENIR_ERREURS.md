# Guide Complet d'Optimisation et de Résolution des Erreurs Alfresco

Ce guide a pour but de vous aider à utiliser Alfresco Community Edition sans rencontrer de problèmes techniques (erreurs mémoire, conflits de ports, problèmes de démarrage).

---

## 1. Dépôt Source Officiel (Git)
Le dépôt officiel d'Alfresco utilisé pour générer cette configuration Docker Compose est :
👉 **[Alfresco/acs-deployment (GitHub)](https://github.com/Alfresco/acs-deployment)**

Nous avons extrait la configuration de la version Community stable depuis le sous-dossier `docker-compose`.

---

## 2. Erreur la plus fréquente : Manque de Mémoire (OOM - Out of Memory)

### Symptôme
Un conteneur (souvent `alfresco` ou `solr6`) s'arrête tout seul quelques secondes/minutes après le démarrage, sans message d'erreur clair dans les logs, ou avec le code d'erreur `137`.

### Cause
Alfresco est composé d'applications Java (Tomcat, Solr, ActiveMQ) gourmandes en RAM. Par défaut, la configuration nécessite environ **6 Go à 8 Go de RAM**. Si votre moteur Docker n'a pas cette quantité allouée, le système d'exploitation arrête les conteneurs (OOM Killer).

### Solution
1. Ouvrez l'application **Docker Desktop** sur votre Mac.
2. Allez dans **Settings** (icône d'engrenage) > **Resources** > **Virtual drawers/RAM**.
3. Réglez la RAM allouée à au moins **8 GB** (10 GB ou 12 GB recommandés si votre Mac possède 16 Go).
4. Réglez le nombre de CPU sur au moins **4**.
5. Cliquez sur **Apply & restart**.

---

## 3. Conflits de Ports Réseau

### Symptôme
Le message suivant s'affiche lors du lancement :
`Bind for 0.0.0.0:8080 failed: port is already allocated` ou `port 5432 is in use`.

### Cause
Une autre application sur votre Mac utilise déjà un des ports requis par Alfresco (notamment `8080` pour le serveur web proxy ou `5432` pour PostgreSQL).

### Solution
Identifiez et arrêtez l'application en conflit :

* **Pour le port 8080** (souvent utilisé par d'autres serveurs web locaux) :
  ```bash
  lsof -i :8080
  ```
  Si un processus apparaît, tuez-le avec `kill -9 <PID>` ou arrêtez le service correspondant.
* **Pour le port 5432** (souvent occupé par une instance locale de PostgreSQL) :
  ```bash
  lsof -i :5432
  ```
  Arrêtez PostgreSQL local s'il tourne (par exemple via `brew services stop postgresql`).

> [!TIP]
> Si vous ne pouvez pas libérer le port `8080`, vous pouvez modifier le mappage de ports du conteneur `proxy` dans votre fichier `docker-compose.yaml`. Changez `8080:8080` en `9090:8080`. Vous accéderez alors à Alfresco via `http://localhost:9090/share`.

---

## 4. Attente du démarrage complet (Erreurs 502 / Bad Gateway)

### Symptôme
Vous tentez d'ouvrir `http://localhost:8080/share` et vous obtenez une page blanche, une erreur de connexion refusée ou une erreur `502 Bad Gateway`.

### Cause
Au tout premier lancement, Alfresco crée les tables dans PostgreSQL, prépare le stockage et démarre l'indexation Solr. Ce processus prend en moyenne **3 à 5 minutes**. Le proxy Traefik renvoie une erreur 502 tant que le conteneur principal n'a pas fini de démarrer.

### Solution
Suivez le démarrage dans la console en exécutant :
```bash
docker compose logs -f alfresco
```
Attendez de voir s'afficher la ligne suivante dans les logs de Tomcat :
`INFO [main] org.apache.catalina.startup.Catalina.start Server startup in [XXXXX] ms`
Dès que cette ligne apparaît et que la commande `docker compose ps` montre le statut `healthy`, les interfaces sont accessibles.

---

## 5. Erreurs d'Indexation et de Recherche (Solr)

### Symptôme
Vous déposez un document dans Alfresco, mais la recherche textuelle ne le trouve pas, ou vous voyez des erreurs de communication Solr dans les logs d'Alfresco.

### Cause
Alfresco utilise un protocole sécurisé (SSL ou Secret Partagé) pour communiquer avec Solr. Si la clé de sécurité ou la configuration réseau dans Docker est altérée, l'indexation échoue.

### Solution
Dans notre configuration, nous utilisons l'authentification par Secret Partagé, configurée de manière identique sur les deux conteneurs :
* Côté **alfresco** : `-Dsolr.secureComms=secret` et `-Dsolr.sharedSecret=secret`
* Côté **solr6** : `ALFRESCO_SECURE_COMMS: "secret"` et `-Dalfresco.secureComms.secret=secret`

Ne modifiez pas ces variables d'environnement dans le `docker-compose.yaml` sans mettre à jour les deux côtés simultanément.

---

## 6. Problèmes de Permissions des Volumes de Données

### Symptôme
Le conteneur PostgreSQL ou Alfresco refuse de démarrer avec des erreurs d'écriture (`Permission denied`, `Cannot write to directory`).

### Cause
Sur macOS, Docker Desktop gère automatiquement le partage de fichiers via gRPC FUSE ou VirtioFS. Mais si vous utilisez des montages de répertoires hôtes (bind mounts) au lieu de volumes nommés Docker, les ID d'utilisateurs Linux dans le conteneur (ex: `postgres` ou `tomcat`) peuvent entrer en conflit avec les droits système macOS.

### Solution
Nous avons résolu ce problème en configurant des **volumes Docker nommés** (ex: `postgres-db-data`) à la fin du fichier `docker-compose.yaml`. Laissez Docker gérer les permissions internes.
Si vous devez remettre à zéro tout l'environnement sans conflits :
```bash
docker compose down -v
```
*(Attention: `-v` supprime toutes vos données de test et recrée des volumes propres).*
