package com.villxin.bandapi.site.controller;

import com.villxin.bandapi.site.dto.ShowResponse;
import com.villxin.bandapi.site.entity.SiteSetting;
import com.villxin.bandapi.site.repository.ShowRepository;
import com.villxin.bandapi.site.repository.SiteSettingRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public site config: which pages are enabled, and the upcoming shows list. */
@RestController
@RequestMapping("/api/site")
public class SiteController {

    /** Flag keys the frontend knows about; stored as {@code page_<name>} settings rows. */
    public static final List<String> PAGE_FLAGS = List.of("store", "live", "yourarea");

    static final String FLAG_PREFIX = "page_";

    private final SiteSettingRepository settingRepository;
    private final ShowRepository showRepository;

    public SiteController(SiteSettingRepository settingRepository, ShowRepository showRepository) {
        this.settingRepository = settingRepository;
        this.showRepository = showRepository;
    }

    @GetMapping("/flags")
    public Map<String, Boolean> flags() {
        Map<String, String> stored = new LinkedHashMap<>();
        for (SiteSetting setting : settingRepository.findAll()) {
            stored.put(setting.getKey(), setting.getValue());
        }
        Map<String, Boolean> flags = new LinkedHashMap<>();
        for (String name : PAGE_FLAGS) {
            flags.put(name, Boolean.parseBoolean(stored.getOrDefault(FLAG_PREFIX + name, "false")));
        }
        return flags;
    }

    @GetMapping("/shows")
    public List<ShowResponse> upcomingShows() {
        return showRepository.findByShowDateGreaterThanEqualOrderByShowDateAsc(LocalDate.now())
                .stream()
                .map(ShowResponse::from)
                .toList();
    }
}
