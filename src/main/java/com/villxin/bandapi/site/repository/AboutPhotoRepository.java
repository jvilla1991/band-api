package com.villxin.bandapi.site.repository;

import com.villxin.bandapi.site.entity.AboutPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AboutPhotoRepository extends JpaRepository<AboutPhoto, Long> {

    /** Carousel order: lowest sort_order first (public and admin lists share this). */
    List<AboutPhoto> findAllByOrderBySortOrderAsc();

    /** Highest existing sort_order, used to append a new upload to the end. */
    Optional<AboutPhoto> findFirstByOrderBySortOrderDesc();
}
