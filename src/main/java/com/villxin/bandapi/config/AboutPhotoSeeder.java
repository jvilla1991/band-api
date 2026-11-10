package com.villxin.bandapi.config;

import com.villxin.bandapi.site.entity.AboutPhoto;
import com.villxin.bandapi.site.repository.AboutPhotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Seeds the three current press photos into the About carousel on first startup,
 * only when the table is empty. The JPEGs are bundled under
 * {@code src/main/resources/seed/about/}; storing them here (rather than as hex
 * blobs in the Flyway migration) keeps ~700KB of binary out of the migration and
 * its checksum. alt text / focus mirror the frontend's former static fallback.
 */
@Component
public class AboutPhotoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AboutPhotoSeeder.class);

    /** The three starter photos, in carousel order. */
    private record Seed(String resource, String alt, String focus) {}

    private static final List<Seed> SEEDS = List.of(
            new Seed("seed/about/villxin-7.jpg", "villxin seated under a graffitied bridge", null),
            new Seed("seed/about/villxin-3.jpg", "villxin on a bridge over the river", null),
            new Seed("seed/about/villxin-16.jpg", "villxin in front of a stone tower", "center 62%")
    );

    private final AboutPhotoRepository repository;

    public AboutPhotoSeeder(AboutPhotoRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            log.info("About photos already present — skipping seed");
            return;
        }

        int order = 0;
        for (Seed seed : SEEDS) {
            Resource resource = new ClassPathResource(seed.resource());
            if (!resource.exists()) {
                log.warn("About photo seed resource missing: {} — skipping", seed.resource());
                continue;
            }
            try {
                AboutPhoto photo = new AboutPhoto();
                photo.setAlt(seed.alt());
                photo.setFocus(seed.focus());
                photo.setSortOrder(order++);
                photo.setContentType("image/jpeg");
                photo.setImageData(resource.getContentAsByteArray());
                repository.save(photo);
            } catch (IOException e) {
                log.error("Failed to read About photo seed {}: {}", seed.resource(), e.getMessage());
            }
        }
        log.info("Seeded {} About carousel photos", repository.count());
    }
}
