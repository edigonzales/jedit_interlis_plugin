package ch.so.agi.jedit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.gjt.sp.jedit.jEdit;
import org.gjt.sp.util.Log;

import ch.interlis.ili2c.Ili2cSettings;
import ch.interlis.ilirepository.impl.ModelLister;
import ch.interlis.ilirepository.impl.ModelMetadata;
import ch.interlis.ilirepository.impl.RepositoryAccess;
import ch.interlis.ilirepository.impl.RepositoryAccessException;
import ch.interlis.ilirepository.impl.RepositoryVisitor;
    
public class ModelDiscoveryService {
    private static final String P_REPOS = "interlis.repos";

    // Thread-safe cache for model metadata
    private static final Map<String, ModelMetadata> modelCache = new ConcurrentHashMap<>();
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
                System.err.println("**** repositoryUrl: " + repositoryUrl);
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
            System.err.println("**************************** mergedModelMetadatav: " + mergedModelMetadatav.size());
                      
            List<ModelMetadata> latestMergedModelMetadatav = RepositoryAccess.getLatestVersions2(mergedModelMetadatav);
            System.err.println("**************************** latestMergedModelMetadatav: " + latestMergedModelMetadatav.size());
        } catch (RepositoryAccessException e) {
            e.printStackTrace();
            Log.log(Log.ERROR, ModelDiscoveryService.class, "Error while fetching repositories: " + e.getMessage());
        }

//        try {
//            // Dummy implementation - replace with actual repository access logic
//            List<ModelMetadata> models = fetchModelMetadataFromRepository(repositoryUrl);
//            
//            for (ModelMetadata model : models) {
//                modelCache.put(model.getName(), model);
//            }
//            
//        } catch (Exception e) {
////            logger.log(Level.WARNING, 
////                String.format("Failed to discover models from repository: %s", repositoryUrl), e);
//        }
    }
    
   private List<ModelMetadata> fetchModelMetadataFromRepository(String repositoryUrl) {
       // Simulate network delay
       try {
           Thread.sleep(100);
       } catch (InterruptedException e) {
           Thread.currentThread().interrupt();
       }
       
       // Dummy data - replace with actual repository parsing logic
       List<ModelMetadata> dummyModels = new ArrayList<>();
       String repoName = repositoryUrl.replaceAll("https?://", "").replaceAll("[^a-zA-Z0-9]", "_");
       
//       dummyModels.add(new ModelMetadata(repoName + "_Model1", "1.0", "First model from " + repoName));
//       dummyModels.add(new ModelMetadata(repoName + "_Model2", "2.1", "Second model from " + repoName));
//       dummyModels.add(new ModelMetadata(repoName + "_CommonModel", "1.5", "Common model from " + repoName));
       
       return dummyModels;
   }
    
    public List<ModelMetadata> searchModelsByName(String searchTerm) {
        if (!initialized) {
            throw new IllegalStateException("Service not initialized. Call initialize() first.");
        }
        
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return new ArrayList<>(modelCache.values());
        }
        
        String normalizedSearchTerm = searchTerm.trim().toLowerCase();
        boolean hasWildcard = normalizedSearchTerm.endsWith("*");
        String prefix = hasWildcard ? normalizedSearchTerm.substring(0, normalizedSearchTerm.length() - 1) : normalizedSearchTerm;
        
        List<ModelMetadata> results = new ArrayList<>();
        
        for (Map.Entry<String, ModelMetadata> entry : modelCache.entrySet()) {
            String modelName = entry.getKey().toLowerCase();
            
            boolean matches = hasWildcard ? 
                modelName.startsWith(prefix) : 
                modelName.equals(prefix);
                
            if (matches) {
                results.add(entry.getValue());
            }
        }
        
        return results;
    }

    public ModelMetadata getModelByName(String modelName) {
        if (!initialized) {
            throw new IllegalStateException("Service not initialized. Call initialize() first.");
        }
        
        return modelName != null ? modelCache.get(modelName) : null;
    }

    public Collection<ModelMetadata> getAllModels() {
        if (!initialized) {
            throw new IllegalStateException("Service not initialized. Call initialize() first.");
        }
        
        return Collections.unmodifiableCollection(modelCache.values());
    }

    public int getCacheSize() {
        return modelCache.size();
    }

    public boolean isInitialized() {
        return initialized;
    }


}
