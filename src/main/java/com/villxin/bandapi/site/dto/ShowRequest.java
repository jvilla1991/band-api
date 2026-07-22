package com.villxin.bandapi.site.dto;

import com.villxin.bandapi.site.entity.Show;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ShowRequest(
        @NotNull LocalDate showDate,
        @NotBlank @Size(max = 200) String venue,
        @NotBlank @Size(max = 120) String city,
        @Size(max = 500) String ticketUrl,
        @Size(max = 300) String note,
        Show.Status status
) {
    public void applyTo(Show show) {
        show.setShowDate(showDate);
        show.setVenue(venue.trim());
        show.setCity(city.trim());
        show.setTicketUrl(ticketUrl == null || ticketUrl.isBlank() ? null : ticketUrl.trim());
        show.setNote(note == null || note.isBlank() ? null : note.trim());
        show.setStatus(status == null ? Show.Status.UPCOMING : status);
    }
}
