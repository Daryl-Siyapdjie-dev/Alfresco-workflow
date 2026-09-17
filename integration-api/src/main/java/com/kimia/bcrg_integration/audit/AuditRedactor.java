package com.kimia.bcrg_integration.audit;

import java.util.regex.Pattern;

/**
 * Masquage des données sensibles avant journalisation (ticket 7.4, §8) : ni jeton, ni mot de passe
 * ne doit apparaître dans la piste d'audit ni dans les accusés archivés.
 * <p>Quatre familles, appliquées <strong>dans cet ordre</strong> — du plus spécifique au plus
 * général, pour qu'une règle large ne vienne pas découper ce qu'une règle précise a déjà masqué :
 * <ol>
 *   <li>l'en-tête {@code Authorization}, valeur entière (elle contient le schéma <em>et</em> le jeton) ;</li>
 *   <li>un {@code Bearer …} isolé, hors en-tête ;</li>
 *   <li>toute paire {@code clé: valeur} dont la clé évoque un secret (JSON, query string, log) ;</li>
 *   <li>un JWT isolé, reconnaissable à son en-tête {@code eyJ…} en trois segments.</li>
 * </ol>
 * Le masquage est volontairement <strong>large</strong> : masquer à tort une valeur anodine est sans
 * conséquence, l'inverse ne l'est pas.
 */
public final class AuditRedactor {

    /** Ce qui remplace une valeur masquée. */
    public static final String MASQUE = "***";

    private static final Pattern AUTHORIZATION = Pattern.compile(
            "(?i)(\"?authorization\"?\\s*[:=]\\s*)(\"[^\"]*\"|[^\\r\\n,;}]+)");

    private static final Pattern BEARER =
            Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9\\-._~+/]+=*");

    private static final Pattern CLE_SENSIBLE = Pattern.compile(
            "(?i)(\"?(?:access_?token|refresh_?token|password|passwd|motdepasse|mot_de_passe"
                    + "|token|secret|api_?key)\"?\\s*[:=]\\s*)(\"[^\"]*\"|[^\\s,;}&]+)");

    private static final Pattern JWT =
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*");

    private AuditRedactor() {
    }

    /** Renvoie le texte débarrassé de ses secrets ; {@code null} et vide sont rendus tels quels. */
    public static String mask(String texte) {
        if (texte == null || texte.isBlank()) {
            return texte;
        }
        String masque = AUTHORIZATION.matcher(texte).replaceAll("$1\"" + MASQUE + "\"");
        masque = BEARER.matcher(masque).replaceAll("Bearer " + MASQUE);
        masque = CLE_SENSIBLE.matcher(masque).replaceAll("$1\"" + MASQUE + "\"");
        return JWT.matcher(masque).replaceAll(MASQUE);
    }
}
