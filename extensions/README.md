# Personnalisation et Modules Alfresco

> **Mise en oeuvre reelle** : ce guide etait jusqu'ici purement theorique (aucun projet genere). Le module
> `backend/repo-amp` a la racine du depot est desormais une implementation concrete et fonctionnelle de ces
> principes (modele de contenu, behavior Java, web script, workflow BPMN), buildee automatiquement par
> `docker compose build alfresco` via `Dockerfile.alfresco`. Voir [backend/repo-amp/README.md](../backend/repo-amp/README.md)
> pour son fonctionnement precis. Le contenu ci-dessous reste une reference generale sur les mecanismes Alfresco.

Ce guide explique comment créer, packager et installer vos propres modules et configurations personnalisés sur Alfresco.

---

## 1. Structure d'un Module Personnalisé (Alfresco SDK)

Il est fortement recommandé d'utiliser le **SDK Alfresco (basé sur Apache Maven)** pour développer des modules. Le SDK génère une structure de projet standardisée et compile le code sous forme de fichier JAR ou AMP.

Il existe deux types de projets de développement principaux :
1. **Platform (ou Repo) Jar/Amp** : Pour étendre le moteur Alfresco (types de documents, règles, scripts Web, behaviors Java).
2. **Share Jar/Amp** : Pour personnaliser l'interface Alfresco Share (nouvelles pages, nouveaux composants).

### Lancer un projet avec le SDK Alfresco (en ligne de commande)
```bash
# Générer un projet d'extension Repo (Plateforme)
mvn archetype:generate \
    -DarchetypeGroupId=org.alfresco.maven.archetype \
    -DarchetypeArtifactId=alfresco-platform-jar-archetype \
    -DarchetypeVersion=4.8.0
```

---

## 2. Définir un Modèle de Contenu Personnalisé (Content Model)

Les modèles de contenu définissent comment Alfresco structure et qualifie les métadonnées de vos fichiers. Vous pouvez définir des **Types** (ex: Contrat, Facture) et des **Aspects** (fonctionnalités transverses, ex: "Est-ce Facturé ?").

### Exemple de fichier XML de modèle (`customModel.xml`) :
```xml
<?xml version="1.0" encoding="UTF-8"?>
<model name="custom:mycompanyModel" xmlns="http://www.alfresco.org/model/dictionary/1.0">
    <description>Modèle de métadonnées pour MaSociété</description>
    <author>Développeur</author>
    <version>1.0</version>
    
    <!-- Déclaration des espaces de noms (Namespaces) -->
    <imports>
        <import uri="http://www.alfresco.org/model/dictionary/1.0" prefix="d" />
        <import uri="http://www.alfresco.org/model/content/1.0" prefix="cm" />
    </imports>
    
    <namespaces>
        <namespace uri="http://www.mycompany.com/model/content/1.0" prefix="mc" />
    </namespaces>
    
    <!-- Définition des Types de Documents -->
    <types>
        <type name="mc:contrat">
            <title>Document de Contrat</title>
            <parent>cm:content</parent>
            <properties>
                <property name="mc:clientName">
                    <title>Nom du Client</title>
                    <type>d:text</type>
                    <mandatory>true</mandatory>
                </property>
                <property name="mc:montant">
                    <title>Montant du Contrat</title>
                    <type>d:float</type>
                </property>
                <property name="mc:dateSignature">
                    <title>Date de Signature</title>
                    <type>d:date</type>
                </property>
            </properties>
        </type>
    </types>
</model>
```

Pour activer ce modèle, il doit être déclaré dans le fichier de contexte Spring d'Alfresco (ex: `service-context.xml`) :
```xml
<bean id="mycompany.dictionaryBootstrap" 
      parent="dictionaryModelBootstrap" 
      depends-on="dictionaryBootstrap">
    <property name="models">
        <list>
            <value>alfresco/module/mon-module/model/customModel.xml</value>
        </list>
    </property>
</bean>
```

---

## 3. Développer des Web Scripts (APIs REST Personnalisées)

Un Web Script est une API HTTP construite avec une description XML, un contrôleur (JavaScript côté serveur ou classe Java), et un template de rendu (HTML ou JSON via FreeMarker).

Pour créer un Web Script GET `http://localhost:8080/alfresco/service/mycompany/hello` :

### A. Fichier de Description (`hello.get.desc.xml`)
```xml
<webscript>
    <shortname>Hello World Webscript</shortname>
    <description>Retourne un message de bienvenue personnalisé</description>
    <url>/mycompany/hello?name={name}</url>
    <authentication>user</authentication>
</webscript>
```

### B. Contrôleur JavaScript (`hello.get.js`)
```javascript
var name = args.name;
if (name == null || name == "") {
    name = "Visiteur";
}
model.greeting = "Bonjour " + name + ", bienvenue sur Alfresco !";
```

### C. Fichier Template JSON (`hello.get.json.ftl`)
```json
{
    "message": "${greeting}"
}
```

Placer ces fichiers dans le répertoire Tomcat `/usr/local/tomcat/shared/classes/alfresco/extension/templates/webscripts/` (ou empaquetés dans un JAR sous `src/main/resources/alfresco/extension/templates/webscripts/`).

---

## 4. Comment Installer un Module dans Docker

### Méthode A : Montage Direct de JAR (Pour le développement rapide)
Si vous compilez une extension Java sous forme de JAR :
1. Créez un dossier `extensions/shared-lib/` dans votre workspace.
2. Déposez-y vos fichiers `.jar`.
3. Ajoutez le montage de volume suivant dans le service `alfresco` de votre `docker-compose.yaml` :
   ```yaml
   volumes:
     - ./extensions/shared-lib:/usr/local/tomcat/shared/lib
   ```
4. Redémarrez le conteneur `alfresco`.

### Méthode B : Image Docker Personnalisée (Recommandé pour la Production)
Pour installer des fichiers `.amp` ou `.jar` de façon pérenne, construisez votre propre image Docker.

#### Exemple de `Dockerfile` :
```dockerfile
FROM docker.io/alfresco/alfresco-content-repository-community:26.1.0

# Copie des outils d'installation AMP
COPY ./my-extensions/*.amp /tmp/

# Installation des fichiers AMP dans le WAR d'Alfresco
RUN java -jar /usr/local/tomcat/bin/alfresco-mmt.jar install \
    /tmp/my-repo-extension.amp /usr/local/tomcat/webapps/alfresco.war -verbose && \
    java -jar /usr/local/tomcat/bin/alfresco-mmt.jar list /usr/local/tomcat/webapps/alfresco.war

# Nettoyage
RUN rm -rf /tmp/*.amp
```

Ensuite, dans le fichier `docker-compose.yaml`, remplacez `image: docker.io/alfresco/...` par :
```yaml
  alfresco:
    build:
      context: .
      dockerfile: Dockerfile
```
Puis lancez le build et démarrez : `docker compose up -d --build`.
