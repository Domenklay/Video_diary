package com.quietmoments.diary

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentSearchTest {
    @Test fun emptyQueryIncludesAll() {
        assertTrue(MomentSearch.matches("Лето", "Семья", "Море", "2026-08-13", "   "))
    }

    @Test fun searchMatchesCaseInsensitiveTitle() {
        assertTrue(MomentSearch.matches("МОРСКОЙ День", "Лето", "", "2026-08-13", "морской"))
    }

    @Test fun searchFindsAlbumDateAndNotes() {
        assertTrue(MomentSearch.matches("Закат", "Путешествие", "Море и чайки", "2026-08-13",
            "2026 море"))
    }

    @Test fun allWordsMustMatch() {
        assertFalse(MomentSearch.matches("Зима", "Друзья", "Снег", "2026-01-01",
            "зима море"))
    }

    @Test fun extraWhitespaceDoesNotAffectResults() {
        assertTrue(MomentSearch.matches("Море", "Наши поездки", "", "2026-07-01",
            "  наши    море  "))
    }
}
