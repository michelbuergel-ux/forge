package forge.ai;

import java.nio.file.Path;

import org.testng.SkipException;
import org.testng.annotations.Test;

import forge.gamemodes.hexcrawl.HexThemeReport;

/**
 * Writes the shop themes' audit report (HexThemeReport) when asked to, else skips:
 * mvn -pl forge-gui-desktop -am test -Dtest=HexThemeReportTest -Dhexcrawl.themeReport=target/hex-theme-report.txt
 */
public class HexThemeReportTest extends AITest {

    @Test
    public void writeReport() throws Exception {
        final String file = System.getProperty("hexcrawl.themeReport");
        if (file == null || file.isEmpty()) {
            throw new SkipException("no -Dhexcrawl.themeReport=FILE");
        }
        HexThemeReport.write(Path.of(file));
    }
}
