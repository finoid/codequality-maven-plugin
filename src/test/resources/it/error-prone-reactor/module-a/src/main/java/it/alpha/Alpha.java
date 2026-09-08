package it.alpha;

public class Alpha {
    // Error Prone: ReferenceEquality - reference equality used to compare arrays
    public boolean sameArray(final int[] first, final int[] second) {
        return first == second;
    }
}
