import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Classe permettant de rechercher des documents dans un index Lucene
 * et d'évaluer le temps moyen de recherche sur 1000 itérations (100 x 10 mots).
 */
public class RechercheMots {
    private static final String FIELD_ID = "id";
    private static final String FIELD_TITRE = "titre";
    private static final String FIELD_AUTEUR = "auteur";
    private static final String FIELD_CONTENU = "contenu";
    private static final String[] SEARCH_FIELDS = {FIELD_TITRE, FIELD_AUTEUR, FIELD_CONTENU};

    public static void main(String[] args) {
        // Désactive l'avertissement Vector API Lucene si présent
        System.setProperty("org.apache.lucene.store.MMapDirectory.enableVectorIncubator", "false");

        try (var reader = new BufferedReader(
                new InputStreamReader(System.in, StandardCharsets.UTF_8))) {

            var language = getLanguage(args, reader);
            System.out.println("Recherche configurée pour l'index / la langue : " + language);

            var analyzer = getAnalyzer(language);
            var parser = new MultiFieldQueryParser(SEARCH_FIELDS, analyzer);

            // 1. Saisie des 10 mots-clés par l'utilisateur
            List<String> keywords = getKeywordsFromUser(reader);

            // 2. Lancement du benchmark (100 itérations * 10 mots = 1000 recherches)
            runBenchmark(parser, keywords, language);

        } catch (Exception e) {
            System.err.println("Erreur : " + e.getMessage());
            e.printStackTrace();
        }
    }
       
    /**
    * Sollicite l'utilisateur pour entrer 10 mots-clés de recherche.
    * Propose une liste par défaut de 10 mots en anglais si l'utilisateur appuie sur Entrée.
    */
    private static List<String> getKeywordsFromUser(BufferedReader reader) throws Exception {
        List<String> keywords = new ArrayList<>();
    
        // Liste par défaut des 10 mots-clés
        String[] defaultKeywords = {
            "school", "teacher", "computer", "spiritual", "activity", 
            "project", "circuit", "meditation", "data", "house"
    };

    System.out.println("\n--- Configuration du test de performance ---");
    System.out.println("Entrez 10 mots-clés séparés par des virgules ou appuyez sur [Entrée] pour la liste par défaut :");
    System.out.println("-> Par défaut : " + String.join(", ", defaultKeywords));
    System.out.print("> ");

    String input = reader.readLine();

    // 1. Cas où l'utilisateur appuie directement sur Entrée
    if (input == null || input.trim().isEmpty()) {
        System.out.println("Utilisation de la liste de mots-clés par défaut.");
        return Arrays.asList(defaultKeywords);
    }

    // 2. Cas où l'utilisateur saisit sa propre liste
    String[] tokens = input.split("[,\\s]+");
    for (String t : tokens) {
        if (!t.isBlank() && keywords.size() < 10) {
            keywords.add(t.trim());
        }
    }

    // 3. Si l'utilisateur n'a pas fourni 10 mots, on lui demande de compléter
    while (keywords.size() < 10) {
        System.out.println("Actuellement " + keywords.size() + "/10 mots enregistrés. Complétez la liste :");
        System.out.print("> ");
        String nextInput = reader.readLine();
        if (nextInput != null && !nextInput.trim().isEmpty()) {
            String[] nextTokens = nextInput.split("[,\\s]+");
            for (String t : nextTokens) {
                if (!t.isBlank() && keywords.size() < 10) {
                    keywords.add(t.trim());
                }
            }
        }
    }

    System.out.println("Les 10 mots-clés retenus pour le benchmark sont : " + keywords);
    return keywords;
}

