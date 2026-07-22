package com.villxin.bandapi.site.repository;

import com.villxin.bandapi.site.entity.SiteSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteSettingRepository extends JpaRepository<SiteSetting, String> {
}
