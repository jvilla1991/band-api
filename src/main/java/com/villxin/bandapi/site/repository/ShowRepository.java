package com.villxin.bandapi.site.repository;

import com.villxin.bandapi.site.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ShowRepository extends JpaRepository<Show, Long> {

    /** Public list: today onward, soonest first. */
    List<Show> findByShowDateGreaterThanEqualOrderByShowDateAsc(LocalDate date);

    /** Admin list: everything, soonest first (past shows at the top make cleanup obvious). */
    List<Show> findAllByOrderByShowDateAsc();
}
