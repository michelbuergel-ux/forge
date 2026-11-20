package forge.util;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;

public class Localizer {

    private static Localizer instance;

    private List<LocalizationChangeObserver> observers = new ArrayList<>();

    private Locale locale;
    private ResourceBundle resourceBundle;
    private ResourceBundle englishBundle;
    private ResourceBundle adventureBundle;
    /**
     * The texts of game modes kept apart from the main files: each subfolder of the languages folder holds a mode's own
     * "en-US.properties", "de-DE.properties" ... (its keys must not clash with the main files'). Looked up after the main
     * file; the English one stands in for a missing translation as the main English file does.
     */
    private final List<ResourceBundle> modeBundles = new ArrayList<>();
    private final List<ResourceBundle> modeEnglishBundles = new ArrayList<>();
    private String currentLanguageRegionID;
    private boolean silent = false;
    private boolean english = false;

    public static Localizer getInstance() {
        if (instance == null) {
            synchronized (Localizer.class) {
                instance = new Localizer();
            }
        }
        return instance;
    }

    public void setEnglish(boolean value) {
        english = value;
    }

    private Localizer() {
    }

    public void initialize(String localeID, String languagesDirectory) {
        setLanguage(localeID, languagesDirectory);
    }

    public String convert(String value, String fromEncoding, String toEncoding) throws UnsupportedEncodingException {
        return new String(value.getBytes(fromEncoding), toEncoding);
    }

    public String charset(String value, String charsets[]) {
        String probe = StandardCharsets.UTF_8.name();
        for(String c : charsets) {
            Charset charset = Charset.forName(c);
            if(charset != null) {
                try {
                    if (value.equals(convert(convert(value, charset.name(), probe), probe, charset.name()))) {
                        return c;
                    }
                } catch(UnsupportedEncodingException ignored) {}
            }
        }
        return StandardCharsets.UTF_8.name();
    }

    public String getMessageorUseDefault(final String key, final String defaultValue, final Object... messageArguments) {
        try {
            silent = true;
            String value = getMessage(key, messageArguments);
            if (value.contains("INVALID PROPERTY:"))
                return defaultValue;
            return value;
        } catch (Exception e) {
            return defaultValue;
        }
    }
    public String getEnglishMessage(final String key, final Object... messageArguments) {
        return getMessage(true, key, messageArguments);
    }
    //FIXME: localizer should return default value from english locale or it will crash some GUI element like the NewGameMenu->NewGameScreen Popup when returned null...
    public String getMessage(final String key, final Object... messageArguments) {
        return getMessage(false, key, messageArguments);
    }
    public String getMessage(boolean forcedEnglish, final String key, final Object... messageArguments) {
        MessageFormat formatter = null;
        String rawValue = null;

        try {
            //formatter = new MessageFormat(resourceBundle.getString(key.toLowerCase()), locale);
            rawValue = lookup(key, english || forcedEnglish);
            formatter = new MessageFormat(rawValue, english || forcedEnglish ? Locale.ENGLISH : locale);
        } catch (final IllegalArgumentException | MissingResourceException e) {
            if (!silent)
                e.printStackTrace();
        }

        if (formatter == null) {
            if (!silent) {
                System.err.println("INVALID PROPERTY: '" + key + "' -- Translation missing from " + locale);
            }

            if (english || forcedEnglish) {
                return "INVALID PROPERTY: '" + key + "' -- Translation missing from English?";
            }
            try {
                rawValue = englishString(key);
                if (rawValue == null) {
                    throw new MissingResourceException("Can't find resource for key " + key, "en-US", key);
                }
                formatter = new MessageFormat(rawValue, Locale.ENGLISH);
                forcedEnglish = true;
            } catch (final IllegalArgumentException | MissingResourceException e) {
                if (!silent) {
                    e.printStackTrace();
                }
                return "INVALID PROPERTY: '" + key + "' -- Translation missing from English locale?";
            }
        }

        silent = false;

        formatter.setLocale(english || forcedEnglish ? Locale.ENGLISH : locale);

        String formattedMessage = "CHAR ENCODING ERROR";
        final String[] charsets = { "ISO-8859-1", "UTF-8" };
        //Support non-English-standard characters
        String detectedCharset = charset(rawValue, charsets);

        final int argLength = messageArguments.length;
        Object[] syncEncodingMessageArguments = new Object[argLength];
        //when messageArguments encoding not equal resourceBundle.getString(key),convert to equal
        //avoid convert to a have two encoding content formattedMessage string.
        for (int i = 0; i < argLength; i++) {
            String objCharset = charset(messageArguments[i].toString(), charsets);
            try {
                syncEncodingMessageArguments[i] = convert(messageArguments[i].toString(), objCharset, detectedCharset);
            } catch (UnsupportedEncodingException ignored) {
                System.err.println("Cannot Convert '" + messageArguments[i].toString() + "' from '" + objCharset + "' To '" + detectedCharset + "'");
                return "encoding '" + key + "' translate string failure";
            }
        }

        try {
            formattedMessage = new String(formatter.format(syncEncodingMessageArguments).getBytes(detectedCharset), StandardCharsets.UTF_8);
        } catch(UnsupportedEncodingException ignored) {}

        return formattedMessage;
    }

