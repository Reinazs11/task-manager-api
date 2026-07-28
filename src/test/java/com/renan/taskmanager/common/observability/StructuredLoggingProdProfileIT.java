package com.renan.taskmanager.common.observability;

import com.renan.taskmanager.common.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Asserts that {@code logback-spring.xml} wires the {@code prod} profile to a
 * JSON appender (decision #20).
 *
 * <p><b>What this proves:</b> the {@code <springProfile name="prod">} block
 * references an appender whose encoder is a {@code LogstashEncoder}, and does
 * NOT reference the plain {@code CONSOLE} (human-readable) appender. If someone
 * reverts the prod block or detaches the JSON appender, this test fails — prod
 * would silently ship human-readable text to a log aggregator that expects JSON.</p>
 *
 * <p><b>Why parse the XML instead of inspecting the running LoggerContext?</b>
 * Logback's {@code LoggerContext} is a JVM-wide singleton, and the Spring
 * TestContext cache reuses it across ITs in the same fork. By the time this IT
 * runs, another IT may have initialized Logback under the default {@code dev}
 * profile, so {@code LoggerFactory.getILoggerFactory()} reflects whichever
 * profile won the race — not necessarily {@code prod}. The XML file is the
 * source of truth for the wiring contract; parsing it is deterministic and
 * immune to context-caching/ordering flakiness.</p>
 *
 * <p><b>Why still extend {@link AbstractIntegrationTest}?</b> Consistency with
 * the rest of the observability IT suite and to keep the class within the IT
 * phase (Failsafe). The superclass boots Spring; this test simply ignores the
 * context and reads the classpath resource directly.</p>
 */
@SpringBootTest(properties = {
        "spring.profiles.active=prod",
        // Prod profile ships CORS_ALLOWED_ORIGINS empty on purpose (fail-fast).
        // Supply a value so the ApplicationContext can start; this test does not
        // exercise CORS.
        "app.cors.allowed-origins=https://prod.example.com"
})
class StructuredLoggingProdProfileIT extends AbstractIntegrationTest {

    private static final String LOGBACK_XML = "logback-spring.xml";

    @Test
    @DisplayName("logback-spring.xml prod profile should reference a LogstashEncoder appender, not the human-readable one")
    void prodProfileShouldReferenceJsonAppender() throws Exception {
        Document doc = parseLogbackXml();

        Element prodBlock = springProfileBlock(doc, "prod");
        assertThat(prodBlock)
                .as("logback-spring.xml must define a <springProfile name=\"prod\"> block")
                .isNotNull();

        // Collect the appender names referenced by the prod root logger.
        NodeList appenderRefs = prodBlock.getElementsByTagName("appender-ref");
        assertThat(appenderRefs.getLength())
                .as("prod profile must attach at least one appender to the root logger")
                .isGreaterThan(0);

        boolean referencesJsonAppender = false;
        boolean referencesHumanAppender = false;
        for (int i = 0; i < appenderRefs.getLength(); i++) {
            Element ref = (Element) appenderRefs.item(i);
            String appenderName = ref.getAttribute("ref");
            Element appenderDef = findAppenderDefinition(doc, appenderName);
            assertThat(appenderDef)
                    .as("appender '%s' referenced in prod has no <appender> definition", appenderName)
                    .isNotNull();
            String encoderClass = encoderClassOf(appenderDef);
            if (encoderClass != null && encoderClass.contains("LogstashEncoder")) {
                referencesJsonAppender = true;
            } else {
                // PatternLayoutEncoder or a plain <pattern> encoder = human-readable.
                referencesHumanAppender = true;
            }
        }

        assertThat(referencesJsonAppender)
                .as("prod profile must reference an appender backed by a LogstashEncoder")
                .isTrue();
        assertThat(referencesHumanAppender)
                .as("prod profile must NOT reference the human-readable CONSOLE appender "
                        + "(that is for dev/test only — see logback-spring.xml)")
                .isFalse();
    }

    private Document parseLogbackXml() throws Exception {
        try (InputStream in = openLogbackXml()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            // Defend against XXE — we parse a trusted project resource, but the
            // habit costs nothing and the file lives in version control.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(in);
        }
    }

    private InputStream openLogbackXml() throws Exception {
        // Prefer the classpath copy (what actually ships), falling back to the
        // source tree so the test still works from any working directory.
        InputStream fromClasspath = getClass().getClassLoader().getResourceAsStream(LOGBACK_XML);
        if (fromClasspath != null) {
            return fromClasspath;
        }
        return Files.newInputStream(Path.of("src/main/resources", LOGBACK_XML));
    }

    private Element springProfileBlock(Document doc, String profileName) {
        NodeList profiles = doc.getElementsByTagName("springProfile");
        for (int i = 0; i < profiles.getLength(); i++) {
            Element el = (Element) profiles.item(i);
            // springProfile name supports alternatives like "dev | default".
            String nameAttr = el.getAttribute("name");
            for (String part : nameAttr.split("\\|")) {
                if (part.trim().equals(profileName)) {
                    return el;
                }
            }
        }
        return null;
    }

    private Element findAppenderDefinition(Document doc, String name) {
        NodeList appenders = doc.getElementsByTagName("appender");
        for (int i = 0; i < appenders.getLength(); i++) {
            Element el = (Element) appenders.item(i);
            if (name.equals(el.getAttribute("name"))) {
                return el;
            }
        }
        return null;
    }

    private String encoderClassOf(Element appender) {
        NodeList encoders = appender.getElementsByTagName("encoder");
        if (encoders.getLength() == 0) {
            return null;
        }
        return ((Element) encoders.item(0)).getAttribute("class");
    }
}
