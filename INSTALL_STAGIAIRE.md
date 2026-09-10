# Guide d'Installation (Alfresco Workflow SIRYF)

Bienvenue sur le projet Alfresco Workflow SIRYF ! Voici les étapes simples pour lancer le projet sur votre machine locale et tester le workflow de bout en bout.

## 1. Prérequis
- **Docker** et **Docker Compose** installés sur votre machine.
- Au moins 6 à 8 Go de RAM alloués à Docker (Alfresco est gourmand en mémoire).
- Aucun service ne doit tourner sur les ports `8080`, `5432` ou `9090` de votre machine.

## 2. Lancement du Serveur Alfresco

Ouvrez un terminal dans ce dossier (où se trouve le fichier `docker-compose.yaml`) et lancez la commande suivante :

```bash
docker compose up -d --build alfresco
```

Le premier démarrage peut prendre entre 3 et 5 minutes le temps que la base de données s'initialise et qu'Alfresco déploie le modèle de données et le workflow SIRYF.

Pour vérifier que tout a bien démarré, vous pouvez regarder les logs :
```bash
docker compose logs -f alfresco
```
*(Attendez de voir un message du type `Server startup in [X] milliseconds`)*.

## 3. Accès aux Interfaces

Une fois démarré, Alfresco vous propose deux interfaces web :
1. **L'interface classique (Alfresco Share) :** http://localhost:8080/share/
2. **L'interface moderne (Alfresco Content App - ACA) :** http://localhost:9090/content-app/

> **Identifiants administrateur par défaut :**
> - **Utilisateur :** `admin`
> - **Mot de passe :** `admin`

## 4. Configuration des Utilisateurs de Test (Obligatoire)

Comme la base de données n'est pas poussée sur Git, votre environnement local est complètement vierge. Pour tester le workflow de validation SIRYF, vous devez impérativement recréer les utilisateurs et groupes de test.

1. Connectez-vous sur Alfresco Share avec le compte `admin` (http://localhost:8080/share/).
2. Allez dans **Outils d'administration** > **Groupes**.
3. Créez les 3 groupes suivants (en respectant bien les identifiants) :
   - Identifiant : `GROUP_CONTROLEURS` (Nom d'affichage : *Contrôleurs*)
   - Identifiant : `GROUP_VALIDATEURS` (Nom d'affichage : *Validateurs*)
   - Identifiant : `GROUP_TRANSMETTEURS` (Nom d'affichage : *Transmetteurs*)
4. Allez dans **Outils d'administration** > **Utilisateurs** et créez 3 utilisateurs :
   - **Yannick** (ajoutez-le au groupe `GROUP_CONTROLEURS`)
   - **Daryl** (ajoutez-le au groupe `GROUP_VALIDATEURS`)
   - **Joyce** (ajoutez-la au groupe `GROUP_TRANSMETTEURS`)

## 5. Tester le Workflow

Pour tester, vous pouvez vous connecter avec le compte **Yannick** (Contrôleur) :
1. Déposez un fichier de déclaration (ex: `1_SITU_00 - New - imf.xlsx`).
2. Lancez le workflow "Processus de validation et transmission SIRYF" sur ce fichier.
3. Allez dans vos Tâches, et cliquez sur **Approuver**.
4. Déconnectez-vous et connectez-vous avec **Daryl** (Validateur). La tâche suivante devrait l'attendre !
5. Enfin, connectez-vous avec **Joyce** (Transmetteuse) pour l'étape finale.

---
**Note sur le développement :**
Le code source du workflow BPMN se trouve dans :
`backend/repo-amp/src/main/resources/alfresco/module/ged-repo-amp/workflow/siriyf-workflow.bpmn20.xml`
Si vous le modifiez, relancez `docker compose up -d --build alfresco` pour appliquer les changements.
