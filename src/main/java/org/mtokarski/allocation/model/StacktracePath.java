package org.mtokarski.allocation.model;

import java.util.ArrayList;
import java.util.List;

public class StacktracePath {

    private final String name;
    private final List<StacktracePath> children;
    private int value;

    public static StacktracePath createRoot() {
        return new StacktracePath(null, new ArrayList<>());
    }

    private StacktracePath(String name, List<StacktracePath> children) {
        this.name = name;
        this.children = children;
    }

    private StacktracePath(StackTraceElement[] elements, int firstElement) {
        value = 1;
        name = elements[firstElement].toString();
        children = new ArrayList<>();
        if (firstElement < elements.length - 1) {
            children.add(new StacktracePath(elements, firstElement + 1));
        }
    }

    public String getName() {
        return name;
    }

    public List<StacktracePath> getChildren() {
        return children;
    }

    public int getValue() {
        return value;
    }

    public StacktracePath addStacktrace(StackTraceElement[] stacktace, int firstElement) {
        this.value++;
        if (firstElement == stacktace.length) {
            return this;
        }

        boolean matched = false;
        String top = stacktace[firstElement].toString();
        for (StacktracePath parent : children) {
            if (parent.name.equals(top)) {
                parent.addStacktrace(stacktace, firstElement + 1);
                matched = true;
                break;
            }
        }

        if (!matched) {
            children.add(new StacktracePath(stacktace, firstElement));
        }

        return this;
    }
}
