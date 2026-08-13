package com.sfgame.classsystem;

import java.util.ArrayList;
import java.util.List;

final class ClassFile {
    private List<ClassDefinition> classes = new ArrayList<>();

    List<ClassDefinition> classes() {
        return classes == null ? List.of() : classes;
    }
}

