package com.renan.taskmanager.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Architecture tests using ArchUnit.
 *
 * <p>These tests enforce the DDD layering rules defined in {@code AGENTS.md}.
 * Unlike unit/integration tests (which verify behavior), architecture tests
 * verify <b>structure</b>: which package may depend on which, where annotations
 * are allowed, etc.</p>
 *
 * <p>If a future commit accidentally adds a JPA annotation to a domain class,
 * or makes the domain depend on Spring, these tests fail at build time —
 * catching architectural erosion before it ships.</p>
 *
 * <p>They run as part of {@code mvn test} (fast, milliseconds), providing
 * continuous protection without slowing down the feedback loop.</p>
 */
class ArchitectureTest {

    /**
     * Imported classes once for all tests (cached for the duration of the class).
     * Excludes test classes themselves from the analysis.
     */
    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.renan.taskmanager");

    @Nested
    @DisplayName("Domain layer isolation")
    class DomainIsolation {

        @Test
        @DisplayName("Domain classes should not depend on Spring")
        void domainShouldNotDependOnSpring() {
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("org.springframework..")
                    .because("the domain layer must remain framework-agnostic (DDD)")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain classes should not depend on JPA/Hibernate")
        void domainShouldNotDependOnJpa() {
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
                    .because("persistence is an infrastructure concern, not domain")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain classes should not depend on infrastructure")
        void domainShouldNotDependOnInfrastructure() {
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..infrastructure..", "..application..", "..api..")
                    .because("domain is the innermost layer; it must not depend on outer layers")
                    .check(classes);
        }

        @Test
        @DisplayName("Domain classes should only depend on domain, java.base, and slf4j")
        void domainShouldOnlyDependOnAllowedPackages() {
            classes()
                    .that().resideInAPackage("..domain..")
                    .should().onlyDependOnClassesThat()
                    .resideInAnyPackage(
                            "com.renan.taskmanager..domain..",
                            "java..",
                            "org.slf4j.."
                    )
                    .because("domain must be free of framework dependencies")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Layer dependencies (DDD hexagonal-ish)")
    class LayerRules {

        @Test
        @DisplayName("API layer may depend on application and domain, not infrastructure")
        void apiLayerShouldNotDependOnInfrastructure() {
            noClasses()
                    .that().resideInAPackage("..api..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..")
                    .because("controllers talk to use cases, not to JPA repositories or configs")
                    .check(classes);
        }

        @Test
        @DisplayName("Infrastructure should not depend on the API layer")
        void infrastructureShouldNotDependOnApi() {
            noClasses()
                    .that().resideInAPackage("..infrastructure..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..api..")
                    .because("infrastructure is a lower layer than API; dependencies flow inward")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Bounded contexts isolation")
    class ContextIsolation {

        @Test
        @DisplayName("Tasks context should not depend on users infrastructure")
        void tasksShouldNotDependOnUsersInfra() {
            noClasses()
                    .that().resideInAPackage("..tasks..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..users.infrastructure..")
                    .because("bounded contexts communicate via domain types only, not infra")
                    .check(classes);
        }

        @Test
        @DisplayName("Users context should not depend on tasks infrastructure")
        void usersShouldNotDependOnTasksInfra() {
            noClasses()
                    .that().resideInAPackage("..users..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..tasks.infrastructure..")
                    .because("bounded contexts communicate via domain types only, not infra")
                    .check(classes);
        }

        @Test
        @DisplayName("Tasks context should not depend on any users layer (only common may be shared)")
        void tasksShouldNotDependOnUsers() {
            // The infra-only rule above is a subset of this: tasks may not reach
            // into users at all, period. Cross-context collaboration goes
            // through ..common.. (shared kernel) — never directly.
            noClasses()
                    .that().resideInAPackage("com.renan.taskmanager.tasks..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.renan.taskmanager.users..")
                    .because("bounded contexts communicate only through the common/shared kernel")
                    .check(classes);
        }

        @Test
        @DisplayName("Users context should not depend on any tasks layer (only common may be shared)")
        void usersShouldNotDependOnTasks() {
            noClasses()
                    .that().resideInAPackage("com.renan.taskmanager.users..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.renan.taskmanager.tasks..")
                    .because("bounded contexts communicate only through the common/shared kernel")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Stereotype placement")
    class StereotypePlacement {

        @Test
        @DisplayName("@RestController and @Controller may only live in the API layer")
        void webStereotypesOnlyInApi() {
            // A @RestController anywhere else (e.g. inside application or
            // infrastructure) is an HTTP concern leaking out of the API layer.
            classes()
                    .that().areAnnotatedWith("org.springframework.stereotype.Controller")
                    .or().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .should().resideInAPackage("..api..")
                    .because("HTTP endpoints belong in the API layer; everything else is internal")
                    .check(classes);
        }

        @Test
        @DisplayName("@Service, @Repository, @Component, @Controller may not live in the domain layer")
        void springStereotypesNotInDomain() {
            // The domain layer is framework-agnostic by design (see
            // DomainIsolation). This rule makes the intent explicit at the
            // stereotype level: even if a Spring stereotype doesn't pull a real
            // dependency yet, it signals intent that the class is Spring-managed
            // — a smell in a pure domain.
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().beAnnotatedWith("org.springframework.stereotype.Service")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Repository")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Component")
                    .orShould().beAnnotatedWith("org.springframework.stereotype.Controller")
                    .because("the domain layer is framework-agnostic; Spring stereotypes are forbidden here")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("Naming conventions in the domain layer")
    class NamingConventions {

        @Test
        @DisplayName("Domain classes must not be named after outer-layer suffixes")
        void domainClassNamesShouldNotUseLayerSuffixes() {
            // A class named User*Service* in ..domain.. is a strong smell: the
            // author is signaling application-layer intent in a domain file.
            //
            // Note: "Repository" is intentionally NOT in this list. In DDD,
            // "Repository" is the canonical name for the persistence port
            // interface that lives IN the domain layer — banishing it would
            // force awkward renames (Port, Store, Gateway) and lose the shared
            // vocabulary with the wider DDD literature. Only the
            // framework-specific suffixes (Controller/Service/Config) are
            // forbidden, since they signal Spring-layer intent.
            noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().haveSimpleNameEndingWith("Controller")
                    .orShould().haveSimpleNameEndingWith("Service")
                    .orShould().haveSimpleNameEndingWith("Config")
                    .because("those suffixes signal Spring-layer intent (api/application/"
                            + "infrastructure); domain types are named after the concept "
                            + "(User, Email, TaskStatus). 'Repository' stays allowed — it is "
                            + "the canonical DDD name for a persistence port.")
                    .check(classes);
        }
    }
}