    public void setLanguage(final String languageRegionID, final String languagesDirectory) {
        String[] splitLocale = languageRegionID.split("-");

        Locale oldLocale = locale;
        locale = new Locale(splitLocale[0], splitLocale[1]);

        //Don't reload the language if nothing changed
        if (oldLocale == null || !oldLocale.equals(locale)) {
            File file = new File(languagesDirectory);
            URL[] urls = null;

            try {
                urls = new URL[] { file.toURI().toURL() };
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }

            ClassLoader loader = new URLClassLoader(urls);
            currentLanguageRegionID = languageRegionID;

            try {
                englishBundle = ResourceBundle.getBundle("en-US", new Locale("en", "US"), loader);
                resourceBundle = ResourceBundle.getBundle(languageRegionID, new Locale(splitLocale[0], splitLocale[1]), loader);
            } catch (NullPointerException | MissingResourceException e) {
                //If the language can't be loaded, default to US English
                resourceBundle = englishBundle;
                e.printStackTrace();
            }

            adventureBundle = null;
            loadModeBundles(file, splitLocale);

            System.out.println("Language '" + resourceBundle.getBaseBundleName() + "' loaded successfully.");

            notifyObservers();
        }
    }

    public void loadAdventureBundle(final String languagesDirectory) {
        if (currentLanguageRegionID == null || languagesDirectory == null || languagesDirectory.isEmpty()) {
            adventureBundle = null;
            return;
        }
        try {
            URL[] urls = { new File(languagesDirectory).toURI().toURL() };
            ClassLoader adventureLoader = new URLClassLoader(urls);
            adventureBundle = ResourceBundle.getBundle("adventure-" + currentLanguageRegionID, locale, adventureLoader);
        } catch (final MalformedURLException | NullPointerException | MissingResourceException e) {
            adventureBundle = null;
        }
    }

    private void loadModeBundles(final File languagesDirectory, final String[] splitLocale) {
        modeBundles.clear();
        modeEnglishBundles.clear();
        final File[] modes = languagesDirectory.listFiles(File::isDirectory);
        if (modes == null) {
            return;
        }
        Arrays.sort(modes);
        for (final File mode : modes) {
            try {
                final ClassLoader loader = new URLClassLoader(new URL[] { mode.toURI().toURL() });
                ResourceBundle english = null;
                try {
                    english = ResourceBundle.getBundle("en-US", new Locale("en", "US"), loader);
                    modeEnglishBundles.add(english);
                } catch (final MissingResourceException ignored) {
                    // a mode without English texts: only its translations
                }
                try {
                    modeBundles.add(ResourceBundle.getBundle(currentLanguageRegionID, new Locale(splitLocale[0], splitLocale[1]), loader));
                } catch (final MissingResourceException e) {
                    if (english != null) {
                        modeBundles.add(english);
                    }
                }
            } catch (final MalformedURLException e) {
                e.printStackTrace();
            }
        }
    }

    private static String modeString(final List<ResourceBundle> bundles, final String key) {
        if (bundles == null) { // a mocked instance (tests) skips the constructor
            return null;
        }
        for (final ResourceBundle bundle : bundles) {
            if (bundle.containsKey(key)) {
                return bundle.getString(key);
            }
        }
        return null;
    }

    private String lookup(final String key, final boolean forceEnglish) {
        if (!forceEnglish && adventureBundle != null) {
            try {
                return adventureBundle.getString(key);
            } catch (final MissingResourceException ignored) {}
        }
        final ResourceBundle main = forceEnglish ? englishBundle : resourceBundle;
        if (main.containsKey(key)) {
            return main.getString(key);
        }
        final String mode = modeString(forceEnglish ? modeEnglishBundles : modeBundles, key);
        if (mode != null) {
            return mode;
        }
        return main.getString(key); // throws: missing
    }

    /** The English text of a key, from the main English file or a mode's; null if none has it. */
    private String englishString(final String key) {
        if (englishBundle.containsKey(key)) {
            return englishBundle.getString(key);
        }
        return modeString(modeEnglishBundles, key);
    }

    public void registerObserver(LocalizationChangeObserver observer) {
        observers.add(observer);
    }

    private void notifyObservers() {
        for (LocalizationChangeObserver observer : observers) {
            observer.localizationChanged();
        }
    }

}
