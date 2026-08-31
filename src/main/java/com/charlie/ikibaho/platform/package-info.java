/**
 * The shared kernel: BaseEntity, the error hierarchy, CurrentUser, the
 * ProblemDetail handler, web plumbing.
 * <p>
 * Declared OPEN because its sub-packages are its API, not its internals. Every
 * module is meant to extend BaseEntity and throw NotFoundException; the default
 * rule -- that a module's sub-packages are private to it -- describes a feature
 * module, and platform is not one.
 * <p>
 * The rule this does NOT relax is direction. Nothing in here may depend on a
 * feature module; that is what makes it a kernel rather than a junk drawer, and
 * ModularityTests still fails the build if it happens.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.charlie.ikibaho.platform;
