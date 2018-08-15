package org.mtokarski.allocation.model;

import java.util.ArrayList;
import java.util.List;

public class StacktracePath {

    private final String element;
    private final List<StacktracePath> parents;
    private int numberOfInvocations;

    public static StacktracePath createRoot() {
        return new StacktracePath(null, new ArrayList<>());
    }

    private StacktracePath(String element, List<StacktracePath> parents) {
        this.element = element;
        this.parents = parents;
    }

    private StacktracePath(StackTraceElement[] elements, int firstElement) {
        numberOfInvocations = 1;
        element = elements[firstElement].toString();
        parents = new ArrayList<>();
        if (firstElement < elements.length - 1) {
            parents.add(new StacktracePath(elements, firstElement + 1));
        }
    }

    public String getElement() {
        return element;
    }

    public List<StacktracePath> getParents() {
        return parents;
    }

    public int getNumberOfInvocations() {
        return numberOfInvocations;
    }

    public StacktracePath addStacktrace(StackTraceElement[] stacktace, int firstElement) {
        this.numberOfInvocations++;
        if (firstElement == stacktace.length) {
            return this;
        }

        boolean matched = false;
        String top = stacktace[firstElement].toString();
        for (StacktracePath parent : parents) {
            if (parent.element.equals(top)) {
                parent.addStacktrace(stacktace, firstElement + 1);
                matched = true;
                break;
            }
        }

        if (!matched) {
            parents.add(new StacktracePath(stacktace, firstElement));
        }

        return this;
    }
}
