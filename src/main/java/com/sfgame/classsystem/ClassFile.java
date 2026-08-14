package com.sfgame.classsystem;

import java.util.ArrayList;
import java.util.List;

final class ClassFile {
    private String parent;
    private List<ClassDefinition> classes = new ArrayList<>();
    private List<ClassDefinition> captainClasses = new ArrayList<>();

    String parent() { return parent == null || parent.isBlank() ? null : parent; }

    List<ClassDefinition> classes() {
        return classes == null ? List.of() : classes;
    }

    List<ClassDefinition> captainClasses() {
        return captainClasses == null ? List.of() : captainClasses;
    }
}
