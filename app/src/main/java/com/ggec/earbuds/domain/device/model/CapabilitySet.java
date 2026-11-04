package com.ggec.earbuds.domain.device.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** 不可变能力集合。 */
public final class CapabilitySet {

    private final Set<Capability> capabilities;

    private CapabilitySet(Set<Capability> capabilities) {
        this.capabilities = Collections.unmodifiableSet(capabilities);
    }

    public static CapabilitySet of(Capability... items) {
        EnumSet<Capability> set = EnumSet.noneOf(Capability.class);
        if (items != null) {
            Collections.addAll(set, items);
        }
        return new CapabilitySet(set);
    }

    public static CapabilitySet none() {
        return new CapabilitySet(EnumSet.noneOf(Capability.class));
    }

    public boolean has(Capability capability) {
        return capabilities.contains(capability);
    }

    public Set<Capability> all() {
        return capabilities;
    }

    public int size() {
        return capabilities.size();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CapabilitySet)) {
            return false;
        }
        return capabilities.equals(((CapabilitySet) other).capabilities);
    }

    @Override
    public int hashCode() {
        return capabilities.hashCode();
    }

    @Override
    public String toString() {
        return "CapabilitySet" + capabilities;
    }
}
