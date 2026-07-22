package com.villxin.bandapi.site.controller;

import com.villxin.bandapi.exception.ApiException;
import com.villxin.bandapi.site.dto.ShowRequest;
import com.villxin.bandapi.site.dto.ShowResponse;
import com.villxin.bandapi.site.entity.Show;
import com.villxin.bandapi.site.entity.SiteSetting;
import com.villxin.bandapi.site.repository.ShowRepository;
import com.villxin.bandapi.site.repository.SiteSettingRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Owner-only site management (ADMIN role, enforced in SecurityConfig):
 * page feature flags and the shows list behind the Live tab.
 */
@RestController
@RequestMapping("/api/site/admin")
public class SiteAdminController {

    private final SiteSettingRepository settingRepository;
    private final ShowRepository showRepository;
    private final SiteController siteController;

    public SiteAdminController(SiteSettingRepository settingRepository,
                               ShowRepository showRepository,
                               SiteController siteController) {
        this.settingRepository = settingRepository;
        this.showRepository = showRepository;
        this.siteController = siteController;
    }

    public record FlagUpdate(boolean enabled) {}

    @PutMapping("/flags/{name}")
    public Map<String, Boolean> setFlag(@PathVariable String name, @RequestBody FlagUpdate update) {
        if (!SiteController.PAGE_FLAGS.contains(name)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_FLAG",
                    "Unknown flag: " + name + " (known: " + String.join(", ", SiteController.PAGE_FLAGS) + ")");
        }
        String key = SiteController.FLAG_PREFIX + name;
        SiteSetting setting = settingRepository.findById(key)
                .orElseGet(() -> new SiteSetting(key, "false"));
        setting.setValue(Boolean.toString(update.enabled()));
        settingRepository.save(setting);
        return siteController.flags();
    }

    @GetMapping("/shows")
    public List<ShowResponse> allShows() {
        return showRepository.findAllByOrderByShowDateAsc()
                .stream()
                .map(ShowResponse::from)
                .toList();
    }

    @PostMapping("/shows")
    public ResponseEntity<ShowResponse> createShow(@Valid @RequestBody ShowRequest request) {
        Show show = new Show();
        request.applyTo(show);
        return ResponseEntity.status(HttpStatus.CREATED).body(ShowResponse.from(showRepository.save(show)));
    }

    @PutMapping("/shows/{id}")
    public ShowResponse updateShow(@PathVariable Long id, @Valid @RequestBody ShowRequest request) {
        Show show = showRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SHOW_NOT_FOUND", "No show with id " + id));
        request.applyTo(show);
        return ShowResponse.from(showRepository.save(show));
    }

    @DeleteMapping("/shows/{id}")
    public ResponseEntity<Void> deleteShow(@PathVariable Long id) {
        if (!showRepository.existsById(id)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "SHOW_NOT_FOUND", "No show with id " + id);
        }
        showRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
