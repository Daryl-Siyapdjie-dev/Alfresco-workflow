# Certificats CA à importer dans le conteneur `integration-api`

Déposer ici des fichiers `*.crt` / `*.pem` / `*.cer` **uniquement si** le TLS vers l'API SIRYF
(`https://syrif.bcrg-guinee.org:8186`) échoue depuis le conteneur avec une erreur
`PKIX path building failed` (visible dans les logs du service `integration-api`).

Deux cas :
- **Un antivirus/proxy inspecte le HTTPS** (sur ce poste : « Avast Web/Mail Shield Root ») : exporter
  sa racine et la déposer ici.
- **La BCRG signe avec une autorité privée** : déposer sa chaîne ici.

Le `Dockerfile.integration-api` importe automatiquement tout certificat présent dans ce dossier
au moment du build. Dossier vide = aucun import (comportement par défaut, truststore JVM standard).
