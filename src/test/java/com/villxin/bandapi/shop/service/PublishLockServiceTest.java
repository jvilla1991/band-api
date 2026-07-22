package com.villxin.bandapi.shop.service;

import com.villxin.bandapi.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lock release: only locked products are touched, and Printify errors translate usefully. */
class PublishLockServiceTest {

    private static final String STORE_URL = "https://villxin.com/#/store";

    private final PrintifyClient printifyClient = mock(PrintifyClient.class);
    private final PublishLockService service = new PublishLockService(printifyClient, STORE_URL);

    private PrintifyClient.PrintifyProduct product(String id, String title, boolean locked) {
        return new PrintifyClient.PrintifyProduct(
                id, title, "desc", true, locked, List.of(), List.of(), List.of());
    }

    @Test
    void releasesOnlyLockedProducts() {
        when(printifyClient.listProducts()).thenReturn(List.of(
                product("p1", "V-Neck Tee", true),
                product("p2", "Racerback Tank", true),
                product("p3", "Sticker", false)));

        PublishLockService.ReleaseResult result = service.releaseAll();

        assertEquals(2, result.released());
        assertEquals(1, result.alreadyUnlocked());
        assertTrue(result.failures().isEmpty());
        verify(printifyClient).markPublishingSucceeded("p1", STORE_URL);
        verify(printifyClient).markPublishingSucceeded("p2", STORE_URL);
        verify(printifyClient, never()).markPublishingSucceeded(eq("p3"), anyString());
    }

    @Test
    void missingScopeSurfacesAsActionableError() {
        when(printifyClient.listProducts()).thenReturn(List.of(product("p1", "V-Neck Tee", true)));
        doThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Invalid scope(s) provided.",
                null, null, null))
                .when(printifyClient).markPublishingSucceeded(anyString(), anyString());

        ApiException thrown = assertThrows(ApiException.class, service::releaseAll);

        assertEquals("PRINTIFY_SCOPE_MISSING", thrown.getCode());
        assertTrue(thrown.getMessage().contains("products.write"));
    }

    @Test
    void perProductFailureIsReportedWithoutAbortingTheRest() {
        when(printifyClient.listProducts()).thenReturn(List.of(
                product("p1", "V-Neck Tee", true),
                product("p2", "Racerback Tank", true)));
        doThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", null, null, null))
                .when(printifyClient).markPublishingSucceeded(eq("p1"), anyString());

        PublishLockService.ReleaseResult result = service.releaseAll();

        assertEquals(1, result.released());
        assertEquals(1, result.failures().size());
        assertTrue(result.failures().get(0).startsWith("V-Neck Tee"));
        verify(printifyClient).markPublishingSucceeded("p2", STORE_URL);
    }

    @Test
    void catalogReadFailureTranslatesToo() {
        when(printifyClient.listProducts()).thenThrow(
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthenticated", null, null, null));

        ApiException thrown = assertThrows(ApiException.class, service::releaseAll);

        assertEquals("PRINTIFY_SCOPE_MISSING", thrown.getCode());
    }
}
