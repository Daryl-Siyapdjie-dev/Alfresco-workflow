package com.kimia.bcrg_integration.pipeline;

import java.time.OffsetDateTime;

/**
 * Clé d'idempotence d'une transmission (cf. §7 : {@code dateArrete} + {@code codeFichier} + période).
 * On y ajoute la {@code feuille} pour une granularité au niveau feuille (reprise ciblée). Deux envois
 * partageant cette clé désignent la même donnée → le second est ignoré s'il a déjà réussi.
 */
public record IdempotencyKey(String codeFichier, String feuille, OffsetDateTime dateArrete) {
}
