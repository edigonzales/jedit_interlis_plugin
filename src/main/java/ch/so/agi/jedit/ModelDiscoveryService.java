package ch.so.agi.jedit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.gjt.sp.jedit.GUIUtilities;
import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ilirepository.impl.ModelLister;
import ch.interlis.ilirepository.impl.ModelMetadata;
import ch.interlis.ilirepository.impl.RepositoryAccess;
import ch.interlis.ilirepository.impl.RepositoryAccessException;
import ch.interlis.ilirepository.impl.RepositoryVisitor;
    
/**
 * Wird für das Vorschlagen/Vervollständigen der Modellnamen im SideKickParser benötigt.
 */
public class ModelDiscoveryService {
    private static final String P_REPOS = "interlis.repos";

    // Thread-safe cache for model metadata
    private static final Map<String, ModelMetadata> MODEL_CACHE = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) {
            return;
        }
        
        String repositoryUrls = jEdit.getProperty(P_REPOS, Ili2cSettings.DEFAULT_ILIDIRS);

        try {
            for (String repositoryUrl : repositoryUrls.split(";")) {
                if (repositoryUrl.equalsIgnoreCase("%ILI_DIR") || repositoryUrl.equalsIgnoreCase("%JAR_DIR")) {
                    continue;
                }
                Log.log(Log.DEBUG, ModelDiscoveryService.class, "repositoryUrls: " + repositoryUrls);
                discoverModelsFromRepository(repositoryUrl);
            }
            initialized = true;
        } catch (Exception e) {
            throw new RuntimeException("Model discovery initialization failed", e);
        }
    }
    
    private static void discoverModelsFromRepository(String repositoryUrl) {   
        RepositoryAccess repoAccess = new RepositoryAccess();
        
        ModelLister modelLister=new ModelLister();
        modelLister.setIgnoreDuplicates(true);
        
        try {
            RepositoryVisitor visitor=new RepositoryVisitor(repoAccess, modelLister);
            visitor.setRepositories(new String[] { repositoryUrl });
            visitor.visitRepositories();
            
            List<ModelMetadata> mergedModelMetadatav = modelLister.getResult2();
            Log.log(Log.DEBUG, ModelDiscoveryService.class, "mergedModelMetadatav.size(): " + mergedModelMetadatav.size());
            
            List<ModelMetadata> latestMergedModelMetadatav = RepositoryAccess.getLatestVersions2(mergedModelMetadatav);
            Log.log(Log.DEBUG, ModelDiscoveryService.class, "latestMergedModelMetadatav.size(): " + latestMergedModelMetadatav.size());
            
            for (ModelMetadata mmd : latestMergedModelMetadatav) {
                MODEL_CACHE.put(mmd.getName(), mmd);
            }
            
        } catch (RepositoryAccessException e) {
            e.printStackTrace();
            Log.log(Log.ERROR, ModelDiscoveryService.class, "Error while fetching repositories: " + e.getMessage());
            GUIUtilities.error(jEdit.getActiveView(), "error-while-fetching-repositories", new String[] { e.getMessage() });

        }
    }
        
    public static List<String> searchModelsByName(String searchTerm) {
        if (!initialized) {
            throw new IllegalStateException("Service not initialized. Call initialize() first.");
        }
        
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return new ArrayList<>(MODEL_CACHE.keySet());
        }
        
        String normalizedSearchTerm = searchTerm.trim().toLowerCase();
        boolean hasWildcard = normalizedSearchTerm.endsWith("*");
        String prefix = hasWildcard ? normalizedSearchTerm.substring(0, normalizedSearchTerm.length() - 1) : normalizedSearchTerm;
        
        List<String> results = new ArrayList<>();
        
        for (Map.Entry<String, ModelMetadata> entry : MODEL_CACHE.entrySet()) {
            String modelName = entry.getKey().toLowerCase();
            
            boolean matches = hasWildcard ? 
                modelName.startsWith(prefix) : 
                modelName.equals(prefix);
                
            if (matches) {
                results.add(entry.getKey());
            }
        }
        
        return results;
    }

    public static ModelMetadata getModelByName(String modelName) {
        if (!initialized) {
            throw new IllegalStateException("Service not initialized. Call initialize() first.");
        }
        
        return modelName != null ? MODEL_CACHE.get(modelName) : null;
    }

    public Collection<ModelMetadata> getAllModels() {
        if (!initialized) {
            throw new IllegalStateException("Service not initialized. Call initialize() first.");
        }
        
        return Collections.unmodifiableCollection(MODEL_CACHE.values());
    }

    public int getCacheSize() {
        return MODEL_CACHE.size();
    }

    public boolean isInitialized() {
        return initialized;
    }
}
