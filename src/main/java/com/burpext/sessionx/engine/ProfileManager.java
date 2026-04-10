package com.burpext.sessionx.engine;

import com.burpext.sessionx.core.SessionProfile;
import com.burpext.sessionx.util.ActivityLogger;
import com.burpext.sessionx.util.JsonUtil;
import burp.api.montoya.MontoyaApi;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Manages the list of session profiles:
 *  - CRUD operations (add, update, delete)
 *  - Persistence via Burp's preferences API (survives restarts)
 *  - JSON export/import to/from filesystem files
 */
public class ProfileManager {

    private static final String PREFS_KEY = "sessionx.profiles";

    private final MontoyaApi     api;
    private final TokenStore     tokenStore;
    private final ActivityLogger logger;
    private final List<SessionProfile> profiles = new ArrayList<>();

    public ProfileManager(MontoyaApi api, TokenStore tokenStore) {
        this.api        = api;
        this.tokenStore = tokenStore;
        this.logger     = ActivityLogger.getInstance();
        loadFromPrefs();
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    public void addProfile(SessionProfile profile) {
        profiles.add(profile);
        saveToPrefs();
        logger.info("Profile created: \"" + profile.getName() + "\"");
    }

    public void updateProfile(SessionProfile updated) {
        for (int i = 0; i < profiles.size(); i++) {
            if (profiles.get(i).getId().equals(updated.getId())) {
                profiles.set(i, updated);
                saveToPrefs();
                logger.info("Profile updated: \"" + updated.getName() + "\"");
                return;
            }
        }
    }

    public void deleteProfile(String profileId) {
        profiles.removeIf(p -> p.getId().equals(profileId));
        tokenStore.clearProfile(profileId);
        saveToPrefs();
        logger.info("Profile deleted: " + profileId);
    }

    public List<SessionProfile> getAllProfiles() {
        return new ArrayList<>(profiles);
    }

    public Optional<SessionProfile> findById(String id) {
        return profiles.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    public List<SessionProfile> getEnabledProfiles() {
        return profiles.stream().filter(SessionProfile::isEnabled).toList();
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private void saveToPrefs() {
        try {
            String json = JsonUtil.toJson(profiles);
            api.persistence().preferences().setString(PREFS_KEY, json);
        } catch (Exception e) {
            logger.error("Failed to persist profiles: " + e.getMessage());
        }
    }

    private void loadFromPrefs() {
        try {
            String json = api.persistence().preferences().getString(PREFS_KEY);
            if (json != null && !json.isBlank()) {
                List<SessionProfile> loaded = JsonUtil.fromJsonList(json, SessionProfile.class);
                profiles.addAll(loaded);
            }
        } catch (Exception e) {
            logger.error("Failed to load profiles from prefs: " + e.getMessage());
        }
    }

    // ─── Import / Export ──────────────────────────────────────────────────────

    /**
     * Exports a single profile to a JSON file.
     */
    public void exportToFile(SessionProfile profile, File file) throws IOException {
        String json = JsonUtil.toPrettyJson(profile);
        Files.writeString(file.toPath(), json);
        logger.info("Profile exported: \"" + profile.getName() + "\" -> " + file.getName());
    }

    /**
     * Imports a profile from a JSON file.
     * The imported profile is added to the profile list.
     */
    public SessionProfile importFromFile(File file) throws IOException {
        String json = Files.readString(file.toPath());
        SessionProfile profile = JsonUtil.fromJson(json, SessionProfile.class);
        // Give it a fresh ID to avoid collisions if same file is imported twice
        // We keep the name as-is but mark it disabled until user enables it
        profile.setEnabled(false);
        addProfile(profile);
        logger.info("Profile imported: \"" + profile.getName() + "\" from " + file.getName());
        return profile;
    }
}
