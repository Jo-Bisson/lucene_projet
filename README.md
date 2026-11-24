# Indexation et recherche Apache Lucene — Corpus Project Gutenberg

Ce projet Java met en œuvre une chaîne complète d'indexation et de recherche plein texte sur le corpus **Project Gutenberg** à l'aide de la bibliothèque **Apache Lucene**. Il permet de construire un index inversé sur disque (`AjoutGutenberg`) puis d'évaluer les performances d'interrogation sur 1 000 requêtes (`RechercheMots`).

> [!NOTE]
> Projet réalisé dans le cadre du cours **INF 6460 — Recherche et filtrage d'information** (Université TÉLUQ).

---

## Architecture du projet

Le projet repose sur deux programmes complémentaires :

1. **`AjoutGutenberg.java`** : Parcourt récursivement les fichiers `.txt` du corpus, applique une analyse linguistique anglaise, génère l'index inversé sur disque et produit un bilan de compression et de débit.

2. **`RechercheMots.java`** : Interroge l'index généré sur plusieurs champs simultanément (`titre`, `auteur`, `contenu`) et exécute un banc d'essai (*benchmark*) mesurant le temps moyen de recherche à la microseconde près.

---

## Prérequis techniques

* **Java** : JDK 21 ou supérieur (développé et testé avec Eclipse Adoptium JDK 25).
* **Bibliothèques Apache Lucene** :
  * `lucene-core`
  * `lucene-analysis-common` (`EnglishAnalyzer`, `FrenchAnalyzer`, `StandardAnalyzer`)
  * `lucene-queryparser` (`MultiFieldQueryParser`)
* **Corpus de données** : Répertoire local contenant les fichiers `.txt` de Project Gutenberg.

> [!IMPORTANT]
> Les deux classes désactivent explicitement l'API vectorielle expérimentale (`org.apache.lucene.store.MMapDirectory.enableVectorIncubator = false`) afin de garantir une exécution fluide sans nécessiter l'ajout manuel de `--add-modules jdk.incubator.vector`.

---

## Structure de l'index Lucene

Chaque fichier texte traité par `AjoutGutenberg` est enregistré dans l'index sous forme d'un `Document` comportant quatre champs :

| Nom du champ | Type Lucene | Stocké (`Field.Store`) | Rôle |
| :--- | :--- | :--- | :--- |
| `id` | `StringField` | `YES` | Identifiant unique séquentiel (`Gutenberg_1`, `Gutenberg_2`, etc.) |
| `titre` | `StringField` | `YES` | Nom exact du fichier source (ex. `00001.txt`) |
| `auteur` | `StringField` | `YES` | Valeur fixe attribuée par défaut (`Project Gutenberg`) |
| `contenu` | `TextField` | `NO` (flux) | Texte intégral du fichier (lu en `ISO-8859-1`), indexé et racinisé |

---

## Compilation

Depuis la racine du projet (avec les archives `.jar` d'Apache Lucene placées dans le dossier `lib/` ou configurées dans VS Code) :

```bash
javac -cp "lib/*" AjoutGutenberg.java RechercheMots.java

1. Étape d'indexation (AjoutGutenberg)
Fonctionnement

    Explore l'arborescence du dossier source, filtre les fichiers .txt et les trie par ordre alphabétique.

    Indexe les documents en mode OpenMode.CREATE (remplace l'index précédent).

    Calcule la taille des sources, la taille finale de l'index, le ratio de compression, la durée totale et la vitesse moyenne d'indexation (Mo/s).

Exemples
Bash

# Mode interactif (demande le nombre de documents dans la console)
java -cp ".;lib/*" AjoutGutenberg

# Mode automatisé (indexation de 500 documents dans ./index_en/)
java -cp ".;lib/*" AjoutGutenberg "D:\Corpus\Gutenberg" "./index_en/" 500

Exemple de sortie
Choisissez une langue pour l'analyseur (Appuyez sur Entrée pour 'en') :
  - en : Anglais (EnglishAnalyzer) [Par défaut]
  - fr : Français (FrenchAnalyzer)
  - default : Standard (StandardAnalyzer)
Entrez votre choix (en/fr/default) [en] : 
Recherche configurée pour l'index / la langue : en

--- Configuration du test de performance ---
Entrez 10 mots-clés séparés par des virgules ou appuyez sur [Entrée] pour la liste par défaut :
-> Par défaut : school, teacher, computer, spiritual, activity, project, circuit, meditation, data, house
> 
Utilisation de la liste de mots-clés par défaut.
nov. 23, 2026 8:15:49 P.M. org.apache.lucene.internal.vectorization.VectorizationProvider lookup
AVERTISSEMENT: You are running with unsupported Java 25. To make full use of the Vector API, please update Apache Lucene.

Exécution de 100 séries de 10 recherches (1000 recherches au total)...

================ RÉSULTATS DU BENCHMARK ================
Nombre total de recherches exécutées : 1000
Temps cumulé des 1000 recherches    : 58,606 ms (0,059 s)
Temps moyen de recherche par mot     : 0,0586 ms (58,61 µs)
========================================================

Détail du nombre de documents trouvés par mot :
  - Mots-clé: "school"        -> 8 document(s) trouvé(s)
  - Mots-clé: "teacher"       -> 6 document(s) trouvé(s)
  - Mots-clé: "computer"      -> 10 document(s) trouvé(s)
  - Mots-clé: "spiritual"     -> 7 document(s) trouvé(s)
  - Mots-clé: "activity"      -> 8 document(s) trouvé(s)
  - Mots-clé: "project"       -> 10 document(s) trouvé(s)
  - Mots-clé: "circuit"       -> 6 document(s) trouvé(s)
  - Mots-clé: "meditation"    -> 6 document(s) trouvé(s)
  - Mots-clé: "data"          -> 10 document(s) trouvé(s)
  - Mots-clé: "house"         -> 9 document(s) trouvé(s)