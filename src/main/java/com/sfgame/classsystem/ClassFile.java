package com.sfgame.classsystem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ClassFile {
    private String parent;
    private List<ClassDefinition> classes = new ArrayList<>();
    private List<ClassDefinition> captainClasses = new ArrayList<>();
    private Map<String, ClassScopeFile> teams = new LinkedHashMap<>();
    private Map<String, ClassScopeFile> maps = new LinkedHashMap<>();

    String parent() { return parent == null || parent.isBlank() ? null : parent; }

    List<ClassDefinition> classes() {
        return classes == null ? List.of() : classes;
    }

    List<ClassDefinition> captainClasses() {
        return captainClasses == null ? List.of() : captainClasses;
    }

    Map<String, ClassScopeFile> teams() {
        return teams == null ? Map.of() : teams;
    }

    Map<String, ClassScopeFile> maps() {
        return maps == null ? Map.of() : maps;
    }
}

/** A team or map override.  Map scopes may contain team scopes as well. */
final class ClassScopeFile {
    private String parent;
    private List<ClassDefinition> classes = new ArrayList<>();
    private List<ClassDefinition> captainClasses = new ArrayList<>();
    private Map<String, ClassScopeFile> teams = new LinkedHashMap<>();

    String parent() { return parent == null || parent.isBlank() ? null : parent; }

    List<ClassDefinition> classes() {
        return classes == null ? List.of() : classes;
    }

    List<ClassDefinition> captainClasses() {
        return captainClasses == null ? List.of() : captainClasses;
    }

    Map<String, ClassScopeFile> teams() {
        return teams == null ? Map.of() : teams;
    }
}
