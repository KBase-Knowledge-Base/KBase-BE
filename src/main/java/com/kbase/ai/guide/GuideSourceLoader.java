package com.kbase.ai.guide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Reads only packaged classpath resources; never the repository or a network. */
@Component
public final class GuideSourceLoader {
    private final GuideSourceCatalog catalog;
    private final ResourceLoader resources;

    public GuideSourceLoader(GuideSourceCatalog catalog, ResourceLoader resources) {
        this.catalog = catalog;
        this.resources = resources;
    }

    public List<LoadedGuideSource> loadAll() {
        return catalog.sources().stream().map(this::load).toList();
    }

    public LoadedGuideSource load(GuideSourceDefinition source) {
        try (var input = resources.getResource(source.resourcePath()).getInputStream()) {
            byte[] bytes = input.readAllBytes();
            return new LoadedGuideSource(source, bytes, new String(bytes, StandardCharsets.UTF_8),
                    HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (IOException exception) {
            throw new IllegalStateException("Packaged Guide source is unavailable", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
