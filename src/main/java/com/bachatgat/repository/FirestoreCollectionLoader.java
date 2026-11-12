package com.bachatgat.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/** Loads typed Firestore collections once and populates the service cache. */
public final class FirestoreCollectionLoader {

    private static final Logger log = LoggerFactory.getLogger(FirestoreCollectionLoader.class);

    private final Firestore firestore;
    private final ObjectMapper objectMapper;
    private final Set<String> loadedCollections;
    private final Object collectionLoadLock;

    public FirestoreCollectionLoader(Firestore firestore,
                                     ObjectMapper objectMapper,
                                     Set<String> loadedCollections,
                                     Object collectionLoadLock) {
        this.firestore = firestore;
        this.objectMapper = objectMapper;
        this.loadedCollections = loadedCollections;
        this.collectionLoadLock = collectionLoadLock;
    }

    public <T> void loadCollection(String collectionName,
                                   Class<T> type,
                                   Map<String, T> target,
                                   BiConsumer<T, String> idSetter) {
        try {
            for (DocumentSnapshot snapshot : firestore.collection(collectionName).get().get().getDocuments()) {
                if (!snapshot.exists() || snapshot.getData() == null) continue;
                T value = objectMapper.convertValue(snapshot.getData(), type);
                idSetter.accept(value, snapshot.getId());
                target.put(snapshot.getId(), value);
            }
            log.info("Loaded {} persisted {} record(s).", target.size(), collectionName);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load Firestore collection '" + collectionName + "'.", ex);
        }
    }

    public <T> void loadCollectionOnce(String collectionName,
                                       Class<T> type,
                                       Map<String, T> target,
                                       BiConsumer<T, String> idSetter) {
        if (firestore == null || loadedCollections.contains(collectionName)) return;
        synchronized (collectionLoadLock) {
            if (loadedCollections.contains(collectionName)) return;
            loadCollection(collectionName, type, target, idSetter);
            loadedCollections.add(collectionName);
        }
    }
}
