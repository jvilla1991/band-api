package com.villxin.bandapi.community.entity;

/**
 * Curated profile accent colors — members pick from this fixed set instead of
 * supplying raw CSS (same nostalgia, no XSS). The frontend maps each value to
 * its design-token color.
 */
public enum ThemeAccent {
    EMBER,   // molten-ember default (#d9402a)
    VIOLET,  // deep violet secondary
    MOSS,
    GOLD,
    ICE
}