    /**
     * Exécute 100 fois la recherche des 10 mots-clés (1000 recherches)
     * et calcule le temps de recherche moyen par mot.
     */
    private static void runBenchmark(MultiFieldQueryParser parser, List<String> keywords, String language) throws Exception {
        var indexPath = Paths.get("./index_" + language + "/");
        final int ITERATIONS = 100;
        final int TOTAL_SEARCHES = ITERATIONS * keywords.size(); // 100 * 10 = 1000

        try (var indexReader = DirectoryReader.open(FSDirectory.open(indexPath))) {
            var searcher = new IndexSearcher(indexReader);

            // Préchauffage (Warm-up) pour charger la structure en mémoire cache
            for (String kw : keywords) {
                searcher.search(parser.parse(kw), 10);
            }

            System.out.println("\nExécution de " + ITERATIONS + " séries de " + keywords.size() + " recherches (" + TOTAL_SEARCHES + " recherches au total)...");

            long totalNanoTime = 0;

            // Boucle principale : 100 itérations
            for (int i = 0; i < ITERATIONS; i++) {
                for (String word : keywords) {
                    Query query = parser.parse(word);

                    long start = System.nanoTime();
                    TopDocs results = searcher.search(query, 4096);
                    long end = System.nanoTime();

                    totalNanoTime += (end - start);
                }
            }

            // Calcul du temps total et moyen
            double totalMillis = totalNanoTime / 1_000_000.0;
            double avgMillisPerSearch = totalMillis / TOTAL_SEARCHES;
            double avgMicrosPerSearch = (totalNanoTime / 1000.0) / TOTAL_SEARCHES;

            // Affichage du détail d'une recherche type pour la vérification
            System.out.println("\n================ RÉSULTATS DU BENCHMARK ================");
            System.out.println("Nombre total de recherches exécutées : " + TOTAL_SEARCHES);
            System.out.printf("Temps cumulé des 1000 recherches    : %.3f ms (%.3f s)%n", totalMillis, totalMillis / 1000.0);
            System.out.printf("Temps moyen de recherche par mot     : %.4f ms (%.2f µs)%n", avgMillisPerSearch, avgMicrosPerSearch);
            System.out.println("========================================================");

            // Affichage du nombre de résultats pour chaque mot (1 passe de contrôle)
            System.out.println("\nDétail du nombre de documents trouvés par mot :");
            for (String word : keywords) {
                TopDocs hits = searcher.search(parser.parse(word), 4096);
                System.out.printf("  - Mots-clé: %-15s -> %d document(s) trouvé(s)%n", "\"" + word + "\"", hits.scoreDocs.length);
            }
        }
    }

    /**
    * Détermine la langue à utiliser en réutilisant le BufferedReader du main.
    * Propose l'anglais ("en") par défaut si l'utilisateur appuie sur Entrée.
    */
    private static String getLanguage(String[] args, BufferedReader reader) throws Exception {
    // 1. Vérification si la langue est passée en argument (--lang=fr ou --lang=en)
    for (var arg : args) {
        if (arg.startsWith("--lang=")) {
            var lang = arg.substring("--lang=".length()).toLowerCase();
            if (lang.equals("fr") || lang.equals("en")) {
                return lang;
            }
        }
    }

    // 2. Demande à l'utilisateur avec "en" par défaut
    System.out.println("Choisissez une langue pour l'analyseur (Appuyez sur Entrée pour 'en') :");
    System.out.println("  - en : Anglais (EnglishAnalyzer) [Par défaut]");
    System.out.println("  - fr : Français (FrenchAnalyzer)");
    System.out.println("  - default : Standard (StandardAnalyzer)");
    System.out.print("Entrez votre choix (en/fr/default) [en] : ");
    
    var lang = reader.readLine().trim().toLowerCase();

    // Si l'entrée est vide (appui direct sur Entrée), on applique "en"
    if (lang.isEmpty() || lang.equals("en")) {
        return "en";
    } else if (lang.equals("fr") || lang.equals("default")) {
        return lang;
    } else {
        throw new IllegalArgumentException("Choix de langue invalide : " + lang);
    }
    }

    private static Analyzer getAnalyzer(String language) {
        return switch (language) {
            case "fr" -> new FrenchAnalyzer();
            case "en" -> new EnglishAnalyzer();
            default -> new StandardAnalyzer();
        };
    }
}