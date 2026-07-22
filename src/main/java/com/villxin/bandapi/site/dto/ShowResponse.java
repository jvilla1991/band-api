package com.villxin.bandapi.site.dto;

import com.villxin.bandapi.site.entity.Show;

import java.time.LocalDate;

public record ShowResponse(
        Long id,
        LocalDate showDate,
        String venue,
        String city,
        String ticketUrl,
        String note,
        Show.Status status
) {
    public static ShowResponse from(Show show) {
        return new ShowResponse(
                show.getId(),
                show.getShowDate(),
                show.getVenue(),
                show.getCity(),
                show.getTicketUrl(),
                show.getNote(),
                show.getStatus()
        );
    }
}
