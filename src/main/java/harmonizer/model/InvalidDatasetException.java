package harmonizer.model;

import java.util.List;

/** Thrown when an uploaded dataset cannot be used. Carries every problem found, not just the first. */
public class InvalidDatasetException extends RuntimeException {

    private final List<String> problems;

    public InvalidDatasetException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public InvalidDatasetException(String problem) {
        this(List.of(problem));
    }

    public List<String> getProblems() {
        return problems;
    }
}
