/**
 * The issue module's published events.
 *
 * Declared a named interface so other modules may listen to them. Without this,
 * a sub-package is module-private -- which would be right for persistence, and
 * exactly wrong for events, whose entire purpose is to be consumed elsewhere.
 *
 * What this exposes is the record shape. Changing a component of a published
 * event is a breaking change for every listener, and for any event already
 * sitting serialized in the publication registry awaiting retry.
 */
@org.springframework.modulith.NamedInterface("events")
package com.charlie.ikibaho.issue.events;
