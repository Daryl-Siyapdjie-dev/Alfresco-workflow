# backend/repo-amp

Module Maven Alfresco au format **"Simple Module"** : un JAR classique déposé dans
`/usr/local/tomcat/webapps/alfresco/WEB-INF/lib/` de l'image Alfresco (voir `Dockerfile.alfresco` à la racine),
sans passer par la mécanique AMP (`alfresco-mmt.jar`).

## Contenu actuel

* `src/main/resources/alfresco/module/ged-repo-amp/model/contractModel.xml` : modèle de contenu `ged:contrat`.
* `src/main/java/com/ged/alfresco/repo/behavior/ContratBehaviour.java` : behavior Java (renseigne `ged:dateSignature`
  automatiquement à la création d'un `ged:contrat`).
* `src/main/resources/alfresco/module/ged-repo-amp/workflow/` : deux workflows BPMN :
  * `gedApproval` (approbation d'un contrat)
  * `gedCreditWorkflow` ("GED - Gestion de Credit") : Soumission dossier -> Verification documents -> Analyse
    credit -> Validation/Decision responsable -> (si rejete : retour a Verification documents) -> Signature ->
    Archivage automatique (`ArchivageDelegate.java`, applique l'aspect `gedwf:archived`).

### Groupes/roles a creer pour le workflow "Gestion de Credit"

Chaque etape est assignee a un **groupe Alfresco** (candidateGroups), pas a un utilisateur nomme, pour rester
valable meme quand le personnel change. A creer via **Control Center > Groups > Create Group** (l'identifiant de
groupe doit correspondre exactement, Alfresco ajoute automatiquement le prefixe `GROUP_`) :

| Etape | Identifiant de groupe a creer |
| :--- | :--- |
| Verification documents | `GED_VERIFICATEURS` |
| Analyse credit | `GED_ANALYSTES_CREDIT` |
| Validation / Decision responsable | `GED_RESPONSABLES_CREDIT` |
| Signature | `GED_SIGNATAIRES` |

L'etape "Archivage" est automatique (pas de groupe, pas d'utilisateur).

Ensuite, ajoutez chaque utilisateur concerne comme **membre** du groupe correspondant a son role
(Control Center > Groups > [groupe] > Add member). Un meme utilisateur peut appartenir a plusieurs groupes.

Pour demarrer une instance du workflow : l'Alfresco Content App (moderne) n'a pas d'ecran "Demarrer un workflow"
integre en Community Edition. Utilisez **Alfresco Share** (http://localhost:8080/share, deja actif dans ce
docker-compose) : dans la bibliotheque de documents, selectionnez le(s) document(s) du dossier de credit ->
bouton **"Demarrer un flux"** -> choisir "GED - Gestion de Credit". Chaque utilisateur suit ensuite ses taches
assignees via le tableau de bord Share ("Mes taches") ou l'API REST workflow.
* `src/main/resources/alfresco/extension/templates/webscripts/com/ged/hello.*` : exemple de web script REST
  (`GET /alfresco/service/ged/hello?name=...`).
* `src/main/resources/alfresco/module/ged-repo-amp/module-context.xml` : câblage Spring (bootstrap du
  modèle, du behavior, déploiement du workflow). Le nom et l'emplacement exact de ce fichier sont imposés par
  Alfresco (`classpath*:alfresco/module/*/module-context.xml`, voir `alfresco/module-context.xml` dans
  `alfresco-repository-*.jar`) : un autre nom/dossier ne serait tout simplement pas charge.

## Compiler en local (sans Docker)

```bash
cd backend/repo-amp
mvn -DskipTests package
```
Le JAR est généré dans `target/ged-repo-amp.jar`. `docker compose build alfresco` fait exactement la même chose
dans un conteneur Maven, donc l'installation locale de Java/Maven n'est nécessaire que si vous voulez
compiler/tester en dehors de Docker (IDE, lint, etc.).

## Ajouter vos propres personnalisations

* **Nouveau type/aspect** : ajoutez-le dans `contractModel.xml` (ou un nouveau fichier `*.xml` sous `model/`,
  référencé dans `module-context.xml`).
* **Nouveau behavior** : nouvelle classe Java sous `behavior/`, bean Spring déclaré dans `module-context.xml`.
* **Nouveau web script** : fichiers `*.desc.xml` / `*.js` / `*.ftl` sous
  `src/main/resources/alfresco/extension/templates/webscripts/...`.
* **Nouveau workflow** : fichier `*.bpmn20.xml` sous `workflow/`, déclaré via un bean `parent="workflowDeployer"`
  dans `module-context.xml`.

Après toute modification : `docker compose up -d --build alfresco` puis vérifiez
`docker compose logs -f alfresco` (déploiement du module au démarrage de Tomcat).
