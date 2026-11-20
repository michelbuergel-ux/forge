package forge.ai;

import forge.gamemodes.hexcrawl.HexTheme;
import org.testng.AssertJUnit;
import org.testng.annotations.Test;

/** Every shop theme says in one line what it collects (the tooltips of the shop's tabs and of the theme picker). */
public class HexThemeDescriptionsTest extends AITest {

    @Test
    public void everyThemeHasADescription() {
        for (final HexTheme theme : HexTheme.all()) {
            final String about = theme.getDescription();
            AssertJUnit.assertNotNull(theme.id, about);
            AssertJUnit.assertFalse(theme.id + ": " + about, about.contains("INVALID PROPERTY"));
        }
    }
}
