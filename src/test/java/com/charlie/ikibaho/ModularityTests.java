package com.charlie.ikibaho;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * The boundary guard.
 *
 * verify() fails when one module reaches into another's {@code internal}
 * package, or when two modules depend on each other in a cycle. That failure is
 * not a nuisance to be suppressed -- it is the design telling you that something
 * either belongs in the public API of its module, or belongs somewhere else.
 */
class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(IkibahoApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        MODULES.verify();
    }

    /**
     * Writes module diagrams and a dependency table to target/spring-modulith-docs.
     * Cheap to keep in the build: documentation that is generated cannot drift.
     */
    @Test
    void writeDocumentation() {
        new Documenter(MODULES).writeDocumentation();
    }
}
