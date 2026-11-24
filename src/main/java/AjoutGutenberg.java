import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

public class AjoutGutenberg {
    private static final String FIELD_ID = "id";
    private static final String FIELD_TITRE = "titre";
    private static final String FIELD_AUTEUR = "auteur";
    private static final String FIELD_CONTENU = "contenu";

    private static final String DEFAULT_ROOT_DIR = 
        "D:\\Études\\TELUQ 2025\\Sciences des données\\INF 6460 Recherche et filtrage\\Documents\\project-gutenberg-august-2003-cd-master";
    private static final String INDEX_DIR = "./index_en/";
    private static final int DEFAULT_MAX_DOCS = 20;

    public static void main(String[] args) {
        System.setProperty("org.apache.lucene.store.MMapDirectory.enableVectorIncubator", "false");

        String rootDirPath = (args.length > 0) ? args[0] : DEFAULT_ROOT_DIR;
        String indexPath = (args.length > 1) ? args[1] : INDEX_DIR;

        try (var consoleReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            // 1. Détermination du nombre de documents (via argument ou saisie console)
            int maxDocs = getMaxDocsFromUserOrArgs(args, consoleReader);

            System.out.println("\nDébut de l'indexation depuis : " + rootDirPath);
            System.out.println("Nombre de documents à indexer : " + maxDocs);

            createIndexFromGutenbergRoot(Paths.get(rootDirPath), Paths.get(indexPath), maxDocs);

        } catch (Exception e) {
            System.err.println("Erreur lors de l'indexation : " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Récupère le nombre de documents :
     * 1. Depuis args[2] si présent.
     * 2. Sinon en demandant interactivement à l'utilisateur.
     */
    private static int getMaxDocsFromUserOrArgs(String[] args, BufferedReader reader) throws Exception {
        // Priorité à l'argument en ligne de commande si fourni
        if (args.length > 2) {
            try {
                return Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.err.println("Argument invalide. Passage en mode interactif...");
            }
        }

        // Demande interactive à l'utilisateur
        System.out.println("--- Configuration de l'indexation Gutenberg ---");
        System.out.print("Combien de documents souhaitez-vous indexer ? [Par défaut : " + DEFAULT_MAX_DOCS + "] : ");
        
        String input = reader.readLine();
        if (input == null || input.trim().isEmpty()) {
            return DEFAULT_MAX_DOCS;
        }

        try {
            int val = Integer.parseInt(input.trim());
            return (val > 0) ? val : DEFAULT_MAX_DOCS;
        } catch (NumberFormatException e) {
            System.out.println("Saisie invalide. Utilisation de la valeur par défaut (" + DEFAULT_MAX_DOCS + ").");
            return DEFAULT_MAX_DOCS;
        }
    }

    private static void createIndexFromGutenbergRoot(Path rootPath, Path indexPath, int maxDocs) throws Exception {
        var analyzer = new EnglishAnalyzer();
        var config = new IndexWriterConfig(analyzer);
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);

        long startTime = System.currentTimeMillis();
        final int[] count = {0};
        final long[] totalSourceBytes = {0L};

        try (var writer = new IndexWriter(FSDirectory.open(indexPath), config);
             Stream<Path> paths = Files.walk(rootPath)) {

            paths.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".txt"))
                 .sorted((p1, p2) -> p1.getFileName().compareTo(p2.getFileName()))
                 .limit(maxDocs)
                 .forEach(filePath -> {
                     try {
                         long fileSize = Files.size(filePath);
                         totalSourceBytes[0] += fileSize;

                         var doc = new Document();
                         String fileName = filePath.getFileName().toString();
                         count[0]++;

                         doc.add(new StringField(FIELD_ID, "Gutenberg_" + count[0], Field.Store.YES));
                         doc.add(new StringField(FIELD_TITRE, fileName, Field.Store.YES));
                         doc.add(new StringField(FIELD_AUTEUR, "Project Gutenberg", Field.Store.YES));

                         try (BufferedReader reader = Files.newBufferedReader(filePath, StandardCharsets.ISO_8859_1)) {
                             doc.add(new TextField(FIELD_CONTENU, reader));
                             writer.addDocument(doc);
                         }

                         System.out.println("Indexé [" + count[0] + "/" + maxDocs + "] : " + fileName + " (" + fileSize + " octets)");
                     } catch (Exception e) {
                         System.err.println("Erreur sur le fichier " + filePath + " : " + e.getMessage());
                     }
                 });
        }

        long endTime = System.currentTimeMillis();
        double durationSeconds = (endTime - startTime) / 1000.0;
        long indexSizeBytes = getDirectorySize(indexPath);

        double sourceMB = totalSourceBytes[0] / (1024.0 * 1024.0);
        double indexMB = indexSizeBytes / (1024.0 * 1024.0);
        double speedMBps = (durationSeconds > 0) ? (sourceMB / durationSeconds) : 0.0;

        System.out.println("\n================ RÉSULTAT DE L'INDEXATION ================");
        System.out.println("Documents demandés : " + maxDocs);
        System.out.println("Documents indexés  : " + count[0]);
        System.out.printf("Taille des sources : %d octets (%.2f Mo)%n", totalSourceBytes[0], sourceMB);
        System.out.printf("Taille de l'index  : %d octets (%.2f Mo)%n", indexSizeBytes, indexMB);
        System.out.printf("Ratio de compression index/source : %.1f %%%n", (indexMB / sourceMB) * 100);
        System.out.printf("Temps d'exécution  : %.3f secondes%n", durationSeconds);
        System.out.printf("Vitesse moyenne     : %.2f Mo/sec%n", speedMBps);
        System.out.println("===========================================================");
    }

    private static long getDirectorySize(Path dirPath) {
        if (!Files.exists(dirPath)) return 0L;
        try (Stream<Path> stream = Files.walk(dirPath)) {
            return stream.filter(Files::isRegularFile)
                         .mapToLong(path -> {
                             try {
                                 return Files.size(path);
                             } catch (Exception e) {
                                 return 0L;
                             }
                         })
                         .sum();
        } catch (Exception e) {
            return 0L;
        }
    }
}