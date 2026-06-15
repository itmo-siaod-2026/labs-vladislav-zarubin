package org.example.functional;

import org.example.WikiMain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WikiMainTest {

    @Test
    void wikiPageUrlEncodesUtf8Title() {
        String url = WikiMain.wikiPageUrl("https://ru.wikipedia.org/", "Нью-Йорк Джайентс");
        assertEquals("https://ru.wikipedia.org/wiki/"
                + "%D0%9D%D1%8C%D1%8E-%D0%99%D0%BE%D1%80%D0%BA_%D0%94%D0%B6%D0%B0%D0%B9%D0%B5%D0%BD%D1%82%D1%81", url);
    }

    @Test
    void normalizeWikiBaseStripsTrailingSlash() {
        assertEquals("https://en.wikipedia.org",
                WikiMain.normalizeWikiBase("https://en.wikipedia.org/"));
    }
}
